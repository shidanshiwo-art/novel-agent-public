package cn.ninth.novel.domain.project.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterUpdateResultVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NovelProjectServiceTest {

    @Test
    void shouldDelegateCharacterDeletionToRepository() {
        RecordingRepository repository = new RecordingRepository();
        INovelProjectService service = new NovelProjectService(repository);
        System.out.println("Service 删除人物：novel-001 / char-001");
        service.deleteCharacter("novel-001", "char-001");
        assertThat(repository.deletedProjectCode).isEqualTo("novel-001");
        assertThat(repository.deletedCharacterCode).isEqualTo("char-001");
        assertThat(repository.characterDeletionCalls).isEqualTo(1);
        System.out.println("仓储收到原始项目和人物编码，删除调用次数为 1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectBlankProjectCodeBeforeDeletingCharacter(String projectCode) {
        assertInvalidCharacterDeletion(projectCode, "char-001", "projectCode");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectBlankCharacterCodeBeforeDeletingCharacter(String characterCode) {
        assertInvalidCharacterDeletion("novel-001", characterCode, "characterCode");
    }

    @Test
    void shouldPropagateCharacterDeletionFailure() {
        RecordingRepository repository = new RecordingRepository();
        repository.characterDeletionFailure = AppException.user(
                ResponseCode.ILLEGAL_PARAMETER.getCode(), "人物不存在");
        INovelProjectService service = new NovelProjectService(repository);
        System.out.println("仓储返回人物不存在时，Service 应继续抛出异常");
        assertThatThrownBy(() -> service.deleteCharacter("novel-001", "missing"))
                .isSameAs(repository.characterDeletionFailure);
        assertThat(repository.characterDeletionCalls).isEqualTo(1);
        System.out.println("Service 原样传递人物不存在异常，未静默成功");
    }

    private void assertInvalidCharacterDeletion(String projectCode, String characterCode, String field) {
        RecordingRepository repository = new RecordingRepository();
        INovelProjectService service = new NovelProjectService(repository);
        System.out.println("校验删除参数：" + field + " 为空或纯空白");
        assertThatThrownBy(() -> service.deleteCharacter(projectCode, characterCode))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
                    assertThat(exception.getInternalDetail()).isEqualTo(
                            field + " 不能为空");
                    assertThat(exception.getInfo()).isNull();
                    System.out.println("参数拦截内部详情：" + exception.getInternalDetail());
                });
        assertThat(repository.characterDeletionCalls).isZero();
        System.out.println("未调用仓储删除");
    }

    @Test
    void shouldApplyProjectDefaultsBeforeCreation() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        NovelProjectVO result = service.createProject(new NovelProjectVO(
                "novel-001",
                "北境夜行",
                "玄幻",
                100,
                null,
                null,
                null
        ));

        assertThat(repository.savedProject.wordsPerChapter()).isEqualTo(2500);
        assertThat(repository.savedProject.currentChapterNumber()).isZero();
        assertThat(repository.savedProject.status()).isEqualTo("DRAFT");
        assertThat(result).isEqualTo(repository.savedProject);
    }

    @Test
    void shouldUpdateTargetChapterCount() {
        RecordingRepository repository = new RecordingRepository();
        repository.existingProject = new NovelProjectVO(
                "novel-001", "北境夜行", "玄幻", 100, 2500, 100, "WRITING"
        );
        NovelProjectService service = new NovelProjectService(repository);

        NovelProjectVO result = service.updateTargetChapterCount("novel-001", 120);

        System.out.printf("调整预计章节数：project=%s, target=%d%n",
                result.projectCode(), result.targetChapterCount());
        assertThat(repository.updatedTargetChapterCount).isEqualTo(120);
        assertThat(result.targetChapterCount()).isEqualTo(120);
    }

    @Test
    void shouldApplyCharacterDefaultsBeforeCreation() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.addCharacter(
                "novel-001",
                character("lin-yuan", "林渊", "MALE_LEAD", "MALE")
        );

        assertThat(result.gender()).isEqualTo("MALE");
        JsonNode state = new ObjectMapper().readTree(result.currentStateJson());
        System.out.printf(
                "人工新增初始化：code=%s, state=%s, life=%s, status=%s%n",
                result.characterCode(), result.currentStateJson(), result.lifeStatus(), result.status()
        );
        assertThat(state.isObject()).isTrue();
        assertThat(result.currentStateJson()).isEqualTo("{}");
        assertThat(result.lifeStatus()).isEqualTo("ALIVE");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldForceBackendCharacterDefaultsWhenCreatingCharacter() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.addCharacter(
                "novel-001",
                new StoryCharacterVO(
                        null, "林渊", "男主角", "男", null, null, null, null,
                        null, "自定义状态", "DEAD", "INACTIVE"
                )
        );

        System.out.printf(
                "backend character defaults: state=%s, life=%s, status=%s%n",
                result.currentStateJson(), result.lifeStatus(), result.status()
        );
        assertThat(new ObjectMapper().readTree(result.currentStateJson()).isObject()).isTrue();
        assertThat(result.currentStateJson()).isEqualTo("{}");
        assertThat(result.lifeStatus()).isEqualTo("ALIVE");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldGenerateCharacterCodeAndMapNaturalRoleBeforeCreation() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.addCharacter(
                "novel-001",
                character("frontend-generated-code", "林渊", "男主角", "男")
        );

        System.out.printf(
                "character create normalized: code=%s, role=%s, gender=%s%n",
                result.characterCode(), result.roleType(), result.gender()
        );
        assertThat(result.characterCode())
                .startsWith("char-")
                .isNotEqualTo("frontend-generated-code");
        assertThat(result.roleType()).isEqualTo("MALE_LEAD");
        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(repository.addedCharacter).isEqualTo(result);
    }

    @Test
    void shouldMapAllBusinessRoleLabelsToStorageValues() {
        String[][] roleMappings = {
                {"男主角", "男", "MALE_LEAD"},
                {"女主角", "女", "FEMALE_LEAD"},
                {"盟友", "其他", "ALLY"},
                {"对手", "其他", "RIVAL"},
                {"反派", "其他", "ANTAGONIST"},
                {"配角", "其他", "SUPPORTING"}
        };
        NovelProjectService service = new NovelProjectService(new RecordingRepository());

        for (String[] mapping : roleMappings) {
            StoryCharacterVO result = service.addCharacter(
                    "novel-001",
                    character(null, "人物", mapping[0], mapping[1])
            );
            System.out.printf("role mapping: %s -> %s%n", mapping[0], result.roleType());
            assertThat(result.roleType()).isEqualTo(mapping[2]);
        }
    }

    @Test
    void shouldUpdateCharacterUsingPathIdentityAndDefaults() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.updateCharacter(
                "novel-001",
                "lin-yuan",
                character("ignored-body-code", "林渊·修订", "MALE_LEAD", "MALE")
        );

        assertThat(repository.updatedCharacter.characterCode())
                .isEqualTo("lin-yuan");
        assertThat(result.name()).isEqualTo("林渊·修订");
        assertThat(result.lifeStatus()).isEqualTo("ALIVE");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldPreserveRuntimeStateWhenEditingCharacterProfile() {
        RecordingRepository repository = new RecordingRepository();
        repository.existingCharacters = List.of(new StoryCharacterVO(
                "lin-yuan", "林渊", "MALE_LEAD", "MALE", "二十七岁", null,
                "谨慎", "北境来客", null, "{\"位置\":\"雾港\"}", "MISSING", "ACTIVE"
        ));
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.updateCharacter(
                "novel-001",
                "lin-yuan",
                new StoryCharacterVO(
                        "ignored-body-code", "林渊·修订", "男主角", "男", "二十八岁",
                        "更显疲惫", "更谨慎", "修订后的经历", "备注", null, null, null
                )
        );

        System.out.printf(
                "profile edit preserved runtime state: state=%s, life=%s, status=%s%n",
                result.currentStateJson(), result.lifeStatus(), result.status()
        );
        assertThat(result.currentStateJson()).isEqualTo("{\"位置\":\"雾港\"}");
        assertThat(result.lifeStatus()).isEqualTo("MISSING");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldIgnoreRuntimeFieldsWhenUpdatingCharacterProfile() {
        RecordingRepository repository = new RecordingRepository();
        repository.existingCharacters = List.of(new StoryCharacterVO(
                "lin-yuan", "林渊", "MALE_LEAD", "MALE", "二十七岁", null,
                "谨慎", "北境来客", null, "{\"位置\":\"雾港\"}", "MISSING", "ACTIVE"
        ));
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.updateCharacter(
                "novel-001",
                "lin-yuan",
                new StoryCharacterVO(
                        "lin-yuan", "林渊·修订", "男主角", "男", "二十八岁",
                        "更显疲惫", "更谨慎", "修订后的经历", "备注",
                        "{\"位置\":\"敌城\"}", "DEAD", "INACTIVE"
                )
        );

        System.out.printf(
                "普通编辑动态字段边界：state=%s, life=%s, status=%s%n",
                result.currentStateJson(), result.lifeStatus(), result.status()
        );
        assertThat(repository.updatedCharacter.currentStateJson()).isEqualTo("{\"位置\":\"雾港\"}");
        assertThat(repository.updatedCharacter.lifeStatus()).isEqualTo("MISSING");
        assertThat(repository.updatedCharacter.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldMergeEditedProfileWithExistingRuntimeStateBeforeRepositoryUpdate() {
        RecordingRepository repository = new RecordingRepository();
        repository.existingCharacters = List.of(new StoryCharacterVO(
                "lin-yuan", "林渊", "MALE_LEAD", "MALE", "二十七岁",
                "旧外貌", "旧性格", "旧背景", "旧备注",
                "{\"位置\":\"雾港\"}", "MISSING", "ACTIVE"
        ));
        NovelProjectService service = new NovelProjectService(repository);

        StoryCharacterVO result = service.updateCharacter(
                "novel-001",
                "lin-yuan",
                new StoryCharacterVO(
                        "spoofed-body-code", "林渊·修订", "男主角", "男", "二十八岁",
                        "新外貌", "新性格", "新背景", "新备注",
                        "{\"位置\":\"敌城\"}", "DEAD", "INACTIVE"
                )
        );

        System.out.printf(
                "Repository 收到合并角色：code=%s, name=%s, personality=%s, state=%s, life=%s, status=%s%n",
                repository.updatedCharacter.characterCode(), repository.updatedCharacter.name(),
                repository.updatedCharacter.personality(), repository.updatedCharacter.currentStateJson(),
                repository.updatedCharacter.lifeStatus(), repository.updatedCharacter.status()
        );
        assertThat(repository.updatedCharacter).isEqualTo(result);
        assertThat(repository.updatedCharacter.characterCode()).isEqualTo("lin-yuan");
        assertThat(repository.updatedCharacter.name()).isEqualTo("林渊·修订");
        assertThat(repository.updatedCharacter.appearance()).isEqualTo("新外貌");
        assertThat(repository.updatedCharacter.personality()).isEqualTo("新性格");
        assertThat(repository.updatedCharacter.backgroundStory()).isEqualTo("新背景");
        assertThat(repository.updatedCharacter.note()).isEqualTo("新备注");
        assertThat(repository.updatedCharacter.currentStateJson()).isEqualTo("{\"位置\":\"雾港\"}");
        assertThat(repository.updatedCharacter.lifeStatus()).isEqualTo("MISSING");
        assertThat(repository.updatedCharacter.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldReturnWarningWhenStructuralCharacterChangeAffectsExistingOutline() {
        RecordingRepository repository = new RecordingRepository();
        repository.outlinesPresent = true;
        repository.existingCharacters = List.of(new StoryCharacterVO(
                "lin-yuan", "林渊", "MALE_LEAD", "MALE", "二十七岁",
                "旧外貌", "旧性格", "普通大学生", "旧备注",
                "{}", "ALIVE", "ACTIVE"
        ));
        NovelProjectService service = new NovelProjectService(repository);

        CharacterUpdateResultVO result = service.updateCharacterWithWarning(
                "novel-001",
                "lin-yuan",
                character("lin-yuan", "林渊", "MALE_LEAD", "MALE")
        );

        System.out.println("已有大纲且人物背景发生结构性变化时，返回 warning 但继续保存");
        assertThat(repository.updatedCharacter).isNotNull();
        assertThat(result.warning()).isEqualTo(
                "该人物已参与现有故事大纲，修改核心设定可能造成剧情冲突。建议修改后重新生成受影响的大纲。"
        );
        System.out.println("人物更新 warning：" + result.warning());
    }

    @Test
    void shouldNotWarnForLightweightCharacterChangeWithExistingOutline() {
        RecordingRepository repository = new RecordingRepository();
        repository.outlinesPresent = true;
        repository.existingCharacters = List.of(new StoryCharacterVO(
                "lin-yuan", "林渊", "MALE_LEAD", "MALE", "二十七岁",
                "旧外貌", "旧性格", "普通大学生", "旧备注",
                "{}", "ALIVE", "ACTIVE"
        ));
        NovelProjectService service = new NovelProjectService(repository);

        CharacterUpdateResultVO result = service.updateCharacterWithWarning(
                "novel-001",
                "lin-yuan",
                new StoryCharacterVO(
                        "lin-yuan", "林渊·新备注", "MALE_LEAD", "MALE", "二十七岁",
                        "新外貌", "旧性格", "普通大学生", "新备注",
                        null, null, null
                )
        );

        System.out.println("已有大纲但只修改姓名、外貌和备注时，不返回结构性 warning");
        assertThat(result.warning()).isNull();
    }

    @Test
    void shouldOverwriteChapterTitleAndContentAndCountNonWhitespaceCodePoints() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        GeneratedChapterVO result = service.overwriteChapterContent(
                "novel-001",
                1,
                "钟楼来信",
                "林 渊\n😀"
        );

        assertThat(repository.overwrittenTitle).isEqualTo("钟楼来信");
        assertThat(repository.overwrittenContent).isEqualTo("林 渊\n😀");
        assertThat(repository.overwrittenWordCount).isEqualTo(3);
        assertThat(result.wordCount()).isEqualTo(3);
    }

    @Test
    void shouldAutoResyncChapterMemoryAfterManualOverwrite() {
        RecordingRepository repository = new RecordingRepository();
        repository.overwrittenStatus = "DIRTY";
        repository.chapterAfterSync = new GeneratedChapterVO(
                1, "钟楼来信", "林渊的修改正文", 8, "FINALIZED"
        );
        RecordingChapterService chapterService = new RecordingChapterService();
        INovelProjectService service = new NovelProjectService(
                repository, null, null, chapterService
        );

        GeneratedChapterVO result = service.overwriteChapterContent(
                "novel-001", 1, "钟楼来信", "林渊的修改正文"
        );

        System.out.printf(
                "正文保存后自动更新章节记忆：calls=%d, finalStatus=%s%n",
                chapterService.resyncCalls, result.status()
        );
        assertThat(chapterService.resyncCalls).isEqualTo(1);
        assertThat(result.status()).isEqualTo("FINALIZED");
        assertThat(result.content()).isEqualTo("林渊的修改正文");
    }

    @Test
    void shouldKeepSavedContentDirtyWhenAutomaticResyncFails() {
        RecordingRepository repository = new RecordingRepository();
        repository.overwrittenStatus = "DIRTY";
        RecordingChapterService chapterService = new RecordingChapterService();
        chapterService.resyncFailure = new RuntimeException("compression unavailable");
        INovelProjectService service = new NovelProjectService(
                repository, null, null, chapterService
        );

        GeneratedChapterVO result = service.overwriteChapterContent(
                "novel-001", 1, "钟楼来信", "已保存但等待更新的正文"
        );

        System.out.printf(
                "章节记忆自动更新失败：calls=%d, savedStatus=%s, content=%s%n",
                chapterService.resyncCalls, result.status(), result.content()
        );
        assertThat(chapterService.resyncCalls).isEqualTo(1);
        assertThat(result.status()).isEqualTo("DIRTY");
        assertThat(result.content()).isEqualTo("已保存但等待更新的正文");
        assertThat(repository.overwrittenContent).isEqualTo("已保存但等待更新的正文");
    }

    @Test
    void shouldRejectInvalidProjectEditingArguments() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        assertIllegalParameter(() -> service.updateCharacter(
                "novel-001",
                " ",
                character("ignored", "林渊", "MALE_LEAD", "MALE")
        ));
        assertIllegalParameter(() -> service.overwriteChapterContent(
                "novel-001",
                1,
                " ",
                " \n "
        ));
    }

    @Test
    void shouldDelegateBibleWithoutChangingItsContent() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);
        StoryBibleVO bible = new StoryBibleVO(
                "少年追查父亲失踪真相",
                "真相与代价",
                "少年与议会争夺线索",
                "揭开议会秘密",
                "北境诸城",
                "{\"realms\":[\"启灵\"]}",
                "[\"能力必须付出代价\"]",
                "第三人称限知",
                null
        );

        StoryBibleVO result = service.saveBible("novel-001", bible);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.oneSentencePremise())
                .isEqualTo("少年追查父亲失踪真相");
    }

    @Test
    void shouldRejectMissingProjectAndChapterQueries() {
        RecordingRepository repository = new RecordingRepository();
        NovelProjectService service = new NovelProjectService(repository);

        assertIllegalParameter(() -> service.getProject("missing"));
        assertIllegalParameter(() -> service.getChapter("missing", 0));
        assertIllegalParameter(() -> service.getChapter("missing", 1));
    }

    private StoryCharacterVO character(
            String code,
            String name,
            String roleType,
            String gender
    ) {
        return new StoryCharacterVO(
                code,
                name,
                roleType,
                gender,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void assertIllegalParameter(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ResponseCode.ILLEGAL_PARAMETER.getCode()
                        ));
    }

    private static final class RecordingRepository
            implements INovelProjectRepository {
        private NovelProjectVO savedProject;
        private StoryCharacterVO addedCharacter;
        private StoryCharacterVO updatedCharacter;
        private List<StoryCharacterVO> existingCharacters = List.of();
        private String overwrittenTitle;
        private String overwrittenContent;
        private Integer overwrittenWordCount;
        private String overwrittenStatus = "FINALIZED";
        private GeneratedChapterVO chapterAfterOverwrite;
        private GeneratedChapterVO chapterAfterSync;
        private String deletedProjectCode;
        private String deletedCharacterCode;
        private int characterDeletionCalls;
        private AppException characterDeletionFailure;
        private NovelProjectVO existingProject;
        private Integer updatedTargetChapterCount;
        private boolean outlinesPresent;

        @Override
        public NovelProjectVO createProject(NovelProjectVO project) {
            savedProject = project;
            return project;
        }

        @Override
        public NovelProjectVO updateTargetChapterCount(
                String projectCode,
                Integer targetChapterCount
        ) {
            updatedTargetChapterCount = targetChapterCount;
            return new NovelProjectVO(
                    existingProject.projectCode(),
                    existingProject.title(),
                    existingProject.genre(),
                    targetChapterCount,
                    existingProject.wordsPerChapter(),
                    existingProject.currentChapterNumber(),
                    existingProject.status()
            );
        }

        @Override
        public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
            return bible;
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            return null;
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            return existingCharacters;
        }

        @Override
        public boolean hasOutlines(String projectCode) {
            return outlinesPresent;
        }

        @Override
        public StoryCharacterVO addCharacter(
                String projectCode,
                StoryCharacterVO character
        ) {
            addedCharacter = character;
            return character;
        }

        @Override
        public StoryCharacterVO updateCharacter(
                String projectCode,
                String characterCode,
                StoryCharacterVO character
        ) {
            updatedCharacter = character;
            return character;
        }

        @Override
        public GeneratedChapterVO overwriteChapterContent(
                String projectCode,
                int chapterNumber,
                String title,
                String content,
                int wordCount
        ) {
            overwrittenTitle = title;
            overwrittenContent = content;
            overwrittenWordCount = wordCount;
            chapterAfterOverwrite = new GeneratedChapterVO(
                    chapterNumber,
                    "第一章",
                    content,
                    wordCount,
                    overwrittenStatus
            );
            return chapterAfterOverwrite;
        }

        @Override
        public void deleteCharacter(String projectCode, String characterCode) {
            characterDeletionCalls++;
            deletedProjectCode = projectCode;
            deletedCharacterCode = characterCode;
            if (characterDeletionFailure != null) {
                throw characterDeletionFailure;
            }
        }

        @Override
        public void deleteChapter(String projectCode, int chapterNumber) {
        }

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return existingProject != null
                    && existingProject.projectCode().equals(projectCode)
                    ? existingProject : null;
        }

        @Override
        public GeneratedChapterVO findChapter(
                String projectCode,
                int chapterNumber
        ) {
            return chapterAfterSync == null ? chapterAfterOverwrite : chapterAfterSync;
        }

        @Override
        public List<GeneratedChapterVO> findChapters(String projectCode) {
            return List.of();
        }

        @Override
        public List<NovelProjectVO> findProjects() {
            return List.of();
        }

        @Override
        public List<VolumeChapterGroupVO> findChaptersByVolume(
                String projectCode
        ) {
            return List.of();
        }
    }

    private static final class RecordingChapterService implements IChapterService {
        private int resyncCalls;
        private RuntimeException resyncFailure;

        @Override
        public ChapterGenerationResultVO generateChapter(String projectCode, int chapterNumber) {
            return null;
        }

        @Override
        public ChapterGenerationResultVO resyncChapterDerivedData(
                String projectCode,
                int chapterNumber
        ) {
            resyncCalls++;
            if (resyncFailure != null) {
                throw resyncFailure;
            }
            return null;
        }

        @Override
        public ChapterGenerationResultVO resumeChapter(
                String workflowId,
                HumanDecisionEnum decision
        ) {
            return null;
        }
    }
}
