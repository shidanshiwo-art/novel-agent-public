package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.service.agent.ChapterPromptFormatter;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.service.LegacyFallbackMetricsCollector;
import cn.ninth.novel.domain.memory.service.LegacyFallbackRouter;
import cn.ninth.novel.domain.memory.service.MemoryContextProviderMetricsRecorder;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.*;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanningService implements IPlanningService {

    private static final int CORE_CHARACTER_PROMPT_LIMIT = 8;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IPlanningModelPort modelPort;
    private final IPlanningDraftRepository draftRepository;
    private final IPlanningRepository planningRepository;
    private final LegacyFallbackRouter legacyFallbackRouter;
    private final RelevantCharacterSelector relevantCharacterSelector =
            new RelevantCharacterSelector();

    public PlanningService(
            IPlanningModelPort modelPort,
            IPlanningDraftRepository draftRepository,
            IPlanningRepository planningRepository
    ) {
        this(modelPort, draftRepository, planningRepository,
                new MemoryContextProviderMetricsRecorder());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PlanningService(
            IPlanningModelPort modelPort,
            IPlanningDraftRepository draftRepository,
            IPlanningRepository planningRepository,
            MemoryContextProviderMetricsRecorder metricsRecorder
    ) {
        this.modelPort = modelPort;
        this.draftRepository = draftRepository;
        this.planningRepository = planningRepository;
        this.legacyFallbackRouter = new LegacyFallbackRouter(
                metricsRecorder, new LegacyFallbackMetricsCollector());
    }

    @Override
    public List<OutlineNodeVO> listOutlineTree(String projectCode) {
        requireText(projectCode, "projectCode");
        requireProject(projectCode);
        return planningRepository.listOutlines(projectCode);
    }

    @Override
    public OutlineNodeVO createOutlineNode(
            String projectCode,
            OutlineNodeVO outline
    ) {
        requireText(projectCode, "projectCode");
        NovelProjectVO project = requireProject(projectCode);
        OutlineNodeVO normalized = bindRootOutlineTitle(project.title(), normalizeCreatedOutline(
                projectCode, normalizeParentCode(outline),
                planningRepository.listOutlines(projectCode)
        ));
        List<OutlineNodeVO> existing = planningRepository.listOutlines(projectCode);
        validateCreateOutline(projectCode, normalized, existing);
        return planningRepository.saveOutline(projectCode, normalized);
    }

    @Override
    public OutlineNodeVO updateOutlineNode(
            String projectCode,
            OutlineNodeVO outline
    ) {
        requireText(projectCode, "projectCode");
        NovelProjectVO project = requireProject(projectCode);
        OutlineNodeVO normalized = normalizeParentCode(outline);
        validateOutlineFields(normalized);
        OutlineNodeVO existing = planningRepository.findOutline(
                projectCode, normalized.nodeCode()
        );
        if (existing == null) {
            throw illegalParameter("大纲节点不存在，nodeCode=" + normalized.nodeCode());
        }
        if (existing.nodeKind() != normalized.nodeKind()) {
            throw illegalParameter("大纲节点类型不能修改");
        }
        normalized = bindRootOutlineTitle(project.title(), normalized);
        if (!Objects.equals(existing.parentNodeCode(), normalized.parentNodeCode())) {
            throw illegalParameter("大纲节点 parent 不能修改");
        }
        boolean rangeChanged = !Objects.equals(existing.startChapter(), normalized.startChapter())
                || !Objects.equals(existing.endChapter(), normalized.endChapter());
        if (existing.nodeKind() == OutlineNodeKindEnum.ARC && rangeChanged) {
            throw illegalParameter("ARC 章节号由后端分配，不能修改章节范围");
        }
        if (rangeChanged && (planningRepository.hasChildren(projectCode, normalized.nodeCode())
                || planningRepository.hasChapterPlans(projectCode, normalized.nodeCode()))) {
            throw illegalParameter("存在子节点或 ChapterPlan 时不能修改章节范围");
        }
        validateOutlineHierarchy(projectCode, normalized);
        normalized = markVolumePlannedAfterContentSaved(existing, normalized);
        return planningRepository.updateOutline(projectCode, normalized);
    }

    @Override
    public OutlineNodeVO reorderOutlineNode(
            String projectCode,
            String nodeCode,
            Integer targetSequence
    ) {
        requireText(projectCode, "projectCode");
        requireText(nodeCode, "nodeCode");
        requireProject(projectCode);
        if (targetSequence == null || targetSequence <= 0) {
            throw illegalParameter("targetSequence 必须大于 0");
        }
        if (planningRepository.findOutline(projectCode, nodeCode) == null) {
            throw illegalParameter("大纲节点不存在，nodeCode=" + nodeCode);
        }
        return planningRepository.reorderOutline(projectCode, nodeCode, targetSequence);
    }

    @Override
    public void deleteOutlineNode(String projectCode, String nodeCode) {
        requireText(projectCode, "projectCode");
        requireText(nodeCode, "nodeCode");
        requireProject(projectCode);
        if (planningRepository.findOutline(projectCode, nodeCode) == null) {
            throw illegalParameter("大纲节点不存在，nodeCode=" + nodeCode);
        }
        if (planningRepository.hasChildren(projectCode, nodeCode)) {
            throw illegalParameter("只能删除叶节点");
        }
        if (planningRepository.hasChapterPlans(projectCode, nodeCode)) {
            throw illegalParameter("ChapterPlan 存在时不能删除");
        }
        planningRepository.deleteOutline(projectCode, nodeCode);
    }

    @Override
    public List<ChapterOutlineVO> listChapterPlans(String projectCode) {
        requireText(projectCode, "projectCode");
        return planningRepository.findChapterPlanOutlines(projectCode);
    }

    @Override
    public ChapterOutlineVO updateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            ChapterOutlineVO chapterPlan
    ) {
        requireText(projectCode, "projectCode");
        if (chapterNumber == null || chapterNumber <= 0) {
            throw illegalParameter("chapterNumber 必须大于 0");
        }
        if (chapterPlan == null) {
            throw illegalParameter("chapterPlan 不能为空");
        }
        requireText(chapterPlan.title(), "title");
        requireText(chapterPlan.summary(), "summary");
        OutlineContext currentContext = resolveChapterArcContext(
                planningRepository.listOutlines(projectCode), chapterNumber
        );
        if (!Objects.equals(chapterPlan.title(), currentContext.target().title())) {
            throw illegalParameter("ChapterPlan.title 与当前 ARC.title 不一致");
        }
        return planningRepository.updateChapterPlan(
                projectCode,
                chapterNumber,
                new ChapterOutlineVO(
                        chapterNumber,
                        currentContext.target().nodeCode(),
                        currentContext.target().title(),
                        chapterPlan.summary(),
                        null
                )
        );
    }

    @Override
    public PlanningDraftVO generateRootOutline(String projectCode, String requirement) {
        requireText(projectCode, "projectCode");
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        String generationId = UUID.randomUUID().toString();
        NovelProjectVO project = requireProject(projectCode);
        if (project.targetChapterCount() == null || project.targetChapterCount() <= 0) {
            throw illegalParameter("项目 targetChapterCount 必须大于 0");
        }

        StoryBibleVO bible = planningRepository.findBible(projectCode);
        List<StoryCharacterVO> characters = planningRepository.findCharacters(projectCode);
        if (characters == null || characters.isEmpty()) {
            throw illegalParameter("请先创建并确认核心人物，再生成故事大纲");
        }
        RootOutlineDraftVO generated = modelPort.call(
                PlanningPrompts.ROOT_OUTLINE_SYSTEM,
                buildRootOutlinePrompt(project, bible, characters, normalizedRequirement),
                RootOutlineDraftVO.class,
                "ROOT_OUTLINE",
                1
        );
        OutlineNodeVO root = normalizeRootOutline(
                generated, project.title(), project.targetChapterCount()
        );
        return draftRepository.save(projectCode, "ROOT_OUTLINE", root);
    }

    private String buildRootOutlinePrompt(
            NovelProjectVO project,
            StoryBibleVO bible,
            List<StoryCharacterVO> characters,
            String requirement
    ) {
        StringBuilder prompt = new StringBuilder()
                .append("【项目】\n")
                .append("标题：").append(promptValue(project.title())).append('\n')
                .append("题材：").append(promptValue(project.genre())).append('\n')
                .append("预计章节数：").append(promptValue(project.targetChapterCount())).append('\n')
                .append("\n【创作方向】\n")
                .append("一句话故事：").append(promptValue(bible == null ? null : bible.oneSentencePremise())).append('\n')
                .append("核心主题：").append(promptValue(bible == null ? null : bible.coreTheme())).append('\n')
                .append("主要矛盾：").append(promptValue(bible == null ? null : bible.mainConflict())).append('\n')
                .append("结局方向：").append(promptValue(bible == null ? null : bible.endingDirection())).append('\n')
                .append("\n【世界背景】\n")
                .append(promptValue(bible == null ? null : bible.worldBackground())).append('\n')
                .append("\n【已确认核心人物】\n")
                .append("以下人物设定属于既定事实。\n")
                .append("- 不得修改姓名、身份、核心背景、主要关系\n")
                .append("- 不得将已有角色替换为新角色承担同一核心职责\n")
                .append("- 大纲必须围绕已有核心人物展开\n")
                .append("- 可以安排角色发展，但不能把未来发展写成初始既定事实\n")
                .append("- 如剧情需要，可引入尚未定义的次要人物，但不要为其建立详细人物档案\n");
        appendCoreCharacterSummaries(prompt, characters);

        return prompt.append("\n【用户补充要求】\n")
                .append(requirement)
                .toString();
    }

    private String buildChildOutlinePrompt(
            NovelProjectVO project,
            StoryBibleVO bible,
            OutlineNodeVO parent,
            List<StoryCharacterVO> characters,
            List<OutlineNodeVO> outlines,
            OutlineNodeKindEnum childKind,
            Integer targetChapter,
            String requirement
    ) {
        StringBuilder prompt = new StringBuilder()
                .append("【项目】\n")
                .append("标题：").append(promptValue(project.title())).append('\n')
                .append("题材：").append(promptValue(project.genre())).append('\n')
                .append("预计章节数：").append(promptValue(project.targetChapterCount())).append('\n')
                .append("\n【创作方向】\n")
                .append("一句话故事：").append(promptValue(bible == null ? null : bible.oneSentencePremise())).append('\n')
                .append("核心主题：").append(promptValue(bible == null ? null : bible.coreTheme())).append('\n')
                .append("主要矛盾：").append(promptValue(bible == null ? null : bible.mainConflict())).append('\n')
                .append("结局方向：").append(promptValue(bible == null ? null : bible.endingDirection())).append('\n')
                .append("\n【世界背景】\n")
                .append(promptValue(bible == null ? null : bible.worldBackground())).append('\n')
                .append(childKind == OutlineNodeKindEnum.VOLUME
                        ? "\n【全书大纲】\n" : "\n【当前卷】\n")
                .append("标题：").append(promptValue(parent.title())).append('\n')
                .append("摘要：").append(promptValue(parent.summary())).append('\n');
        if (childKind == OutlineNodeKindEnum.VOLUME) {
            appendVolumePlanningContext(prompt, parent.nodeCode(), outlines);
        } else {
            appendNextChapterPlanningContext(prompt, parent, outlines, targetChapter);
        }
        prompt.append("\n【核心人物摘要】\n");
        appendCoreCharacterSummaries(prompt, characters);

        prompt.append("\n【本次规划目标】\n")
                .append(childKind == OutlineNodeKindEnum.VOLUME
                        ? "全书的下一个卷"
                        : "第" + promptValue(targetChapter) + "章")
                .append('\n');
        return prompt.append("\n【用户补充要求】\n")
                .append(requirement)
                .toString();
    }

    private String buildVolumeOutlinePrompt(
            String projectCode,
            NovelProjectVO project,
            StoryBibleVO bible,
            OutlineNodeVO book,
            OutlineNodeVO volume,
            List<StoryCharacterVO> characters,
            List<OutlineNodeVO> outlines,
            String requirement
    ) {
        StringBuilder prompt = new StringBuilder()
                .append("【项目】\n")
                .append("标题：").append(promptValue(project.title())).append('\n')
                .append("题材：").append(promptValue(project.genre())).append('\n')
                .append("预计章节数：").append(promptValue(project.targetChapterCount())).append('\n')
                .append("\n【创作方向】\n")
                .append("一句话故事：").append(promptValue(bible == null ? null : bible.oneSentencePremise())).append('\n')
                .append("核心主题：").append(promptValue(bible == null ? null : bible.coreTheme())).append('\n')
                .append("主要矛盾：").append(promptValue(bible == null ? null : bible.mainConflict())).append('\n')
                .append("结局方向：").append(promptValue(bible == null ? null : bible.endingDirection())).append('\n')
                .append("\n【世界背景】\n")
                .append(promptValue(bible == null ? null : bible.worldBackground())).append('\n')
                .append("\n【全书大纲】\n")
                .append("标题：").append(promptValue(book.title())).append('\n')
                .append("摘要：").append(promptValue(book.summary())).append('\n')
                .append("\n【当前卷】\n")
                .append("结构位置：").append(volumeLabel(volume.sequenceNo())).append('\n')
                .append("章节范围：第").append(promptValue(volume.startChapter()))
                .append('～').append(promptValue(volume.endChapter())).append("章\n")
                .append("当前卷尚未完成正式卷纲，请补全这一卷的剧情标题和卷级内容。\n")
                .append("\n【前置卷正式卷纲】\n");
        appendPreviousVolumeOutlineAndFacts(
                prompt, projectCode, book, volume, outlines
        );
        prompt.append("\n【核心人物摘要】\n");
        appendCoreCharacterSummaries(prompt, characters);
        return prompt.append("\n【用户补充要求】\n")
                .append(requirement)
                .toString();
    }

    private void appendPreviousVolumeOutlineAndFacts(
            StringBuilder prompt,
            String projectCode,
            OutlineNodeVO book,
            OutlineNodeVO volume,
            List<OutlineNodeVO> outlines
    ) {
        List<OutlineNodeVO> source = outlines == null ? List.of() : outlines;
        OutlineNodeVO previousVolume = source.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.VOLUME
                        && Objects.equals(book.nodeCode(), node.parentNodeCode())
                        && node.sequenceNo() != null
                        && volume.sequenceNo() != null
                        && node.sequenceNo() < volume.sequenceNo())
                .max(Comparator.comparing(OutlineNodeVO::sequenceNo))
                .orElse(null);
        if (previousVolume == null) {
            prompt.append("（暂无前置卷）\n");
            prompt.append("\n【前置卷已规划章节】\n（暂无前置卷章节）\n")
                    .append("\n【已发生剧情事实】\n（暂无可用的前置剧情事实）\n");
            return;
        }

        prompt.append("标题：").append(promptValue(previousVolume.title())).append('\n')
                .append("摘要：").append(promptValue(previousVolume.summary())).append('\n');
        prompt.append("\n【前置卷已规划章节】\n");
        List<OutlineNodeVO> arcs = source.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.ARC
                        && Objects.equals(previousVolume.nodeCode(), node.parentNodeCode())
                        && node.startChapter() != null)
                .sorted(Comparator.comparing(
                        OutlineNodeVO::startChapter,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
        if (arcs.isEmpty()) {
            prompt.append("（暂无前置卷章节大纲）\n");
        } else {
            for (OutlineNodeVO arc : arcs) {
                prompt.append("第").append(promptValue(arc.startChapter())).append("章 · ")
                        .append(promptValue(arc.title())).append('\n')
                        .append("摘要：").append(promptValue(arc.summary())).append('\n');
            }
        }

        String previousChapterSummary = planningRepository.findPreviousChapterSummary(
                projectCode, volume.startChapter()
        );
        List<ChapterMemoryBriefVO> memories = planningRepository
                .findRecentChapterMemories(
                        projectCode,
                        volume.startChapter(),
                        ChapterPlanContextVO.MAX_RECENT_MEMORIES
                )
                .stream()
                .filter(Objects::nonNull)
                .map(this::toMemoryBrief)
                .toList();
        prompt.append("\n【已发生剧情事实】\n")
                .append("上一章实际摘要：")
                .append(promptValue(previousChapterSummary)).append('\n');
        appendRecentMemories(prompt, memories);
    }

    private void appendVolumePlanningContext(
            StringBuilder prompt,
            String bookNodeCode,
            List<OutlineNodeVO> outlines
    ) {
        List<OutlineNodeVO> source = outlines == null ? List.of() : outlines;
        List<OutlineNodeVO> volumes = source.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.VOLUME
                        && Objects.equals(bookNodeCode, node.parentNodeCode()))
                .sorted(Comparator.comparing(
                        OutlineNodeVO::sequenceNo,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        prompt.append("\n【已规划卷】\n");
        if (volumes.isEmpty()) {
            prompt.append("（暂无已规划卷）\n")
                    .append("\n【上一卷最终推进】\n（暂无上一卷）\n");
            return;
        }
        for (int index = 0; index < volumes.size(); index++) {
            OutlineNodeVO volume = volumes.get(index);
            prompt.append(volumeLabel(index + 1)).append(" · ")
                    .append(promptValue(volume.title())).append('\n')
                    .append("摘要：").append(promptValue(volume.summary())).append('\n');
        }

        OutlineNodeVO previousVolume = volumes.get(volumes.size() - 1);
        Integer latestChapter = lastArcChapter(source, previousVolume);
        prompt.append("\n【上一卷最终推进】\n")
                .append("卷：").append(volumeLabel(volumes.size())).append(" · ")
                .append(promptValue(previousVolume.title())).append('\n')
                .append("摘要：").append(promptValue(previousVolume.summary())).append('\n')
                .append("已规划到第").append(promptValue(latestChapter)).append("章\n");
    }

    private String volumeLabel(int volumeNumber) {
        return "卷" + switch (volumeNumber) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> Integer.toString(volumeNumber);
        };
    }

    private String normalizeVolumeTitle(String title, Integer sequenceNo) {
        String normalized = title.trim();
        if (sequenceNo == null) {
            return normalized;
        }
        String structuralLabel = volumeLabel(sequenceNo);
        String chineseSequence = structuralLabel.substring(1);
        if (normalized.matches("卷[一二三四五六七八九十百千万两〇零\\d]+")
                || normalized.matches("第[一二三四五六七八九十百千万两〇零\\d]+卷")) {
            return structuralLabel;
        }
        return normalized;
    }

    private void appendNextChapterPlanningContext(
            StringBuilder prompt,
            OutlineNodeVO volume,
            List<OutlineNodeVO> outlines,
            Integer targetChapter
    ) {
        List<OutlineNodeVO> source = outlines == null ? List.of() : outlines;
        List<OutlineNodeVO> chapterOutlines = source.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.ARC
                        && Objects.equals(volume.nodeCode(), node.parentNodeCode())
                        && node.startChapter() != null)
                .sorted(Comparator.comparing(
                        OutlineNodeVO::startChapter,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        OutlineNodeVO previousChapter = chapterOutlines.stream()
                .filter(node -> targetChapter == null || node.startChapter() < targetChapter)
                .max(Comparator.comparing(OutlineNodeVO::startChapter))
                .orElse(null);
        prompt.append("\n【上一章大纲】\n");
        if (previousChapter == null) {
            prompt.append("（暂无上一章大纲）\n");
        } else {
            prompt.append("第").append(promptValue(previousChapter.startChapter())).append("章 · ")
                    .append(promptValue(previousChapter.title())).append('\n')
                    .append("摘要：").append(promptValue(previousChapter.summary())).append('\n');
        }

        prompt.append("\n【当前卷已有章节大纲】\n");
        if (chapterOutlines.isEmpty()) {
            prompt.append("（暂无已规划章节）\n");
        } else {
            for (OutlineNodeVO chapter : chapterOutlines) {
                prompt.append("第").append(promptValue(chapter.startChapter())).append("章 · ")
                        .append(promptValue(chapter.title())).append('\n')
                        .append("摘要：").append(promptValue(chapter.summary())).append('\n');
            }
        }

        prompt.append("\n【本次目标章节】\n第")
                .append(promptValue(targetChapter)).append("章\n");
    }

    private String buildArcRegenerationPrompt(
            NovelProjectVO project,
            StoryBibleVO bible,
            OutlineNodeVO arc,
            OutlineNodeVO volume,
            OutlineNodeVO book,
            List<StoryCharacterVO> characters,
            String requirement
    ) {
        StringBuilder prompt = new StringBuilder()
                .append("【项目】\n")
                .append("标题：").append(promptValue(project.title())).append('\n')
                .append("题材：").append(promptValue(project.genre())).append('\n')
                .append("预计章节数：").append(promptValue(project.targetChapterCount())).append('\n')
                .append("\n【创作方向】\n")
                .append("一句话故事：").append(promptValue(bible == null ? null : bible.oneSentencePremise())).append('\n')
                .append("核心主题：").append(promptValue(bible == null ? null : bible.coreTheme())).append('\n')
                .append("主要矛盾：").append(promptValue(bible == null ? null : bible.mainConflict())).append('\n')
                .append("结局方向：").append(promptValue(bible == null ? null : bible.endingDirection())).append('\n')
                .append("\n【世界背景】\n")
                .append(promptValue(bible == null ? null : bible.worldBackground())).append('\n')
                .append("\n【全书上层方向】\n")
                .append("标题：").append(promptValue(book == null ? null : book.title())).append('\n')
                .append("摘要：").append(promptValue(book == null ? null : book.summary())).append('\n')
                .append("\n【当前卷】\n")
                .append("标题：").append(promptValue(volume.title())).append('\n')
                .append("摘要：").append(promptValue(volume.summary())).append('\n')
                .append("章节范围：").append(promptValue(volume.startChapter()))
                .append('～').append(promptValue(volume.endChapter())).append('\n')
                .append("\n【当前章】\n")
                .append("章节号：").append(promptValue(arc.startChapter())).append('\n')
                .append("当前标题：").append(promptValue(arc.title())).append('\n')
                .append("当前概要：").append(promptValue(arc.summary())).append('\n')
                .append("\n【核心人物摘要】\n");
        appendCoreCharacterSummaries(prompt, characters);
        return prompt.append("\n【用户补充要求】\n")
                .append(requirement)
                .toString();
    }

    private void appendCoreCharacterSummaries(
            StringBuilder prompt,
            List<StoryCharacterVO> characters
    ) {
        List<StoryCharacterVO> selected = selectCoreCharacters(characters);
        if (selected.isEmpty()) {
            prompt.append("（暂无）\n");
            return;
        }
        for (StoryCharacterVO character : selected) {
            prompt.append("- name：").append(promptValue(character.name()))
                    .append("；角色：").append(characterRoleLabel(character.roleType()))
                    .append("；personality：").append(promptValue(character.personality()))
                    .append('\n');
        }
    }

    private String characterRoleLabel(String roleType) {
        if (roleType == null) {
            return "其他角色";
        }
        return switch (roleType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "MALE_LEAD" -> "男主角";
            case "FEMALE_LEAD" -> "女主角";
            case "ALLY" -> "盟友";
            case "RIVAL" -> "对手";
            case "ANTAGONIST" -> "反派";
            case "SUPPORTING" -> "配角";
            default -> "其他角色";
        };
    }

    private List<StoryCharacterVO> selectCoreCharacters(List<StoryCharacterVO> characters) {
        List<StoryCharacterVO> selected = new ArrayList<>(CORE_CHARACTER_PROMPT_LIMIT);
        if (characters == null) {
            return selected;
        }
        for (int pass = 0; pass < 2 && selected.size() < CORE_CHARACTER_PROMPT_LIMIT; pass++) {
            for (StoryCharacterVO character : characters) {
                if (character == null || isBlank(character.name())
                        || (pass == 0 && !isCoreCharacter(character))
                        || (pass == 1 && isCoreCharacter(character))) {
                    continue;
                }
                selected.add(character);
                if (selected.size() >= CORE_CHARACTER_PROMPT_LIMIT) {
                    return selected;
                }
            }
        }
        return selected;
    }

    private boolean isCoreCharacter(StoryCharacterVO character) {
        String roleType = promptValue(character.roleType()).toLowerCase(java.util.Locale.ROOT);
        return roleType.contains("protagonist")
                || roleType.contains("main")
                || roleType.contains("lead")
                || roleType.contains("主角")
                || roleType.contains("核心");
    }

    private String promptValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRootOutline(
            String projectCode,
            String draftId,
            String summary
    ) {
        requireText(projectCode, "projectCode");
        PlanningDraftVO draft = requireDraft(projectCode, draftId, "ROOT_OUTLINE");
        NovelProjectVO project = requireProject(projectCode);
        OutlineNodeVO existingRoot = planningRepository.findRootOutline(projectCode);
        if (existingRoot != null && existingRoot.nodeKind() == OutlineNodeKindEnum.BOOK) {
            throw illegalParameter("项目已存在 BOOK 节点");
        }
        if (!(draft.payload() instanceof OutlineNodeVO original)
                || original.nodeKind() != OutlineNodeKindEnum.BOOK
                || original.parentNodeCode() != null) {
            throw illegalParameter("根大纲草稿内容无效");
        }
        requireText(summary, "summary");
        OutlineNodeVO confirmed = new OutlineNodeVO(
                original.nodeCode(), null, OutlineNodeKindEnum.BOOK, 1,
                project.title(), summary,
                original.startChapter(), original.endChapter(), "READY"
        );
        planningRepository.saveOutline(projectCode, confirmed);
        ensureDefaultVolume(projectCode, confirmed);
        draftRepository.remove(projectCode, draftId);
    }

    private void ensureDefaultVolume(
            String projectCode,
            OutlineNodeVO book
    ) {
        List<OutlineNodeVO> outlines = planningRepository.listOutlines(projectCode);
        boolean hasVolume = outlines.stream()
                .filter(Objects::nonNull)
                .anyMatch(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME
                        && Objects.equals(book.nodeCode(), node.parentNodeCode()));
        if (hasVolume) {
            return;
        }

        requireChapterRange(book.startChapter(), book.endChapter(), book.nodeCode());
        OutlineNodeVO defaultVolume = new OutlineNodeVO(
                code("VOL", nextNodeCodeSequence(projectCode, OutlineNodeKindEnum.VOLUME)),
                book.nodeCode(),
                OutlineNodeKindEnum.VOLUME,
                nextSiblingSequenceNo(outlines, book.nodeCode()),
                "卷一",
                "",
                book.startChapter(),
                book.endChapter(),
                "UNPLANNED"
        );
        planningRepository.saveOutline(projectCode, defaultVolume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutlineNodeVO createNextVolume(
            String projectCode,
            String bookNodeCode
    ) {
        requireText(projectCode, "projectCode");
        requireText(bookNodeCode, "bookNodeCode");
        requireProject(projectCode);
        OutlineNodeVO book = planningRepository.findOutline(projectCode, bookNodeCode);
        if (book == null || book.nodeKind() != OutlineNodeKindEnum.BOOK) {
            throw illegalParameter("只能为 BOOK 创建下一卷");
        }
        requireChapterRange(book.startChapter(), book.endChapter(), bookNodeCode);

        OutlineNodeVO currentVolume = findLatestVolume(projectCode, bookNodeCode);
        if (currentVolume == null) {
            throw illegalParameter("当前没有活动卷，不能生成下一卷");
        }
        requireVolumePlanningComplete(currentVolume, OutlineNodeKindEnum.ARC);
        OutlineRangeAllocator.ChapterRange range = nextVolumeRange(projectCode, book);
        List<OutlineNodeVO> outlines = planningRepository.listOutlines(projectCode);
        int nextSequence = nextSiblingSequenceNo(outlines, bookNodeCode);
        OutlineNodeVO nextVolume = new OutlineNodeVO(
                code("VOL", nextSequence),
                bookNodeCode,
                OutlineNodeKindEnum.VOLUME,
                nextSequence,
                volumeLabel(nextSequence),
                "",
                range.startChapter(),
                range.endChapter(),
                "UNPLANNED"
        );
        return planningRepository.finalizeCurrentVolumeAndCreateNext(
                projectCode, currentVolume.nodeCode(), nextVolume
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmNextOutline(
            String projectCode,
            String parentNodeCode,
            String draftId,
            String title,
            String summary
    ) {
        requireText(projectCode, "projectCode");
        requireText(parentNodeCode, "parentNodeCode");
        requireText(title, "title");
        requireText(summary, "summary");
        PlanningDraftVO draft = requireDraft(projectCode, draftId, "NEXT_OUTLINE");
        requireProject(projectCode);

        OutlineNodeVO parent = planningRepository.findOutline(
                projectCode, parentNodeCode
        );
        if (parent == null) {
            throw illegalParameter("父节点不存在，nodeCode=" + parentNodeCode);
        }
        OutlineNodeKindEnum childKind = expectedChildKind(parent.nodeKind());
        if (childKind == null) {
            throw illegalParameter("ARC 不能作为 parent");
        }
        if (childKind == OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("请先创建下一卷结构");
        }
        requireVolumePlanningComplete(parent, childKind);

        if (!(draft.payload() instanceof OutlineNodeVO generated)) {
            throw illegalParameter("下一步大纲草稿内容无效");
        }
        OutlineNodeVO expected = normalizeNextOutline(
                projectCode, parent, childKind,
                generated.title(), generated.summary()
        );
        validateNextOutlineDraft(generated, expected, parentNodeCode, childKind);
        OutlineNodeVO confirmed = new OutlineNodeVO(
                generated.nodeCode(), generated.parentNodeCode(), generated.nodeKind(),
                generated.sequenceNo(), title.trim(), summary.trim(),
                generated.startChapter(), generated.endChapter(), "READY"
        );
        planningRepository.saveOutline(projectCode, confirmed);
        draftRepository.remove(projectCode, draftId);
    }

    @Override
    public PlanningDraftVO generateVolumeOutline(
            String projectCode,
            String volumeNodeCode,
            String requirement
    ) {
        requireText(projectCode, "projectCode");
        requireText(volumeNodeCode, "volumeNodeCode");
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        NovelProjectVO project = requireProject(projectCode);
        VolumeOutlineContext context = requireUnplannedVolumeContext(
                projectCode, volumeNodeCode
        );
        StoryBibleVO bible = planningRepository.findBible(projectCode);
        List<StoryCharacterVO> characters = planningRepository.findCharacters(projectCode);
        List<OutlineNodeVO> outlines = planningRepository.listOutlines(projectCode);
        ChildOutlineDraftVO generated = modelPort.call(
                PlanningPrompts.VOLUME_OUTLINE_SYSTEM,
                buildVolumeOutlinePrompt(
                        projectCode, project, bible, context.book(), context.volume(), characters,
                        outlines, normalizedRequirement
                ),
                ChildOutlineDraftVO.class,
                "VOLUME_OUTLINE",
                1
        );
        StructuredModelOutputValidator.validate(generated);
        requireText(generated.title(), "volumeOutline.title");
        requireText(generated.summary(), "volumeOutline.summary");

        OutlineNodeVO payload = new OutlineNodeVO(
                context.volume().nodeCode(),
                context.volume().parentNodeCode(),
                OutlineNodeKindEnum.VOLUME,
                context.volume().sequenceNo(),
                normalizeVolumeTitle(generated.title(), context.volume().sequenceNo()),
                generated.summary().trim(),
                context.volume().startChapter(),
                context.volume().endChapter(),
                context.volume().status()
        );
        return draftRepository.save(projectCode, "VOLUME_OUTLINE", payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmVolumeOutline(
            String projectCode,
            String volumeNodeCode,
            String draftId,
            String title,
            String summary
    ) {
        requireText(projectCode, "projectCode");
        requireText(volumeNodeCode, "volumeNodeCode");
        requireText(title, "title");
        requireText(summary, "summary");

        PlanningDraftVO draft = requireDraft(projectCode, draftId, "VOLUME_OUTLINE");
        if (!(draft.payload() instanceof OutlineNodeVO generated)
                || generated.nodeKind() != OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("卷纲草稿内容无效");
        }
        VolumeOutlineContext context = requireUnplannedVolumeContext(
                projectCode, volumeNodeCode
        );
        OutlineNodeVO current = context.volume();
        if (!Objects.equals(volumeNodeCode, generated.nodeCode())
                || !Objects.equals(current.nodeCode(), generated.nodeCode())
                || !Objects.equals(current.parentNodeCode(), generated.parentNodeCode())
                || current.nodeKind() != generated.nodeKind()
                || !Objects.equals(current.sequenceNo(), generated.sequenceNo())
                || !Objects.equals(current.startChapter(), generated.startChapter())
                || !Objects.equals(current.endChapter(), generated.endChapter())) {
            throw illegalParameter("大纲结构已变化，请重新生成卷纲");
        }

        updateOutlineNode(projectCode, new OutlineNodeVO(
                current.nodeCode(),
                current.parentNodeCode(),
                current.nodeKind(),
                current.sequenceNo(),
                normalizeVolumeTitle(title, current.sequenceNo()),
                summary.trim(),
                current.startChapter(),
                current.endChapter(),
                "PLANNED"
        ));
        draftRepository.remove(projectCode, draftId);
    }

    @Override
    public PlanningDraftVO generateArcRegeneration(
            String projectCode,
            String arcNodeCode,
            String requirement
    ) {
        requireText(projectCode, "projectCode");
        requireText(arcNodeCode, "arcNodeCode");
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        NovelProjectVO project = requireProject(projectCode);
        ArcRegenerationContext context = requireArcRegenerationContext(
                projectCode, arcNodeCode
        );
        StoryBibleVO bible = planningRepository.findBible(projectCode);
        List<StoryCharacterVO> characters = planningRepository.findCharacters(projectCode);
        ChildOutlineDraftVO generated = modelPort.call(
                PlanningPrompts.ARC_REGENERATION_SYSTEM,
                buildArcRegenerationPrompt(
                        project, bible, context.arc(),
                        context.volume(), context.book(), characters, normalizedRequirement
                ),
                ChildOutlineDraftVO.class,
                "ARC_REGENERATION",
                1
        );
        StructuredModelOutputValidator.validate(generated);
        requireText(generated.title(), "arc.title");
        requireText(generated.summary(), "arc.summary");

        OutlineNodeVO payload = new OutlineNodeVO(
                context.arc().nodeCode(),
                context.arc().parentNodeCode(),
                context.arc().nodeKind(),
                context.arc().sequenceNo(),
                generated.title().trim(),
                generated.summary().trim(),
                context.arc().startChapter(),
                context.arc().endChapter(),
                context.arc().status()
        );
        return draftRepository.save(projectCode, "ARC_REGENERATION", payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmArcRegeneration(
            String projectCode,
            String arcNodeCode,
            String draftId,
            String title,
            String summary
    ) {
        requireText(projectCode, "projectCode");
        requireText(arcNodeCode, "arcNodeCode");
        requireText(title, "title");
        requireText(summary, "summary");

        PlanningDraftVO draft = requireDraft(projectCode, draftId, "ARC_REGENERATION");
        if (!(draft.payload() instanceof OutlineNodeVO generated)
                || generated.nodeKind() != OutlineNodeKindEnum.ARC) {
            throw illegalParameter("ARC 重生成草稿内容无效");
        }
        ArcRegenerationContext context = requireArcRegenerationContext(
                projectCode, arcNodeCode
        );
        OutlineNodeVO current = context.arc();
        if (!Objects.equals(arcNodeCode, generated.nodeCode())
                || !Objects.equals(current.nodeCode(), generated.nodeCode())
                || !Objects.equals(current.parentNodeCode(), generated.parentNodeCode())
                || current.nodeKind() != generated.nodeKind()
                || !Objects.equals(current.sequenceNo(), generated.sequenceNo())
                || !Objects.equals(current.startChapter(), generated.startChapter())
                || !Objects.equals(current.endChapter(), generated.endChapter())) {
            throw illegalParameter("大纲结构已变化，请重新生成 ARC");
        }

        updateOutlineNode(projectCode, new OutlineNodeVO(
                current.nodeCode(),
                current.parentNodeCode(),
                current.nodeKind(),
                current.sequenceNo(),
                title.trim(),
                summary.trim(),
                current.startChapter(),
                current.endChapter(),
                current.status()
        ));
        draftRepository.remove(projectCode, draftId);
    }

    @Override
    public PlanningDraftVO generateNextOutline(
            String projectCode,
            String parentNodeCode,
            String requirement
    ) {
        requireText(projectCode, "projectCode");
        requireText(parentNodeCode, "parentNodeCode");
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        NovelProjectVO project = requireProject(projectCode);
        OutlineNodeVO parent = planningRepository.findOutline(
                projectCode, parentNodeCode
        );
        if (parent == null) {
            throw illegalParameter("父节点不存在，nodeCode=" + parentNodeCode);
        }
        OutlineNodeKindEnum childKind = expectedChildKind(parent.nodeKind());
        if (childKind == null) {
            throw illegalParameter("ARC 不能作为 parent");
        }
        if (childKind == OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("请先创建下一卷结构");
        }
        requireVolumePlanningComplete(parent, childKind);
        requireChapterRange(parent.startChapter(), parent.endChapter(), parentNodeCode);
        Integer targetChapter = findNextChapterNumberForActiveVolume(projectCode, parent);
        StoryBibleVO bible = planningRepository.findBible(projectCode);
        List<StoryCharacterVO> characters = planningRepository.findCharacters(projectCode);
        List<OutlineNodeVO> outlines = planningRepository.listOutlines(projectCode);
        ChildOutlineDraftVO generated = modelPort.call(
                PlanningPrompts.NEXT_CHAPTER_OUTLINE_SYSTEM,
                buildChildOutlinePrompt(
                        project, bible, parent, characters, outlines, childKind,
                        targetChapter,
                        normalizedRequirement
                ),
                ChildOutlineDraftVO.class,
                "NEXT_CHAPTER_OUTLINE",
                1
        );
        StructuredModelOutputValidator.validate(generated);
        requireText(generated.title(), "nextOutline.title");
        requireText(generated.summary(), "nextOutline.summary");
        OutlineNodeVO nextOutline = normalizeNextOutline(
                projectCode, parent, childKind,
                generated.title(), generated.summary()
        );
        return draftRepository.save(projectCode, "NEXT_OUTLINE", nextOutline);
    }

    @Override
    public PlanningDraftVO generateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            String requirement
    ) {
        return generateChapterPlan(
                projectCode, chapterNumber, requirement, MemoryMode.defaultMode());
    }

    @Override
    public PlanningDraftVO generateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            String requirement,
            MemoryMode memoryMode
    ) {
        requireText(projectCode, "projectCode");
        MemoryMode resolvedMemoryMode = memoryMode == null
                ? MemoryMode.defaultMode() : memoryMode;
        String generationId = UUID.randomUUID().toString();
        if (chapterNumber == null || chapterNumber <= 0) {
            throw illegalParameter("chapterNumber 必须大于 0");
        }
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        NovelProjectVO project = requireProject(projectCode);
        if (project.targetChapterCount() != null
                && chapterNumber > project.targetChapterCount()) {
            throw illegalParameter("chapterNumber 超出项目章节范围");
        }

        List<OutlineNodeVO> outlines = planningRepository.listOutlines(projectCode);
        OutlineContext outlineContext = resolveChapterArcContext(
                outlines, chapterNumber
        );
        requireChapterRange(
                outlineContext.target().startChapter(),
                outlineContext.target().endChapter(),
                outlineContext.target().nodeCode()
        );
        requireText(outlineContext.target().title(), "arc.title");

        StoryBibleVO bible = planningRepository.findBible(projectCode);
        List<String> hardRules = parseHardRules(bible == null ? null : bible.hardRulesJson());
        MemoryContextPack memoryContextPack = buildPlanMemoryContext(
                projectCode,
                chapterNumber,
                outlineContext.target(),
                hardRules,
                resolvedMemoryMode,
                generationId
        );
        String previousChapterSummary = resolvedMemoryMode == MemoryMode.V1
                ? previousChapterSummary(memoryContextPack, chapterNumber)
                : planningRepository.findPreviousChapterSummary(projectCode, chapterNumber);
        List<ChapterMemoryBriefVO> recentMemories = resolvedMemoryMode == MemoryMode.V1
                ? List.of()
                : planningRepository.findRecentChapterMemories(
                                projectCode,
                                chapterNumber,
                                ChapterPlanContextVO.MAX_RECENT_MEMORIES
                        ).stream()
                        .filter(Objects::nonNull)
                        .map(this::toMemoryBrief)
                        .toList();
        List<CharacterBriefVO> relevantCharacters = relevantCharacterSelector.select(
                previousChapterSummary,
                outlineContext.target().title(),
                outlineContext.target().summary(),
                planningRepository.findCharacters(projectCode)
        );

        ChapterPlanContextVO context = new ChapterPlanContextVO(
                chapterNumber,
                outlineContext.bookBrief(),
                outlineContext.parent() == null
                        ? null : toOutlineBrief(outlineContext.parent()),
                toOutlineBrief(outlineContext.target()),
                toStoryBibleBrief(bible),
                new ChapterPositionVO(
                        chapterNumber - outlineContext.target().startChapter() + 1,
                        outlineContext.target().endChapter()
                                - outlineContext.target().startChapter() + 1
                ),
                previousChapterSummary,
                recentMemories,
                relevantCharacters,
                hardRules,
                normalizedRequirement
        );

        ChapterPlanDraftVO generated = modelPort.call(
                PlanningPrompts.CHAPTER_PLAN_SYSTEM,
                buildChapterPlanPrompt(context, memoryContextPack),
                ChapterPlanDraftVO.class,
                "CHAPTER_PLAN",
                1
        );
        StructuredModelOutputValidator.validate(generated);
        requireText(generated.summary(), "chapterPlan.summary");

        ChapterOutlineVO payload = new ChapterOutlineVO(
                chapterNumber,
                outlineContext.target().nodeCode(),
                outlineContext.target().title().trim(),
                generated.summary().trim(),
                "PLANNED"
        );
        return draftRepository.save(projectCode, "CHAPTER_PLAN", payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmChapterPlan(
            String projectCode,
            Integer chapterNumber,
            String draftId,
            String title,
            String summary
    ) {
        requireText(projectCode, "projectCode");
        if (chapterNumber == null || chapterNumber <= 0) {
            throw illegalParameter("chapterNumber 必须大于 0");
        }
        requireText(title, "title");
        requireText(summary, "summary");

        PlanningDraftVO storedDraft = requireDraft(projectCode, draftId, "CHAPTER_PLAN");
        if (!(storedDraft.payload() instanceof ChapterOutlineVO generated)) {
            throw illegalParameter("章节计划草稿内容无效");
        }
        if (!Objects.equals(chapterNumber, generated.chapterNumber())) {
            throw illegalParameter("草稿 chapterNumber 与请求不一致");
        }
        requireText(generated.outlineNodeCode(), "outlineNodeCode");

        OutlineNodeVO outline = planningRepository.findOutline(
                projectCode, generated.outlineNodeCode()
        );
        if (outline == null) {
            throw illegalParameter("草稿大纲节点不存在，nodeCode="
                    + generated.outlineNodeCode());
        }
        requireChapterRange(outline.startChapter(), outline.endChapter(), outline.nodeCode());
        if (chapterNumber < outline.startChapter() || chapterNumber > outline.endChapter()) {
            throw illegalParameter("chapterNumber 不在草稿大纲范围内");
        }

        List<OutlineNodeVO> currentOutlines = planningRepository.listOutlines(projectCode);
        OutlineContext currentContext = resolveChapterArcContext(currentOutlines, chapterNumber);
        if (!Objects.equals(
                currentContext.target().nodeCode(),
                generated.outlineNodeCode()
        )) {
            throw illegalParameter("大纲结构已变化，请重新生成章节计划");
        }
        requireText(currentContext.target().title(), "arc.title");
        if (!Objects.equals(title.trim(), currentContext.target().title().trim())) {
            throw illegalParameter("ChapterPlan.title 与当前 ARC.title 不一致");
        }

        rejectCompletedChapterPlanOverwrite(projectCode, chapterNumber);
        planningRepository.upsertChapterPlan(projectCode, new ChapterOutlineVO(
                chapterNumber,
                generated.outlineNodeCode(),
                currentContext.target().title().trim(),
                summary.trim(),
                "READY"
        ));
        draftRepository.remove(projectCode, draftId);
    }

    private void rejectCompletedChapterPlanOverwrite(
            String projectCode,
            Integer chapterNumber
    ) {
        boolean hasCompletedPlan = planningRepository.findChapterPlanOutlines(projectCode)
                .stream()
                .anyMatch(plan -> plan != null
                        && Objects.equals(chapterNumber, plan.chapterNumber())
                        && "COMPLETED".equalsIgnoreCase(plan.status()));
        if (hasCompletedPlan) {
            throw illegalParameter("已有正文的 ChapterPlan 不能被 AI 覆盖");
        }
    }

    private String buildChapterPlanPrompt(ChapterPlanContextVO context) {
        return buildChapterPlanPrompt(context, null);
    }

    private String buildChapterPlanPrompt(
            ChapterPlanContextVO context,
            MemoryContextPack memoryContextPack
    ) {
        StringBuilder prompt = new StringBuilder(8192);
        prompt.append("【当前章节】\n")
                .append("第 ").append(context.chapterNumber()).append(" 章\n\n");

        appendStoryBible(prompt, context.storyBible());

        prompt.append("【当前剧情段】\n")
                .append("标题：").append(promptValue(context.targetOutline().title())).append('\n')
                .append("摘要：").append(promptValue(context.targetOutline().summary())).append("\n\n");

        prompt.append("【当前章节标题】\n")
                .append("当前章节标题已经确定：")
                .append(promptValue(context.targetOutline().title())).append('\n')
                .append("不得修改该标题；本次只生成本章写作执行计划。\n\n");

        prompt.append("【上层方向】\n")
                .append("上级大纲：").append(renderOutline(context.parentOutline())).append('\n')
                .append("全书方向：").append(renderBookOutline(context.bookOutline())).append("\n\n");

        prompt.append("【当前位置】\n")
                .append("当前剧情段第 ").append(context.position().position())
                .append('/').append(context.position().total()).append(" 章\n\n");

        appendPreviousChapterSummary(prompt, context, memoryContextPack);

        prompt.append("【相关人物】\n");
        if (context.relevantCharacters().isEmpty()) {
            prompt.append("无\n\n");
        } else {
            for (CharacterBriefVO character : context.relevantCharacters()) {
                prompt.append("人物：").append(promptValue(character.name())).append('\n')
                        .append("角色：").append(promptValue(character.role())).append('\n')
                        .append("当前目标：").append(promptValue(character.currentGoal())).append('\n')
                        .append("当前位置：").append(promptValue(character.currentLocation())).append("\n\n");
            }
        }

        if (memoryContextPack == null || memoryContextPack.items().isEmpty()) {
            appendRecentMemories(prompt, context.recentMemories());
            appendListSection(prompt, "【硬规则】", context.hardRules());
        } else {
            ChapterPromptFormatter.appendMemoryContextPack(
                    prompt, "【PLAN 记忆上下文】", memoryContextPack);
        }
        prompt.append("【补充要求】\n");
        if (context.requirement() == null || context.requirement().isBlank()) {
            prompt.append("无（按当前 Story Bible、当前大纲和前文自然生成下一章计划）\n");
        } else {
            prompt.append("本章额外创作偏好：")
                    .append(promptValue(context.requirement())).append('\n');
        }
        return prompt.toString();
    }

    private void appendPreviousChapterSummary(
            StringBuilder prompt,
            ChapterPlanContextVO context,
            MemoryContextPack memoryContextPack
    ) {
        if (hasPreviousChapterSummaryInMemory(context, memoryContextPack)) {
            return;
        }
        prompt.append("【上一章】\n")
                .append(promptValue(context.previousChapterSummary())).append("\n\n");
    }

    private boolean hasPreviousChapterSummaryInMemory(
            ChapterPlanContextVO context,
            MemoryContextPack memoryContextPack
    ) {
        if (context.chapterNumber() == null) {
            return false;
        }
        int previousChapterNumber = context.chapterNumber() - 1;
        boolean recentMemoryHit = context.recentMemories().stream()
                .anyMatch(memory -> memory != null
                        && Objects.equals(memory.chapterNumber(), previousChapterNumber)
                        && !isBlank(memory.shortSummary()));
        if (recentMemoryHit) {
            return true;
        }
        return memoryContextPack != null
                && memoryContextPack.items().stream()
                .anyMatch(item -> item.sourceChapter() == previousChapterNumber);
    }

    private MemoryContextPack buildPlanMemoryContext(
            String projectCode,
            int chapterNumber,
            OutlineNodeVO currentArc,
            List<String> hardRules,
            MemoryMode memoryMode,
            String generationId
    ) {
        MemoryMode resolvedMode = memoryMode == null
                ? MemoryMode.defaultMode() : memoryMode;
        List<MemoryContextItem> canonicalCandidates = resolvedMode == MemoryMode.LEGACY
                ? List.of()
                : safeItems(planningRepository.findCanonicalMemoryContextItems(
                        projectCode, chapterNumber));
        List<MemoryContextItem> legacyCandidates = resolvedMode == MemoryMode.V1
                ? null
                : safeItems(planningRepository.findMemoryContextItems(
                        projectCode, chapterNumber));
        // AUTO/LEGACY 没有长期候选时保留原 PLAN 的 recent/story_summary 展示路径；
        // V1 即使 Canonical 暂无数据，也要继续执行以便按规则惰性触发 bridge fallback。
        if (resolvedMode != MemoryMode.V1
                && canonicalCandidates.isEmpty()
                && legacyCandidates.isEmpty()) {
            return null;
        }
        List<MemoryContextItem> canonicalContextCandidates = new ArrayList<>();
        canonicalContextCandidates.addAll(canonicalCandidates);

        if (currentArc != null && currentArc.title() != null && currentArc.summary() != null) {
            MemoryContextItem arcCandidate = new MemoryContextItem(
                    "arc-progression-" + currentArc.nodeCode(),
                    MemoryContextCategory.CONSOLIDATED,
                    "当前剧情段推进：" + currentArc.title().trim()
                            + "；" + currentArc.summary().trim(),
                    chapterNumber,
                    false,
                    false,
                    false,
                    0);
            canonicalContextCandidates.add(arcCandidate);
        }
        List<String> safeRules = hardRules == null ? List.of() : hardRules;
        for (int index = 0; index < safeRules.size(); index++) {
            String rule = safeRules.get(index);
            if (rule == null || rule.isBlank()) {
                continue;
            }
            MemoryContextItem ruleCandidate = new MemoryContextItem(
                    "plan-world-rule-" + index,
                    MemoryContextCategory.RULES,
                    rule.trim(),
                    chapterNumber,
                    false,
                    false,
                    false,
                    0);
            canonicalContextCandidates.add(ruleCandidate);
        }

        Set<String> openLoopIds = canonicalContextCandidates.stream()
                .filter(item -> item.category() == MemoryContextCategory.OPEN_LOOPS)
                .map(MemoryContextItem::itemId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> ruleIds = canonicalContextCandidates.stream()
                .filter(item -> item.category() == MemoryContextCategory.RULES)
                .map(MemoryContextItem::itemId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        MemoryQuerySpec querySpec = new MemoryQuerySpec(
                MemoryProfile.PLAN,
                Set.of(),
                Set.of(),
                openLoopIds,
                ruleIds,
                Set.of());
        return legacyFallbackRouter.provide(
                resolvedMode,
                querySpec,
                MemoryBudgetSpec.defaultP0(),
                canonicalContextCandidates,
                resolvedMode == MemoryMode.V1
                        ? () -> planningRepository.findLegacyMemoryContextItems(
                                projectCode, chapterNumber)
                        : () -> resolvedMode == MemoryMode.LEGACY
                                ? combineCandidates(legacyCandidates, canonicalContextCandidates)
                                : legacyCandidates,
                chapterNumber,
                false,
                false,
                resolvedMode != MemoryMode.LEGACY && canonicalCandidates.isEmpty(),
                false,
                projectCode,
                generationId);
    }

    private List<MemoryContextItem> combineCandidates(
            List<MemoryContextItem> first,
            List<MemoryContextItem> second
    ) {
        List<MemoryContextItem> combined = new ArrayList<>();
        if (first != null) {
            combined.addAll(first);
        }
        if (second != null) {
            combined.addAll(second);
        }
        return List.copyOf(combined);
    }

    private List<MemoryContextItem> safeItems(List<MemoryContextItem> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    private String previousChapterSummary(
            MemoryContextPack memoryContextPack,
            int chapterNumber
    ) {
        if (memoryContextPack == null) {
            return null;
        }
        return memoryContextPack.items().stream()
                .filter(item -> item.sourceChapter() == chapterNumber - 1)
                .filter(item -> item.category() == MemoryContextCategory.CONSOLIDATED
                        || item.category() == MemoryContextCategory.EPISODES)
                .map(MemoryContextItem::content)
                .findFirst()
                .orElse(null);
    }

    private void appendStoryBible(
            StringBuilder prompt,
            StoryBibleBriefVO storyBible
    ) {
        prompt.append("【Story Bible】\n");
        if (storyBible == null) {
            prompt.append("无\n\n");
            return;
        }
        prompt.append("一句话故事：").append(promptValue(storyBible.oneSentencePremise())).append('\n')
                .append("核心主题：").append(promptValue(storyBible.coreTheme())).append('\n')
                .append("主要矛盾：").append(promptValue(storyBible.mainConflict())).append('\n')
                .append("结局方向：").append(promptValue(storyBible.endingDirection())).append('\n')
                .append("世界背景：").append(promptValue(storyBible.worldBackground())).append('\n')
                .append("特殊体系：").append(promptValue(storyBible.powerSystem())).append('\n')
                .append("写作风格：").append(promptValue(storyBible.styleGuide())).append("\n\n");
    }

    private StoryBibleBriefVO toStoryBibleBrief(StoryBibleVO bible) {
        if (bible == null) {
            return null;
        }
        return new StoryBibleBriefVO(
                bible.oneSentencePremise(),
                bible.coreTheme(),
                bible.mainConflict(),
                bible.endingDirection(),
                bible.worldBackground(),
                renderPowerSystem(bible.powerSystemJson()),
                bible.styleGuide()
        );
    }

    private String renderPowerSystem(String powerSystemJson) {
        if (powerSystemJson == null || powerSystemJson.isBlank()) {
            return "无";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(powerSystemJson);
            String rendered = naturalText(root).trim();
            return rendered.isBlank() ? "无" : rendered;
        } catch (RuntimeException ignored) {
            return legacyLines(powerSystemJson);
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

    private void appendListSection(
            StringBuilder prompt,
            String title,
            List<String> values
    ) {
        prompt.append(title).append('\n');
        if (values == null || values.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }
        for (String value : values) {
            prompt.append("- ").append(promptValue(value)).append('\n');
        }
        prompt.append('\n');
    }

    private void appendRecentMemories(
            StringBuilder prompt,
            List<ChapterMemoryBriefVO> memories
    ) {
        prompt.append("【近期剧情记忆】\n");
        if (memories == null || memories.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }
        for (ChapterMemoryBriefVO memory : memories) {
            prompt.append("第 ").append(promptValue(
                            memory.chapterNumber() == null
                                    ? null : memory.chapterNumber().toString()))
                    .append(" 章\n")
                    .append("摘要：").append(promptValue(memory.shortSummary())).append('\n');
            appendMemoryList(prompt, "关键事件", memory.keyEvents());
            appendMemoryList(prompt, "未解决问题", memory.unresolved());
            prompt.append("结尾钩子：")
                    .append(promptValue(memory.endingHook())).append("\n\n");
        }
    }

    private void appendMemoryList(
            StringBuilder prompt,
            String label,
            List<String> values
    ) {
        prompt.append(label).append("：");
        if (values == null || values.isEmpty()) {
            prompt.append("无\n");
            return;
        }
        prompt.append('\n');
        for (String value : values) {
            prompt.append("- ").append(promptValue(value)).append('\n');
        }
    }

    private ChapterMemoryBriefVO toMemoryBrief(ChapterMemoryVO memory) {
        return new ChapterMemoryBriefVO(
                memory.getChapterNumber(),
                memory.getShortSummary(),
                memory.getKeyEvents(),
                memory.getUnresolved(),
                memory.getEndingHook()
        );
    }

    private String renderOutline(OutlineBriefVO outline) {
        if (outline == null) {
            return "无";
        }
        return "标题：" + promptValue(outline.title())
                + "；摘要：" + promptValue(outline.summary());
    }

    private String renderBookOutline(OutlineBriefVO outline) {
        return outline == null ? "无" : promptValue(outline.summary());
    }

    private String promptValue(String value) {
        return value == null || value.isBlank() ? "无" : value.trim();
    }

    private List<String> parseHardRules(String hardRulesJson) {
        if (hardRulesJson == null || hardRulesJson.isBlank()) {
            return List.of();
        }
        List<String> rules = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(hardRulesJson);
            if (root != null && root.isArray()) {
                root.forEach(value -> {
                    if (rules.size() < ChapterPlanContextVO.MAX_HARD_RULES) {
                        String rule = value == null || value.isNull()
                                ? null
                                : value.isTextual() ? value.asText() : value.toString();
                        if (rule != null && !rule.isBlank()) {
                            rules.add(rule.trim());
                        }
                    }
                });
                return List.copyOf(rules);
            }
        } catch (RuntimeException ignored) {
            // 兼容历史非 JSON 硬规则配置，作为单条规则继续使用。
        }
        return List.of(hardRulesJson.trim());
    }

    private OutlineContext resolveOutlineContext(
            List<OutlineNodeVO> outlines,
            int chapterNumber
    ) {
        if (outlines == null || outlines.isEmpty()) {
            throw illegalParameter("找不到覆盖当前章节的大纲节点");
        }
        List<OutlineNodeVO> validOutlines = outlines.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.startChapter() != null && node.endChapter() != null)
                .filter(node -> node.startChapter() <= chapterNumber
                        && chapterNumber <= node.endChapter())
                .toList();
        OutlineNodeVO target = null;
        for (OutlineNodeVO candidate : validOutlines) {
            if (target == null || isMoreSpecific(candidate, target, outlines)) {
                target = candidate;
            }
        }
        if (target == null) {
            throw illegalParameter("找不到覆盖第 " + chapterNumber + " 章的大纲节点");
        }

        java.util.Map<String, OutlineNodeVO> byCode = outlines.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.nodeCode() != null)
                .collect(java.util.stream.Collectors.toMap(
                        OutlineNodeVO::nodeCode,
                        value -> value,
                        (left, right) -> left
                ));
        OutlineNodeVO parent = target.parentNodeCode() == null
                ? null : byCode.get(target.parentNodeCode());
        OutlineNodeVO book = null;
        Set<String> visited = new HashSet<>();
        OutlineNodeVO cursor = target;
        while (cursor != null && cursor.parentNodeCode() != null) {
            if (!visited.add(cursor.nodeCode())) {
                throw illegalParameter("大纲父链存在循环，nodeCode=" + cursor.nodeCode());
            }
            cursor = byCode.get(cursor.parentNodeCode());
            if (cursor == null) {
                throw illegalParameter("大纲父节点不存在，nodeCode=" + target.nodeCode());
            }
            if (cursor.nodeKind() == OutlineNodeKindEnum.BOOK) {
                book = cursor;
                break;
            }
        }
        OutlineBriefVO bookBrief = book == null
                ? null
                : new OutlineBriefVO(null, book.summary(), null, null);
        return new OutlineContext(target, parent, bookBrief);
    }

    private OutlineContext resolveChapterArcContext(
            List<OutlineNodeVO> outlines,
            int chapterNumber
    ) {
        boolean hasCoveringArc = outlines != null && outlines.stream()
                .filter(Objects::nonNull)
                .anyMatch(node -> node.nodeKind() == OutlineNodeKindEnum.ARC
                        && node.startChapter() != null
                        && node.endChapter() != null
                        && node.startChapter() <= chapterNumber
                        && chapterNumber <= node.endChapter());
        if (!hasCoveringArc) {
            throw illegalParameter("当前章节尚未创建章纲");
        }
        OutlineContext context = resolveOutlineContext(outlines, chapterNumber);
        if (context.target().nodeKind() != OutlineNodeKindEnum.ARC
                || context.parent() == null
                || context.parent().nodeKind() != OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("当前章节尚未创建章纲");
        }
        return context;
    }

    private boolean isMoreSpecific(
            OutlineNodeVO candidate,
            OutlineNodeVO current,
            List<OutlineNodeVO> outlines
    ) {
        int candidateRange = candidate.endChapter() - candidate.startChapter();
        int currentRange = current.endChapter() - current.startChapter();
        if (candidateRange != currentRange) {
            return candidateRange < currentRange;
        }
        return outlineDepth(candidate, outlines) > outlineDepth(current, outlines);
    }

    private int outlineDepth(OutlineNodeVO node, List<OutlineNodeVO> outlines) {
        java.util.Map<String, OutlineNodeVO> byCode = outlines.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.nodeCode() != null)
                .collect(java.util.stream.Collectors.toMap(
                        OutlineNodeVO::nodeCode,
                        value -> value,
                        (left, right) -> left
                ));
        int depth = 0;
        Set<String> visited = new HashSet<>();
        OutlineNodeVO cursor = node;
        while (cursor != null && cursor.parentNodeCode() != null
                && visited.add(cursor.nodeCode())) {
            depth++;
            cursor = byCode.get(cursor.parentNodeCode());
        }
        return depth;
    }

    private OutlineBriefVO toOutlineBrief(OutlineNodeVO outline) {
        return outline == null ? null : new OutlineBriefVO(
                outline.title(), outline.summary(),
                outline.startChapter(), outline.endChapter()
        );
    }

    private record OutlineContext(
            OutlineNodeVO target,
            OutlineNodeVO parent,
            OutlineBriefVO bookBrief
    ) {
    }

    private record ArcRegenerationContext(
            OutlineNodeVO arc,
            OutlineNodeVO volume,
            OutlineNodeVO book
    ) {
    }

    private record VolumeOutlineContext(
            OutlineNodeVO volume,
            OutlineNodeVO book
    ) {
    }

    private VolumeOutlineContext requireUnplannedVolumeContext(
            String projectCode,
            String volumeNodeCode
    ) {
        OutlineNodeVO volume = planningRepository.findOutline(projectCode, volumeNodeCode);
        if (volume == null || volume.nodeKind() != OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("当前卷不存在，nodeCode=" + volumeNodeCode);
        }
        if (!isVolumeUnplanned(volume)) {
            throw illegalParameter("当前卷已完成规划");
        }
        requireChapterRange(volume.startChapter(), volume.endChapter(), volume.nodeCode());

        OutlineNodeVO book = planningRepository.findOutline(
                projectCode, volume.parentNodeCode()
        );
        if (book == null || book.nodeKind() != OutlineNodeKindEnum.BOOK) {
            throw illegalParameter("VOLUME 的父节点必须是 BOOK");
        }
        return new VolumeOutlineContext(volume, book);
    }

    private ArcRegenerationContext requireArcRegenerationContext(
            String projectCode,
            String arcNodeCode
    ) {
        OutlineNodeVO arc = planningRepository.findOutline(projectCode, arcNodeCode);
        if (arc == null) {
            throw illegalParameter("ARC 节点不存在，nodeCode=" + arcNodeCode);
        }
        if (arc.nodeKind() != OutlineNodeKindEnum.ARC) {
            throw illegalParameter("只能重新生成 ARC 节点");
        }
        requireChapterRange(arc.startChapter(), arc.endChapter(), arc.nodeCode());
        if (!Objects.equals(arc.startChapter(), arc.endChapter())) {
            throw illegalParameter("ARC 必须是单章，startChapter 必须等于 endChapter");
        }

        OutlineNodeVO volume = planningRepository.findOutline(
                projectCode, arc.parentNodeCode()
        );
        if (volume == null || volume.nodeKind() != OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("ARC 的父节点必须是 VOLUME");
        }
        requireChapterRange(volume.startChapter(), volume.endChapter(), volume.nodeCode());
        if (arc.startChapter() < volume.startChapter()
                || arc.endChapter() > volume.endChapter()) {
            throw illegalParameter("ARC 章节号必须位于父卷范围内");
        }

        OutlineNodeVO book = planningRepository.findOutline(
                projectCode, volume.parentNodeCode()
        );
        if (book == null || book.nodeKind() != OutlineNodeKindEnum.BOOK) {
            throw illegalParameter("VOLUME 的父节点必须是 BOOK");
        }
        return new ArcRegenerationContext(arc, volume, book);
    }

    private OutlineNodeVO normalizeRootOutline(
            RootOutlineDraftVO value,
            String projectTitle,
            int targetChapterCount
    ) {
        StructuredModelOutputValidator.validate(value);
        requireText(projectTitle, "project.title");
        requireText(value.summary(), "rootOutline.summary");
        return new OutlineNodeVO(
                code("BOOK", 1), null, OutlineNodeKindEnum.BOOK, 1,
                projectTitle, value.summary(), 1, targetChapterCount, "PLANNED"
        );
    }

    private OutlineNodeVO bindRootOutlineTitle(
            String projectTitle,
            OutlineNodeVO outline
    ) {
        if (outline == null || outline.nodeKind() != OutlineNodeKindEnum.BOOK) {
            return outline;
        }
        return new OutlineNodeVO(
                outline.nodeCode(), outline.parentNodeCode(), outline.nodeKind(),
                outline.sequenceNo(), projectTitle, outline.summary(),
                outline.startChapter(), outline.endChapter(), outline.status()
        );
    }

    private OutlineNodeVO normalizeNextOutline(
            String projectCode,
            OutlineNodeVO parent,
            OutlineNodeKindEnum childKind,
            String title,
            String summary
    ) {
        requireText(title, "nextOutline.title");
        requireText(summary, "nextOutline.summary");
        int sequenceNo = planningRepository.nextSequence(projectCode, parent.nodeCode());
        int nodeCodeSequence = nextNodeCodeSequence(projectCode, childKind);
        Integer startChapter;
        Integer endChapter;
        if (childKind == OutlineNodeKindEnum.VOLUME) {
            OutlineRangeAllocator.ChapterRange range = nextVolumeRange(projectCode, parent);
            startChapter = range.startChapter();
            endChapter = range.endChapter();
        } else if (childKind == OutlineNodeKindEnum.ARC) {
            startChapter = findNextChapterNumberForActiveVolume(projectCode, parent);
            endChapter = startChapter;
        } else {
            throw illegalParameter("不支持的下一步大纲类型");
        }
        return new OutlineNodeVO(
                code(nodeCodePrefix(childKind), nodeCodeSequence),
                parent.nodeCode(), childKind, sequenceNo,
                title.trim(), summary.trim(), startChapter, endChapter, "PLANNED"
        );
    }

    private OutlineRangeAllocator.ChapterRange nextVolumeRange(
            String projectCode,
            OutlineNodeVO book
    ) {
        List<OutlineNodeVO> volumes = planningRepository.listOutlines(projectCode).stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.VOLUME
                        && Objects.equals(book.nodeCode(), node.parentNodeCode()))
                .toList();
        if (volumes.isEmpty()) {
            throw illegalParameter("当前没有活动卷，不能开始下一卷");
        }

        OutlineNodeVO currentVolume = volumes.stream()
                .max(Comparator.comparing(
                        OutlineNodeVO::sequenceNo,
                        Comparator.nullsFirst(Integer::compareTo)
                ))
                .orElseThrow(() -> illegalParameter("当前活动卷不存在"));
        Integer actualEndChapter = lastArcChapter(projectCode, currentVolume);
        if (actualEndChapter == null) {
            throw illegalParameter("当前卷尚未规划章节，不能开始下一卷");
        }
        int nextStartChapter = actualEndChapter + 1;
        if (book.endChapter() == null || nextStartChapter > book.endChapter()) {
            throw illegalParameter("当前卷没有可用的下一卷范围");
        }
        return new OutlineRangeAllocator.ChapterRange(
                nextStartChapter, book.endChapter()
        );
    }

    private OutlineNodeVO findLatestVolume(
            String projectCode,
            String bookNodeCode
    ) {
        return planningRepository.listOutlines(projectCode).stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.VOLUME
                        && Objects.equals(bookNodeCode, node.parentNodeCode()))
                .max(Comparator.comparing(
                        OutlineNodeVO::sequenceNo,
                        Comparator.nullsFirst(Integer::compareTo)
                ))
                .orElse(null);
    }

    private Integer lastArcChapter(String projectCode, OutlineNodeVO volume) {
        return lastArcChapter(planningRepository.listOutlines(projectCode), volume);
    }

    private Integer lastArcChapter(
            List<OutlineNodeVO> outlines,
            OutlineNodeVO volume
    ) {
        if (outlines == null || volume == null) {
            return null;
        }
        return outlines.stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.ARC
                        && Objects.equals(volume.nodeCode(), node.parentNodeCode())
                        && node.startChapter() != null)
                .map(OutlineNodeVO::startChapter)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private int findNextChapterNumber(
            String projectCode,
            OutlineNodeVO volume
    ) {
        if (volume == null || volume.nodeKind() != OutlineNodeKindEnum.VOLUME) {
            throw illegalParameter("ARC 章节分配必须使用 VOLUME 父节点");
        }
        requireChapterRange(volume.startChapter(), volume.endChapter(), volume.nodeCode());
        int latestChapter = planningRepository.listOutlines(projectCode).stream()
                .filter(node -> node != null
                        && node.nodeKind() == OutlineNodeKindEnum.ARC
                        && Objects.equals(volume.nodeCode(), node.parentNodeCode())
                        && node.startChapter() != null)
                .mapToInt(node -> Math.min(volume.endChapter(), node.startChapter()))
                .max()
                .orElse(volume.startChapter() - 1);
        int nextChapter = latestChapter + 1;
        NovelProjectVO project = planningRepository.findProject(projectCode);
        if (project != null
                && project.targetChapterCount() != null
                && nextChapter > project.targetChapterCount()) {
            throw illegalParameter(
                    "预计章节数已达到 " + project.targetChapterCount()
                            + " 章，请先调整预计章节数后继续创作。"
            );
        }
        if (nextChapter > volume.endChapter()) {
            throw illegalParameter("父节点没有可用的下一章");
        }
        return nextChapter;
    }

    private int findNextChapterNumberForActiveVolume(
            String projectCode,
            OutlineNodeVO volume
    ) {
        requireActiveVolumeForChapterOutline(projectCode, volume);
        return findNextChapterNumber(projectCode, volume);
    }

    private void requireActiveVolumeForChapterOutline(
            String projectCode,
            OutlineNodeVO volume
    ) {
        OutlineNodeVO activeVolume = volume == null
                ? null
                : findLatestVolume(projectCode, volume.parentNodeCode());
        if (activeVolume == null
                || !Objects.equals(activeVolume.nodeCode(), volume.nodeCode())) {
            throw illegalParameter("只能在当前活动卷下生成章节大纲");
        }
    }

    private void requireActiveVolumeForNextVolume(
            String projectCode,
            String bookNodeCode
    ) {
        if (findLatestVolume(projectCode, bookNodeCode) == null) {
            throw illegalParameter("当前没有活动卷，不能生成下一卷");
        }
    }

    private void validateNextOutlineDraft(
            OutlineNodeVO generated,
            OutlineNodeVO expected,
            String parentNodeCode,
            OutlineNodeKindEnum expectedKind
    ) {
        if (generated == null
                || !Objects.equals(generated.nodeCode(), expected.nodeCode())
                || !Objects.equals(generated.parentNodeCode(), parentNodeCode)
                || generated.nodeKind() != expectedKind
                || !Objects.equals(generated.sequenceNo(), expected.sequenceNo())
                || !Objects.equals(generated.startChapter(), expected.startChapter())
                || !Objects.equals(generated.endChapter(), expected.endChapter())) {
            throw illegalParameter("大纲结构已变化，请重新生成下一步大纲");
        }
        requireText(generated.title(), "nextOutline.title");
        requireText(generated.summary(), "nextOutline.summary");
        requireChapterRange(generated.startChapter(), generated.endChapter(), generated.nodeCode());
        if (expectedKind == OutlineNodeKindEnum.ARC
                && !Objects.equals(generated.startChapter(), generated.endChapter())) {
            throw illegalParameter("ARC 必须是单章");
        }
    }

    private String code(String prefix, int sequenceNo) {
        return "%s_%03d".formatted(prefix, sequenceNo);
    }

    private OutlineNodeVO normalizeCreatedOutline(
            String projectCode,
            OutlineNodeVO outline,
            List<OutlineNodeVO> existing
    ) {
        if (outline == null
                || outline.nodeCode() != null
                || outline.nodeKind() != null
                || outline.sequenceNo() != null
                || outline.status() != null) {
            return outline;
        }
        requireText(outline.parentNodeCode(), "parentNodeCode");
        OutlineNodeVO parent = planningRepository.findOutline(
                projectCode, outline.parentNodeCode()
        );
        if (parent == null) {
            throw illegalParameter("父节点不存在，nodeCode=" + outline.parentNodeCode());
        }
        OutlineNodeKindEnum childKind = expectedChildKind(parent.nodeKind());
        if (childKind == null) {
            throw illegalParameter("ARC 不能作为 parent");
        }
        Integer startChapter = outline.startChapter();
        Integer endChapter = outline.endChapter();
        if (childKind == OutlineNodeKindEnum.ARC) {
            requireActiveVolumeForChapterOutline(projectCode, parent);
            startChapter = findNextChapterNumber(projectCode, parent);
            endChapter = startChapter;
        } else if (childKind == OutlineNodeKindEnum.VOLUME
                && startChapter == null && endChapter == null) {
            OutlineRangeAllocator.ChapterRange range = findNextAvailableVolumeRange(
                    parent, existing
            );
            startChapter = range.startChapter();
            endChapter = range.endChapter();
        }
        return new OutlineNodeVO(
                code(nodeCodePrefix(childKind), nextNodeCodeSequence(projectCode, childKind)),
                parent.nodeCode(),
                childKind,
                nextSiblingSequenceNo(existing, parent.nodeCode()),
                outline.title(),
                outline.summary(),
                startChapter,
                endChapter,
                "PLANNED"
        );
    }

    private OutlineRangeAllocator.ChapterRange findNextAvailableVolumeRange(
            OutlineNodeVO parent,
            List<OutlineNodeVO> existing
    ) {
        requireChapterRange(parent.startChapter(), parent.endChapter(), parent.nodeCode());
        int nextStart = parent.startChapter();
        List<OutlineNodeVO> siblings = existing.stream()
                .filter(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME)
                .filter(node -> Objects.equals(node.parentNodeCode(), parent.nodeCode()))
                .filter(node -> node.startChapter() != null && node.endChapter() != null)
                .sorted(Comparator.comparing(OutlineNodeVO::startChapter))
                .toList();

        for (OutlineNodeVO sibling : siblings) {
            if (sibling.endChapter() < nextStart) {
                continue;
            }
            if (sibling.startChapter() > parent.endChapter()) {
                break;
            }
            int siblingStart = Math.max(parent.startChapter(), sibling.startChapter());
            int siblingEnd = Math.min(parent.endChapter(), sibling.endChapter());
            if (siblingStart > siblingEnd) {
                continue;
            }
            if (nextStart < siblingStart) {
                return new OutlineRangeAllocator.ChapterRange(nextStart, siblingStart - 1);
            }
            if (siblingEnd == Integer.MAX_VALUE) {
                throw illegalParameter("父节点没有可用章节范围");
            }
            nextStart = siblingEnd + 1;
            if (nextStart > parent.endChapter()) {
                break;
            }
        }

        if (nextStart <= parent.endChapter()) {
            return new OutlineRangeAllocator.ChapterRange(nextStart, parent.endChapter());
        }
        throw illegalParameter("父节点没有可用章节范围");
    }

    private int nextSiblingSequenceNo(
            List<OutlineNodeVO> existing,
            String parentNodeCode
    ) {
        return existing.stream()
                .filter(node -> Objects.equals(node.parentNodeCode(), parentNodeCode))
                .map(OutlineNodeVO::sequenceNo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    private int nextNodeCodeSequence(
            String projectCode,
            OutlineNodeKindEnum childKind
    ) {
        String prefix = nodeCodePrefix(childKind);
        String pattern = prefix + "_\\d+";
        return planningRepository.listOutlines(projectCode).stream()
                .map(OutlineNodeVO::nodeCode)
                .filter(Objects::nonNull)
                .filter(value -> value.matches(pattern))
                .mapToInt(value -> Integer.parseInt(
                        value.substring(prefix.length() + 1)
                ))
                .max()
                .orElse(0) + 1;
    }

    private String nodeCodePrefix(OutlineNodeKindEnum nodeKind) {
        return switch (nodeKind) {
            case VOLUME -> "VOL";
            case ARC -> "ARC";
            case BOOK -> throw illegalParameter("节点类型不能是 BOOK");
        };
    }

    private void requireChapterRange(
            Integer startChapter,
            Integer endChapter,
            String parentNodeCode
    ) {
        if (startChapter == null || endChapter == null
                || startChapter <= 0 || endChapter < startChapter) {
            throw illegalParameter("父节点章节范围非法，nodeCode=" + parentNodeCode);
        }
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

    private NovelProjectVO requireProject(String projectCode) {
        NovelProjectVO project = planningRepository.findProject(projectCode);
        if (project == null) {
            throw illegalParameter("项目不存在，projectCode=" + projectCode);
        }
        return project;
    }

    private void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw illegalParameter(name + " 必须在 " + min + "～" + max + " 之间");
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw illegalParameter(name + " 不能为空");
        }
    }

    private AppException illegalParameter(String message) {
        if (message != null
                && (message.contains("nodeCode")
                || message.contains("parentNodeCode")
                || message.contains("projectCode")
                || message.contains("draftId")
                || message.contains("arcNodeCode")
                || message.contains("targetSequence")
                || message.contains("sequenceNo")
                || message.contains("nodeKind")
                || message.contains("outlineNodeCode")
                || message.contains("chapterNumber")
                || message.contains("status")
                || message.contains("targetChapterCount")
                || message.contains("wordsPerChapter")
                || message.contains("preferredCount")
                || message.contains("chapterPlan")
                || message.contains("ChapterPlan")
                || message.contains("startChapter")
                || message.contains("endChapter")
                || message.contains("outline")
                || message.contains("payload")
                || message.contains("parent")
                || message.contains("child")
                || message.contains("structured response")
                || message.contains("结构化响应")
                || message.contains("."))) {
            return AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
        }
        return AppException.user(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }

    private void validateCreateOutline(
            String projectCode,
            OutlineNodeVO outline,
            List<OutlineNodeVO> existing
    ) {
        validateOutlineFields(outline);
        if (outline.nodeKind() == OutlineNodeKindEnum.BOOK) {
            validateBookChapterRange(projectCode, outline);
            if (outline.parentNodeCode() != null) {
                throw illegalParameter("BOOK 不能作为 child");
            }
            if (existing.stream().anyMatch(node -> node.nodeKind() == OutlineNodeKindEnum.BOOK)) {
                throw illegalParameter("一个项目只能有一个 BOOK");
            }
            return;
        }
        validateOutlineHierarchy(projectCode, outline);
        if (outline.nodeKind() == OutlineNodeKindEnum.ARC) {
            requireVolumePlanningComplete(
                    planningRepository.findOutline(projectCode, outline.parentNodeCode()),
                    OutlineNodeKindEnum.ARC
            );
            requireActiveVolumeForChapterOutline(
                    projectCode,
                    planningRepository.findOutline(projectCode, outline.parentNodeCode())
            );
        }
    }

    private void validateOutlineHierarchy(
            String projectCode,
            OutlineNodeVO outline
    ) {
        if (outline.nodeKind() == OutlineNodeKindEnum.BOOK) {
            if (outline.parentNodeCode() != null) {
                throw illegalParameter("BOOK 不能作为 child");
            }
            validateBookChapterRange(projectCode, outline);
            return;
        }
        if (outline.parentNodeCode() == null) {
            throw illegalParameter("非 BOOK 节点必须有 parent");
        }
        OutlineNodeVO parent = planningRepository.findOutline(
                projectCode, outline.parentNodeCode()
        );
        if (parent == null) {
            throw illegalParameter("父节点不存在，nodeCode=" + outline.parentNodeCode());
        }
        requireAllowedChildKind(parent.nodeKind(), outline.nodeKind());
        validateChildChapterRange(projectCode, outline, parent);
    }

    private void validateChildChapterRange(
            String projectCode,
            OutlineNodeVO child,
            OutlineNodeVO parent
    ) {
        requireChapterRange(parent.startChapter(), parent.endChapter(), parent.nodeCode());
        if (child.startChapter() < parent.startChapter()
                || child.endChapter() > parent.endChapter()) {
            throw illegalParameter("子节点章节范围必须位于父节点范围内，nodeCode=" + child.nodeCode());
        }

        List<OutlineNodeVO> siblings = planningRepository.listOutlines(projectCode).stream()
                .filter(node -> !Objects.equals(node.nodeCode(), child.nodeCode()))
                .filter(node -> Objects.equals(node.parentNodeCode(), child.parentNodeCode()))
                .toList();
        for (OutlineNodeVO sibling : siblings) {
            if (sibling.startChapter() == null || sibling.endChapter() == null) {
                continue;
            }
            if (child.startChapter() <= sibling.endChapter()
                    && sibling.startChapter() <= child.endChapter()) {
                throw illegalParameter("子节点章节范围与已有同级大纲重叠，nodeCode=" + child.nodeCode());
            }
        }
    }

    private void validateBookChapterRange(
            String projectCode,
            OutlineNodeVO outline
    ) {
        NovelProjectVO project = requireProject(projectCode);
        Integer targetChapterCount = project.targetChapterCount();
        if (targetChapterCount == null || targetChapterCount <= 0
                || outline.startChapter() != 1
                || !Objects.equals(outline.endChapter(), targetChapterCount)) {
            throw illegalParameter("BOOK 必须覆盖全书范围：1～"
                    + targetChapterCount + "章");
        }
    }

    private OutlineNodeKindEnum expectedChildKind(
            OutlineNodeKindEnum parentKind
    ) {
        if (parentKind == null) {
            throw illegalParameter("父节点类型不能为空");
        }
        return switch (parentKind) {
            case BOOK -> OutlineNodeKindEnum.VOLUME;
            case VOLUME -> OutlineNodeKindEnum.ARC;
            case ARC -> null;
        };
    }

    private void requireVolumePlanningComplete(
            OutlineNodeVO parent,
            OutlineNodeKindEnum childKind
    ) {
        if (childKind != OutlineNodeKindEnum.ARC || parent == null
                || parent.nodeKind() != OutlineNodeKindEnum.VOLUME) {
            return;
        }
        if (isVolumeUnplanned(parent)) {
            throw illegalParameter("请先完成当前卷规划");
        }
    }

    private boolean isVolumeUnplanned(OutlineNodeVO volume) {
        return volume != null
                && (volume.summary() == null || volume.summary().isBlank()
                || "UNPLANNED".equalsIgnoreCase(volume.status()));
    }

    private OutlineNodeVO markVolumePlannedAfterContentSaved(
            OutlineNodeVO existing,
            OutlineNodeVO normalized
    ) {
        if (existing.nodeKind() != OutlineNodeKindEnum.VOLUME
                || !"UNPLANNED".equalsIgnoreCase(existing.status())
                || normalized.summary() == null
                || normalized.summary().isBlank()) {
            return normalized;
        }
        return new OutlineNodeVO(
                normalized.nodeCode(), normalized.parentNodeCode(), normalized.nodeKind(),
                normalized.sequenceNo(), normalized.title(), normalized.summary(),
                normalized.startChapter(), normalized.endChapter(), "PLANNED"
        );
    }

    private void requireAllowedChildKind(
            OutlineNodeKindEnum parentKind,
            OutlineNodeKindEnum childKind
    ) {
        if (childKind == null) {
            throw illegalParameter("大纲层级类型不能为空");
        }
        OutlineNodeKindEnum expectedKind = expectedChildKind(parentKind);
        if (expectedKind == null) {
            throw illegalParameter("ARC 不能作为 parent");
        }
        if (childKind != expectedKind) {
            throw illegalParameter(parentKind + " 只能创建 " + expectedKind + " 子节点");
        }
    }

    private void validateOutlineFields(OutlineNodeVO outline) {
        if (outline == null) {
            throw illegalParameter("outline 不能为空");
        }
        requireText(outline.nodeCode(), "nodeCode");
        if (outline.nodeKind() == null) {
            throw illegalParameter("nodeKind 不能为空");
        }
        if (outline.sequenceNo() == null || outline.sequenceNo() <= 0) {
            throw illegalParameter("sequenceNo 必须大于 0");
        }
        requireText(outline.title(), "title");
        requireText(outline.summary(), "summary");
        requireText(outline.status(), "status");
        requireChapterRange(outline.startChapter(), outline.endChapter(), outline.nodeCode());
        if (outline.nodeKind() == OutlineNodeKindEnum.ARC
                && !Objects.equals(outline.startChapter(), outline.endChapter())) {
            throw illegalParameter("ARC 必须是单章，startChapter 必须等于 endChapter");
        }
    }

    private OutlineNodeVO normalizeParentCode(OutlineNodeVO outline) {
        if (outline == null || outline.parentNodeCode() == null
                || !outline.parentNodeCode().isBlank()) {
            return outline;
        }
        return new OutlineNodeVO(
                outline.nodeCode(), null, outline.nodeKind(), outline.sequenceNo(),
                outline.title(), outline.summary(), outline.startChapter(),
                outline.endChapter(), outline.status()
        );
    }
}
