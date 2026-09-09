package cn.ninth.novel.domain.project.service;

import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftListVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterUpdateResultVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.PowerSystemDraftVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleDraftVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class NovelProjectService implements INovelProjectService {

    private static final int DEFAULT_WORDS_PER_CHAPTER = 2500;
    private static final int MAX_CHARACTER_PREFERRED_COUNT = 15;
    private static final String CHARACTER_OUTLINE_WARNING =
            "该人物已参与现有故事大纲，修改核心设定可能造成剧情冲突。建议修改后重新生成受影响的大纲。";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final INovelProjectRepository projectRepository;
    private final IPlanningModelPort modelPort;
    private final IPlanningDraftRepository draftRepository;
    private final IChapterService chapterService;
    private final IPlanningRepository planningRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public NovelProjectService(
            INovelProjectRepository projectRepository,
            IPlanningModelPort modelPort,
            IPlanningDraftRepository draftRepository,
            IChapterService chapterService,
            IPlanningRepository planningRepository
    ) {
        this.projectRepository = projectRepository;
        this.modelPort = modelPort;
        this.draftRepository = draftRepository;
        this.chapterService = chapterService;
        this.planningRepository = planningRepository;
    }

    public NovelProjectService(
            INovelProjectRepository projectRepository,
            IPlanningModelPort modelPort,
            IPlanningDraftRepository draftRepository,
            IChapterService chapterService
    ) {
        this(projectRepository, modelPort, draftRepository, chapterService, null);
    }

    public NovelProjectService(
            INovelProjectRepository projectRepository,
            IPlanningModelPort modelPort,
            IPlanningDraftRepository draftRepository
    ) {
        this(projectRepository, modelPort, draftRepository, null, null);
    }

    public NovelProjectService(INovelProjectRepository projectRepository) {
        this(projectRepository, null, null, null, null);
    }

    @Override
    public NovelProjectVO createProject(NovelProjectVO project) {
        if (project == null) {
            throw illegalParameter("项目参数不能为空");
        }
        requireText(project.projectCode(), "projectCode");
        requireText(project.title(), "title");
        requireText(project.genre(), "genre");
        if (project.targetChapterCount() != null
                && project.targetChapterCount() <= 0) {
            throw illegalParameter("targetChapterCount 必须大于 0");
        }
        if (project.wordsPerChapter() != null
                && project.wordsPerChapter() <= 0) {
            throw illegalParameter("wordsPerChapter 必须大于 0");
        }
        return projectRepository.createProject(new NovelProjectVO(
                project.projectCode(),
                project.title(),
                project.genre(),
                project.targetChapterCount(),
                project.wordsPerChapter() == null
                        ? DEFAULT_WORDS_PER_CHAPTER
                        : project.wordsPerChapter(),
                0,
                "DRAFT"
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelProjectVO updateTargetChapterCount(
            String projectCode,
            Integer targetChapterCount
    ) {
        requireText(projectCode, "projectCode");
        if (targetChapterCount == null || targetChapterCount <= 0) {
            throw illegalParameter("targetChapterCount 必须大于 0");
        }
        NovelProjectVO project = getProject(projectCode);
        if (project.currentChapterNumber() != null
                && targetChapterCount < project.currentChapterNumber()) {
            throw illegalParameter("预计章节数不能小于已完成章节数");
        }
        return projectRepository.updateTargetChapterCount(
                projectCode,
                targetChapterCount
        );
    }

    @Override
    public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
        requireText(projectCode, "projectCode");
        if (bible == null) {
            throw illegalParameter("bible 不能为空");
        }
        requireText(bible.oneSentencePremise(), "oneSentencePremise");
        return projectRepository.saveBible(projectCode, new StoryBibleVO(
                bible.oneSentencePremise(),
                bible.coreTheme(),
                bible.mainConflict(),
                bible.endingDirection(),
                bible.worldBackground(),
                bible.powerSystemJson(),
                bible.hardRulesJson(),
                bible.styleGuide(),
                defaultText(bible.status(), "CONFIRMED")
        ));
    }

    @Override
    public StoryBibleVO getBible(String projectCode) {
        requireText(projectCode, "projectCode");
        return projectRepository.findBible(projectCode);
    }

    @Override
    public PlanningDraftVO generateStoryBible(String projectCode, String requirement) {
        requireText(projectCode, "projectCode");
        NovelProjectVO project = getProject(projectCode);
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        StoryBibleVO currentBible = getBible(projectCode);
        String systemPrompt;
        String userPrompt;
        if (currentBible == null) {
            systemPrompt = PlanningPrompts.STORY_BIBLE_INIT_SYSTEM;
            userPrompt = "项目标题=" + promptValue(project.title())
                    + "\n题材=" + promptValue(project.genre())
                    + "\n预计章节数=" + (project.targetChapterCount() == null
                    ? "无" : project.targetChapterCount())
                    + "\n用户补充要求=" + promptValue(normalizedRequirement);
        } else {
            systemPrompt = PlanningPrompts.STORY_BIBLE_REVISION_SYSTEM;
            userPrompt = storyBibleRevisionPrompt(currentBible, normalizedRequirement);
        }
        String modelStage = currentBible == null
                ? "STORY_BIBLE_INIT"
                : "STORY_BIBLE_REVISION";
        StoryBibleDraftVO generated = modelPort.call(
                systemPrompt,
                userPrompt,
                StoryBibleDraftVO.class,
                modelStage,
                1
        );
        return draftRepository.save(
                projectCode,
                "STORY_BIBLE",
                normalizeStoryBibleDraft(generated)
        );
    }

    @Override
    public PlanningDraftVO generateCharacters(
            String projectCode,
            Integer preferredCount,
            String requirement
    ) {
        requireText(projectCode, "projectCode");
        validatePreferredCount(preferredCount);
        NovelProjectVO project = getProject(projectCode);
        StoryBibleVO bible = getBible(projectCode);
        List<StoryCharacterVO> existingCharacters = projectRepository.findCharacters(projectCode);
        List<OutlineNodeVO> outlines = planningRepository == null
                ? List.of() : planningRepository.listOutlines(projectCode);
        String userPrompt = characterGenerationPrompt(
                project, bible, existingCharacters, outlines, preferredCount, requirement
        );
        log.info(
                "[CHARACTER_GENERATION] prompt ready projectCode={} promptChars={}",
                projectCode,
                userPrompt.length()
        );

        CharacterDraftListVO generated = modelPort.call(
                PlanningPrompts.CHARACTER_SYSTEM,
                userPrompt,
                CharacterDraftListVO.class,
                "CHARACTER_GENERATION",
                1
        );
        return draftRepository.save(
                projectCode,
                "CHARACTERS",
                normalizeCharacterDrafts(generated)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StoryCharacterVO> confirmCharacters(
            String projectCode,
            String draftId,
            List<CharacterDraftVO> characters
    ) {
        requireText(projectCode, "projectCode");
        PlanningDraftVO draft = requireDraft(projectCode, draftId, "CHARACTERS");
        getProject(projectCode);
        if (!(draft.payload() instanceof CharacterDraftListVO original)) {
            throw illegalParameter("人物草稿内容无效");
        }
        List<CharacterDraftVO> edited = characters == null
                ? original.characters()
                : characters;
        if (edited == null) {
            throw illegalParameter("人物草稿列表不能为空");
        }
        validateCharacterNames(projectCode, edited);
        List<StoryCharacterVO> saved = new ArrayList<>(edited.size());
        for (CharacterDraftVO value : edited) {
            if (value == null) {
                throw illegalParameter("人物草稿项不能为空");
            }
            saved.add(addCharacter(projectCode, new StoryCharacterVO(
                    null,
                    value.name(),
                    value.role(),
                    value.gender(),
                    value.ageDescription(),
                    value.appearance(),
                    value.personality(),
                    value.backgroundStory(),
                    value.note(),
                    null,
                    null,
                    null
            )));
        }
        draftRepository.remove(projectCode, draftId);
        return List.copyOf(saved);
    }

    @Override
    public void discardCharacterDraft(String projectCode, String draftId) {
        requireText(projectCode, "projectCode");
        requireDraft(projectCode, draftId, "CHARACTERS");
        draftRepository.remove(projectCode, draftId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoryBibleVO confirmStoryBible(
            String projectCode,
            String draftId,
            StoryBibleVO editedBible
    ) {
        requireText(projectCode, "projectCode");
        PlanningDraftVO draft = requireDraft(projectCode, draftId, "STORY_BIBLE");
        getProject(projectCode);
        if (!(draft.payload() instanceof StoryBibleDraftVO)) {
            throw illegalParameter("故事圣经草稿内容无效");
        }
        StoryBibleVO confirmed = toConfirmedStoryBible(editedBible);
        StoryBibleVO saved = saveBible(projectCode, confirmed);
        draftRepository.remove(projectCode, draftId);
        return saved;
    }

    @Override
    public List<StoryCharacterVO> listCharacters(String projectCode) {
        requireText(projectCode, "projectCode");
        return projectRepository.findCharacters(projectCode);
    }

    @Override
    public StoryCharacterVO addCharacter(
            String projectCode,
            StoryCharacterVO character
    ) {
        requireText(projectCode, "projectCode");
        if (character == null) {
            throw illegalParameter("character 不能为空");
        }
        requireText(character.name(), "name");
        validateCharacterNameAvailable(projectCode, character.name());
        return projectRepository.addCharacter(
                projectCode,
                normalizeCharacter(character, null)
        );
    }

    @Override
    public StoryCharacterVO updateCharacter(
            String projectCode,
            String characterCode,
            StoryCharacterVO character
    ) {
        return updateCharacterWithWarning(projectCode, characterCode, character).character();
    }

    @Override
    public CharacterUpdateResultVO updateCharacterWithWarning(
            String projectCode,
            String characterCode,
            StoryCharacterVO character
    ) {
        requireText(projectCode, "projectCode");
        requireText(characterCode, "characterCode");
        if (character == null) {
            throw illegalParameter("character 不能为空");
        }
        StoryCharacterVO existing = findCharacter(projectCode, characterCode);
        StoryCharacterVO profile = new StoryCharacterVO(
                characterCode,
                character.name(),
                character.roleType(),
                character.gender(),
                character.ageDescription(),
                character.appearance(),
                character.personality(),
                character.backgroundStory(),
                character.note(),
                existing == null ? null : existing.currentStateJson(),
                existing == null ? null : existing.lifeStatus(),
                existing == null ? null : existing.status()
        );
        StoryCharacterVO normalized = normalizeCharacter(profile, characterCode);
        StoryCharacterVO saved = projectRepository.updateCharacter(
                projectCode,
                characterCode,
                normalized
        );
        String warning = existing != null
                && projectRepository.hasOutlines(projectCode)
                && hasStructuralCharacterChange(existing, normalized)
                ? CHARACTER_OUTLINE_WARNING : null;
        return new CharacterUpdateResultVO(saved, warning);
    }

    private boolean hasStructuralCharacterChange(
            StoryCharacterVO existing,
            StoryCharacterVO updated
    ) {
        return !java.util.Objects.equals(existing.roleType(), updated.roleType())
                || !java.util.Objects.equals(existing.backgroundStory(), updated.backgroundStory())
                || !java.util.Objects.equals(existing.lifeStatus(), updated.lifeStatus());
    }

    @Override
    public void deleteCharacter(String projectCode, String characterCode) {
        requireText(projectCode, "projectCode");
        requireText(characterCode, "characterCode");
        projectRepository.deleteCharacter(projectCode, characterCode);
    }

    @Override
    public GeneratedChapterVO overwriteChapterContent(
            String projectCode,
            int chapterNumber,
            String title,
            String content
    ) {
        requireText(projectCode, "projectCode");
        if (chapterNumber <= 0) {
            throw illegalParameter("chapterNumber 必须大于 0");
        }
        requireText(title, "title");
        requireText(content, "content");
        int wordCount = Math.toIntExact(content.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count());
        GeneratedChapterVO savedChapter = projectRepository.overwriteChapterContent(
                projectCode,
                chapterNumber,
                title,
                content,
                wordCount
        );
        if (chapterService == null) {
            return savedChapter;
        }
        try {
            chapterService.resyncChapterDerivedData(projectCode, chapterNumber);
            GeneratedChapterVO refreshedChapter = projectRepository.findChapter(
                    projectCode, chapterNumber
            );
            return refreshedChapter == null ? savedChapter : refreshedChapter;
        } catch (RuntimeException exception) {
            log.warn(
                    "正文保存成功，但章节记忆自动更新失败，章节保持 DIRTY，projectCode={}，chapterNumber={}",
                    projectCode,
                    chapterNumber,
                    exception
            );
            return savedChapter;
        }
    }

    @Override
    public GeneratedChapterVO resyncChapterDerivedData(
            String projectCode,
            int chapterNumber
    ) {
        requireText(projectCode, "projectCode");
        if (chapterNumber <= 0) {
            throw illegalParameter("chapterNumber 必须大于 0");
        }
        if (chapterService == null) {
            throw illegalParameter("正文派生数据同步服务不可用");
        }
        ChapterGenerationResultVO result = chapterService.resyncChapterDerivedData(
                projectCode, chapterNumber);
        return projectRepository.findChapter(projectCode, result.chapterNumber());
    }

    @Override
    public void deleteChapter(String projectCode, int chapterNumber) {
        requireText(projectCode, "projectCode");
        if (chapterNumber <= 0) throw illegalParameter("chapterNumber 必须大于 0");
        projectRepository.deleteChapter(projectCode, chapterNumber);
    }

    private StoryCharacterVO normalizeCharacter(
            StoryCharacterVO character,
            String pathCharacterCode
    ) {
        if (character == null) {
            throw illegalParameter("character 不能为空");
        }
        String characterCode = pathCharacterCode == null
                ? generateCharacterCode()
                : pathCharacterCode;
        requireText(characterCode, "characterCode");
        requireText(character.name(), "name");
        requireText(character.roleType(), "roleType");
        String roleType = normalizeRoleType(character.roleType());
        String gender = normalizeGender(character.gender());
        if ("MALE_LEAD".equals(roleType)
                && !"MALE".equals(gender)) {
            throw illegalParameter("MALE_LEAD 的 gender 必须为 MALE");
        }
        boolean creating = pathCharacterCode == null;
        return new StoryCharacterVO(
                characterCode,
                character.name(),
                roleType,
                gender,
                character.ageDescription(),
                character.appearance(),
                character.personality(),
                character.backgroundStory(),
                character.note(),
                creating ? "{}" : normalizeCurrentStateJson(character.currentStateJson()),
                creating ? "ALIVE" : defaultText(character.lifeStatus(), "ALIVE"),
                creating ? "ACTIVE" : defaultText(character.status(), "ACTIVE")
        );
    }

    private String normalizeCurrentStateJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        try {
            OBJECT_MAPPER.readTree(value);
            return value;
        } catch (Exception e) {
            throw illegalParameter("currentStateJson 必须是合法 JSON");
        }
    }

    private StoryCharacterVO findCharacter(String projectCode, String characterCode) {
        List<StoryCharacterVO> characters = projectRepository.findCharacters(projectCode);
        if (characters == null) {
            return null;
        }
        return characters.stream()
                .filter(character -> character != null
                        && characterCode.equals(character.characterCode()))
                .findFirst()
                .orElse(null);
    }

    private String generateCharacterCode() {
        return "char-" + UUID.randomUUID().toString().replace("-", "");
    }

    private void validateCharacterNames(
            String projectCode,
            List<CharacterDraftVO> characters
    ) {
        Set<String> names = new HashSet<>();
        List<StoryCharacterVO> existing = projectRepository.findCharacters(projectCode);
        if (existing != null) {
            for (StoryCharacterVO character : existing) {
                if (character != null && character.name() != null
                        && !character.name().isBlank()) {
                    names.add(characterNameKey(character.name()));
                }
            }
        }
        for (CharacterDraftVO character : characters) {
            if (character == null) {
                throw illegalParameter("人物草稿项不能为空");
            }
            requireText(character.name(), "name");
            if (!names.add(characterNameKey(character.name()))) {
                throw illegalParameter("人物姓名已存在：" + character.name());
            }
        }
    }

    private void validateCharacterNameAvailable(String projectCode, String name) {
        List<StoryCharacterVO> existing = projectRepository.findCharacters(projectCode);
        if (existing == null) {
            return;
        }
        String key = characterNameKey(name);
        for (StoryCharacterVO character : existing) {
            if (character != null && character.name() != null
                    && key.equals(characterNameKey(character.name()))) {
                throw illegalParameter("人物姓名已存在：" + name);
            }
        }
    }

    private String characterNameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRoleType(String role) {
        String value = role.trim();
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "男主", "男主角", "MALE_LEAD" -> "MALE_LEAD";
            case "女主", "女主角", "FEMALE_LEAD" -> "FEMALE_LEAD";
            case "盟友", "ALLY" -> "ALLY";
            case "对手", "RIVAL" -> "RIVAL";
            case "反派", "ANTAGONIST", "VILLAIN" -> "ANTAGONIST";
            case "配角", "SUPPORTING" -> "SUPPORTING";
            default -> throw illegalParameter("不支持的人物定位：" + role);
        };
    }

    private String normalizeGender(String gender) {
        String value = defaultText(gender, "OTHER").trim();
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "男", "男性", "MALE" -> "MALE";
            case "女", "女性", "FEMALE" -> "FEMALE";
            case "其他", "其他或未知", "未知", "OTHER" -> "OTHER";
            default -> throw illegalParameter("不支持的性别：" + gender);
        };
    }

    @Override
    public GeneratedChapterVO getChapter(
            String projectCode,
            int chapterNumber
    ) {
        requireText(projectCode, "projectCode");
        if (chapterNumber <= 0) {
            throw illegalParameter("chapterNumber 必须大于 0");
        }
        GeneratedChapterVO chapter = projectRepository.findChapter(
                projectCode,
                chapterNumber
        );
        if (chapter == null) {
            throw illegalParameter("章节不存在，chapterNumber=" + chapterNumber);
        }
        return chapter;
    }

    @Override
    public List<GeneratedChapterVO> listChapters(String projectCode) {
        requireText(projectCode, "projectCode");
        return projectRepository.findChapters(projectCode);
    }

    @Override
    public List<GeneratedChapterVO> searchChapters(String projectCode, String keyword) {
        requireText(projectCode, "projectCode");
        requireText(keyword, "keyword");
        return projectRepository.searchChapters(projectCode, keyword.trim(), 50);
    }

    @Override
    public List<NovelProjectVO> listProjects() {
        return projectRepository.findProjects();
    }

    @Override
    public List<VolumeChapterGroupVO> listChaptersByVolume(
            String projectCode
    ) {
        requireText(projectCode, "projectCode");
        return projectRepository.findChaptersByVolume(projectCode);
    }

    @Override
    public NovelProjectVO getProject(String projectCode) {
        requireText(projectCode, "projectCode");
        NovelProjectVO project = projectRepository.findProject(projectCode);
        if (project == null) {
            throw illegalParameter("项目不存在，projectCode=" + projectCode);
        }
        return project;
    }

    private PlanningDraftVO requireDraft(
            String projectCode,
            String draftId,
            String draftType
    ) {
        requireText(draftId, "draftId");
        PlanningDraftVO draft = draftRepository.find(projectCode, draftId)
                .orElseThrow(() -> illegalParameter("草稿不存在或已过期"));
        if (!draftType.equals(draft.draftType())) {
            throw illegalParameter("草稿类型不匹配");
        }
        return draft;
    }

    private StoryBibleDraftVO normalizeStoryBibleDraft(StoryBibleDraftVO value) {
        if (value == null) {
            throw illegalParameter("故事圣经草稿不能为空");
        }
        requireText(value.oneSentencePremise(), "oneSentencePremise");
        if (value.hardRules() == null) {
            throw illegalParameter("故事圣经硬规则不能为空");
        }
        return new StoryBibleDraftVO(
                value.oneSentencePremise().trim(),
                value.coreTheme(),
                value.mainConflict(),
                value.endingDirection(),
                value.worldBackground(),
                value.powerSystem() == null
                        ? null : normalizePowerSystemDraft(value.powerSystem()),
                List.copyOf(value.hardRules()),
                value.styleGuide()
        );
    }

    private PowerSystemDraftVO normalizePowerSystemDraft(PowerSystemDraftVO value) {
        return new PowerSystemDraftVO(
                value.name(),
                value.description(),
                value.levels(),
                value.supplement()
        );
    }

    private String storyBibleRevisionPrompt(StoryBibleVO bible, String requirement) {
        return "【当前正式 Story Bible】"
                + "\n一句话故事=" + promptValue(bible.oneSentencePremise())
                + "\n核心主题=" + promptValue(bible.coreTheme())
                + "\n主要矛盾=" + promptValue(bible.mainConflict())
                + "\n结局方向=" + promptValue(bible.endingDirection())
                + "\n世界背景=" + promptValue(bible.worldBackground())
                + "\n" + renderPowerSystem(bible.powerSystemJson())
                + "\n" + renderHardRules(bible.hardRulesJson())
                + "\n写作风格=" + promptValue(bible.styleGuide())
                + "\n【用户调整要求】\n" + promptValue(requirement);
    }

    private String characterGenerationPrompt(
            NovelProjectVO project,
            StoryBibleVO bible,
            List<StoryCharacterVO> existingCharacters,
            List<OutlineNodeVO> outlines,
            Integer preferredCount,
            String requirement
    ) {
        StringBuilder prompt = new StringBuilder()
                .append("项目标题=").append(promptValue(project.title()))
                .append("\n题材=").append(promptValue(project.genre()))
                .append("\n本次建议新增 ").append(preferredCount).append(" 名人物")
                .append("\n当前 Story Bible（故事设定）：\n");
        if (bible == null) {
            prompt.append("无\n");
        } else {
            prompt.append("一句话故事=").append(promptValue(bible.oneSentencePremise())).append('\n')
                    .append("核心主题=").append(promptValue(bible.coreTheme())).append('\n')
                    .append("主要矛盾=").append(promptValue(bible.mainConflict())).append('\n')
                    .append("结局方向=").append(promptValue(bible.endingDirection())).append('\n')
                    .append("世界背景=").append(promptValue(bible.worldBackground())).append('\n')
                    .append(renderPowerSystem(bible.powerSystemJson())).append('\n')
                    .append(renderHardRules(bible.hardRulesJson())).append('\n')
                    .append("写作风格=").append(promptValue(bible.styleGuide())).append('\n');
        }
        appendOutlinePlanningContext(prompt, outlines);
        prompt.append("已有角色摘要：\n");
        if (existingCharacters == null || existingCharacters.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (StoryCharacterVO character : existingCharacters) {
                prompt.append("- 姓名：").append(promptValue(character.name()))
                        .append("；定位：").append(roleLabel(character.roleType()))
                        .append("；性别：").append(genderLabel(character.gender()))
                        .append("；年龄：").append(promptValue(character.ageDescription()))
                        .append("；外貌：").append(promptValue(character.appearance()))
                        .append("；性格：").append(promptValue(character.personality()))
                        .append("；人物经历：").append(promptValue(character.backgroundStory()))
                        .append("；备注：").append(promptValue(character.note()))
                        .append('\n');
            }
        }
        return prompt.append("用户补充要求=").append(promptValue(requirement)).toString();
    }

    private void appendOutlinePlanningContext(
            StringBuilder prompt,
            List<OutlineNodeVO> outlines
    ) {
        prompt.append("【已有大纲剧情位置】\n");
        List<OutlineNodeVO> source = outlines == null ? List.of() : outlines;
        OutlineNodeVO book = source.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.BOOK
                        && node.parentNodeCode() == null)
                .findFirst()
                .orElse(null);
        if (book == null) {
            prompt.append("无（尚未确认 BOOK）\n");
            return;
        }
        prompt.append("全书大纲：标题=").append(promptValue(book.title()))
                .append("；摘要=").append(promptValue(book.summary())).append('\n');

        OutlineNodeVO currentVolume = source.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.VOLUME
                        && java.util.Objects.equals(book.nodeCode(), node.parentNodeCode()))
                .max(java.util.Comparator.comparing(
                        OutlineNodeVO::sequenceNo,
                        java.util.Comparator.nullsFirst(Integer::compareTo)
                ))
                .orElse(null);
        if (currentVolume == null) {
            prompt.append("当前卷：无\n");
            return;
        }
        prompt.append("当前卷：标题=").append(promptValue(currentVolume.title()))
                .append("；摘要=").append(promptValue(currentVolume.summary()))
                .append("；章节范围=").append(promptValue(currentVolume.startChapter()))
                .append('～').append(promptValue(currentVolume.endChapter())).append('\n')
                .append("以上是已确认的剧情规划，用于定位本次新增人物；它不是正式人物档案，");
        prompt.append("大纲中尚未定义的人物只能作为待补充的剧情位置。\n");
    }

    private void validatePreferredCount(Integer preferredCount) {
        if (preferredCount == null
                || preferredCount < 1
                || preferredCount > MAX_CHARACTER_PREFERRED_COUNT) {
            throw illegalParameter("preferredCount 必须在 1 到 "
                    + MAX_CHARACTER_PREFERRED_COUNT + " 之间");
        }
    }

    private CharacterDraftListVO normalizeCharacterDrafts(CharacterDraftListVO value) {
        StructuredModelOutputValidator.validate(value);
        return new CharacterDraftListVO(List.copyOf(value.characters()));
    }

    private String roleLabel(String role) {
        if (role == null || role.isBlank()) {
            return "其他角色";
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "MALE_LEAD", "男主", "男主角" -> "男主角";
            case "FEMALE_LEAD", "女主", "女主角" -> "女主角";
            case "ALLY", "盟友" -> "盟友";
            case "RIVAL", "对手" -> "对手";
            case "ANTAGONIST", "VILLAIN", "反派" -> "反派";
            case "SUPPORTING", "配角" -> "配角";
            default -> "其他角色";
        };
    }

    private String genderLabel(String gender) {
        if (gender == null || gender.isBlank()) {
            return "其他";
        }
        return switch (gender.trim().toUpperCase(Locale.ROOT)) {
            case "MALE", "男", "男性" -> "男";
            case "FEMALE", "女", "女性" -> "女";
            default -> "其他";
        };
    }

    private String renderPowerSystem(String powerSystemJson) {
        if (powerSystemJson == null || powerSystemJson.isBlank()) {
            return "特殊体系\n无";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(powerSystemJson);
            if (root == null || root.isNull()) {
                return "特殊体系\n无";
            }
            StringBuilder text = new StringBuilder("特殊体系\n");
            if (root.isObject()) {
                boolean hasContent = false;
                hasContent |= appendPowerField(text, "名称", firstField(root, "name", "名称"));
                hasContent |= appendPowerField(text, "说明", firstField(
                        root, "description", "说明", "mechanism", "机制", "source"
                ));
                JsonNode levels = firstField(
                        root, "levels", "level", "等级/境界", "等级", "境界", "层级", "ranks"
                );
                if (levels != null && !levels.isNull()) {
                    if (levels.isArray()) {
                        text.append("等级/境界：\n");
                        appendBullets(text, levels);
                    } else {
                        hasContent |= appendPowerField(text, "等级/境界", levels);
                    }
                }
                hasContent |= appendPowerField(text, "补充设定", firstField(
                        root, "supplement", "补充设定", "cost", "代价", "limit", "限制"
                ));
                if (levels != null && !levels.isNull() && levels.isArray()) {
                    hasContent = true;
                }
                for (var field : root.properties()) {
                    if (!isPowerField(field.getKey()) && !field.getValue().isNull()) {
                        text.append(field.getKey()).append('：')
                                .append(naturalText(field.getValue())).append('\n');
                        hasContent = true;
                    }
                }
                if (!hasContent) {
                    text.append("无\n");
                }
            } else if (root.isArray()) {
                text.append("等级/境界：\n");
                appendBullets(text, root);
            } else {
                String value = naturalText(root).trim();
                text.append(value.isBlank() ? "无" : value).append('\n');
            }
            return text.toString().trim();
        } catch (RuntimeException ignored) {
            // 兼容历史非 JSON 配置，继续以文本形式提供给模型。
        }
        return "特殊体系\n" + legacyLines(powerSystemJson);
    }

    private boolean appendPowerField(StringBuilder text, String label, JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        String rendered = naturalText(value).trim();
        if (rendered.isBlank()) {
            return false;
        }
        text.append(label).append('：').append(rendered).append('\n');
        return true;
    }

    private JsonNode firstField(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private boolean isPowerField(String name) {
        return switch (name) {
            case "name", "名称", "description", "说明", "mechanism", "机制", "source",
                    "levels", "level", "等级/境界", "等级", "层级", "境界", "ranks",
                    "supplement", "补充设定", "cost", "代价", "limit", "限制" -> true;
            default -> false;
        };
    }

    private void appendBullets(StringBuilder text, JsonNode value) {
        if (value.isArray()) {
            value.forEach(item -> appendBullet(text, item));
        } else {
            appendBullet(text, value);
        }
        if (text.toString().endsWith("等级/境界：\n") || text.toString().endsWith("层级：\n")) {
            text.append("- 无\n");
        }
    }

    private void appendBullet(StringBuilder text, JsonNode value) {
        String item = naturalText(value).trim();
        if (!item.isBlank() && !"null".equalsIgnoreCase(item)) {
            text.append("- ").append(item).append('\n');
        }
    }

    private String naturalText(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        if (value.isArray()) {
            StringBuilder text = new StringBuilder();
            value.forEach(item -> {
                String itemText = naturalText(item).trim();
                if (!itemText.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append('、');
                    }
                    text.append(itemText);
                }
            });
            return text.toString();
        }
        StringBuilder text = new StringBuilder();
        value.properties().forEach(field -> {
            String fieldText = naturalText(field.getValue()).trim();
            if (!fieldText.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('；');
                }
                text.append(field.getKey()).append('：').append(fieldText);
            }
        });
        return text.toString();
    }

    private String renderHardRules(String hardRulesJson) {
        String header = "不可违反的规则\n";
        StringBuilder text = new StringBuilder(header);
        for (String rule : parseHardRules(hardRulesJson)) {
            text.append("- ").append(rule).append('\n');
        }
        if (text.length() == header.length()) {
            text.append("- 无\n");
        }
        return text.toString().trim();
    }

    private List<String> parseHardRules(String hardRulesJson) {
        if (hardRulesJson == null || hardRulesJson.isBlank()) {
            return List.of();
        }
        List<String> rules = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(hardRulesJson);
            if (root == null || root.isNull()) {
                return List.of();
            }
            if (root.isObject()) {
                JsonNode explicitRules = firstField(root, "rules", "hardRules", "硬规则", "不可违反的规则");
                if (explicitRules != null) {
                    appendRules(rules, explicitRules);
                } else {
                    root.properties().forEach(field -> {
                        String value = naturalText(field.getValue()).trim();
                        if (!value.isBlank()) {
                            addRule(rules, field.getKey() + "：" + value);
                        }
                    });
                }
            } else {
                appendRules(rules, root);
            }
            return List.copyOf(rules);
        } catch (RuntimeException ignored) {
            // 兼容历史非 JSON 硬规则配置，按文本行继续使用。
        }
        return legacyRuleLines(hardRulesJson);
    }

    private void appendRules(List<String> rules, JsonNode value) {
        if (value.isArray()) {
            value.forEach(item -> appendRules(rules, item));
            return;
        }
        if (value.isObject()) {
            value.properties().forEach(field -> {
                String fieldValue = naturalText(field.getValue()).trim();
                if (!fieldValue.isBlank()) {
                    addRule(rules, field.getKey() + "：" + fieldValue);
                }
            });
            return;
        }
        addRule(rules, naturalText(value));
    }

    private void addRule(List<String> rules, String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return;
        }
        for (String line : value.trim().split("\\R")) {
            if (!line.isBlank() && !"null".equalsIgnoreCase(line.trim())) {
                rules.add(line.trim());
            }
        }
    }

    private String legacyLines(String value) {
        StringBuilder text = new StringBuilder();
        for (String line : value.trim().split("\\R")) {
            if (!line.isBlank() && !"null".equalsIgnoreCase(line.trim())) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(line.trim());
            }
        }
        return text.isEmpty() ? "无" : text.toString();
    }

    private List<String> legacyRuleLines(String value) {
        List<String> rules = new ArrayList<>();
        for (String line : value.trim().split("\\R")) {
            addRule(rules, line);
        }
        return List.copyOf(rules);
    }

    private StoryBibleVO toConfirmedStoryBible(StoryBibleVO value) {
        if (value == null) {
            throw illegalParameter("editedBible 不能为空");
        }
        requireText(value.oneSentencePremise(), "oneSentencePremise");
        return new StoryBibleVO(
                value.oneSentencePremise().trim(),
                value.coreTheme(),
                value.mainConflict(),
                value.endingDirection(),
                value.worldBackground(),
                value.powerSystemJson(),
                value.hardRulesJson(),
                value.styleGuide(),
                "CONFIRMED"
        );
    }

    private String promptValue(String value) {
        return value == null || value.isBlank() ? "无" : value.trim();
    }

    private String promptValue(Object value) {
        return value == null ? "无" : value.toString();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw illegalParameter(field + " 不能为空");
        }
    }

    private AppException illegalParameter(String detail) {
        if (detail != null
                && (detail.contains("projectCode")
                || detail.contains("characterCode")
                || detail.contains("chapterNumber")
                || detail.contains("draftId")
                || detail.contains("targetChapterCount")
                || detail.contains("wordsPerChapter")
                || detail.contains("preferredCount")
                || detail.contains("currentStateJson")
                || detail.contains("editedBible")
                || detail.contains("oneSentencePremise")
                || detail.contains("hardRules"))) {
            return AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
        }
        return AppException.user(ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
    }
}
