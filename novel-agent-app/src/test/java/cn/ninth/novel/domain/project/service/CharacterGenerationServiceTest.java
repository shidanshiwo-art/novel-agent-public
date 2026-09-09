package cn.ninth.novel.domain.project.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftListVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterGenerationServiceTest {

    @Test
    void shouldGenerateCharacterDraftWithoutWritingFormalCharacter() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        PlanningDraftVO result = service.generateCharacters(
                "novel-001", 4, "男主偏理性，女主与核心谜案有关"
        );

        CharacterDraftListVO payload = (CharacterDraftListVO) result.payload();
        System.out.printf(
                "character draft: id=%s, count=%d, prompt=%s%n",
                result.draftId(), payload.characters().size(), model.userPrompt
        );
        assertThat(model.responseType).isEqualTo(CharacterDraftListVO.class);
        assertThat(model.systemPrompt).isEqualTo(PlanningPrompts.CHARACTER_SYSTEM);
        assertThat(model.userPrompt)
                .contains("项目标题=回响之城", "题材=城市奇幻")
                .contains("本次建议新增 4 名人物")
                .contains("一句话故事=钟楼下的失踪案")
                .contains("已有角色摘要", "顾言")
                .contains("用户补充要求=男主偏理性，女主与核心谜案有关")
                .doesNotContain(
                        "preferredCount", "characterCode", "roleType", "currentStateJson", "lifeStatus",
                        "status", "MALE_LEAD", "FEMALE_LEAD", "数据库 ID", "数据库ID"
                );
        assertThat(result.draftType()).isEqualTo("CHARACTERS");
        assertThat(payload.characters()).containsExactly(new CharacterDraftVO(
                "林澈", "男主角", "男", "二十七岁", "清瘦克制",
                "理性谨慎", "在旧城长大，擅长追查线索", "与核心谜案有个人牵连"
        ));
        assertThat(repository.addedCharacters).isEmpty();
        assertThat(repository.updatedCharacters).isEmpty();
        assertThat(drafts.savedPayload).isEqualTo(payload);
    }

    @Test
    void shouldIncludeCountOneAndKeepExistingCharacterOutOfFormalWrites() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        PlanningDraftVO result = service.generateCharacters(
                "novel-001", 1, "希望人物与谜案有关"
        );

        CharacterDraftListVO payload = (CharacterDraftListVO) result.payload();
        System.out.printf(
                "人物增量生成回归：preferredCount=1，Prompt=%s，新增=%d，更新=%d%n",
                model.userPrompt, repository.addedCharacters.size(), repository.updatedCharacters.size()
        );
        assertThat(model.userPrompt)
                .contains("本次建议新增 1 名人物")
                .contains("已有角色摘要", "顾言")
                .contains("用户补充要求=希望人物与谜案有关");
        assertThat(payload.characters()).hasSize(1);
        assertThat(repository.addedCharacters).isEmpty();
        assertThat(repository.updatedCharacters).isEmpty();
        assertThat(drafts.savedPayload).isEqualTo(payload);
    }

    @Test
    void shouldIncludeBookAndCurrentVolumeWhenGeneratingCharactersAfterOutlineExists() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingProjectRepository repository = new RecordingProjectRepository();
        RecordingPlanningRepository planning = new RecordingPlanningRepository();
        NovelProjectService service = new NovelProjectService(
                repository, model, drafts, null, planning
        );

        service.generateCharacters("novel-001", 2, "补充当前卷会登场的关键人物");

        System.out.println("已有 BOOK 与当前 VOLUME 时，人物生成应携带滚动剧情上下文");
        assertThat(model.userPrompt)
                .contains("【已有大纲剧情位置】")
                .contains("全书大纲：标题=回响之城全书")
                .contains("主角必须查清钟楼失踪案")
                .contains("当前卷：标题=南境回声")
                .contains("监察使将成为本卷核心阻力")
                .contains("以上是已确认的剧情规划，用于定位本次新增人物")
                .contains("已有角色摘要", "顾言")
                .doesNotContain("BOOK", "VOLUME", "nodeCode", "sequenceNo");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 16})
    void shouldRejectPreferredCountOutsideRangeBeforeCallingModel(Integer preferredCount) {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        assertThatThrownBy(() -> service.generateCharacters(
                "novel-001", preferredCount, "用户要求"
        ))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
                    assertThat(exception.getInternalDetail())
                            .contains("preferredCount 必须在 1 到 15 之间");
                    assertThat(exception.getInfo()).isNull();
                });

        System.out.printf(
                "人物生成数量校验：preferredCount=%s 被拒绝，模型调用=%s，草稿保存=%s%n",
                preferredCount, model.responseType != null, drafts.savedPayload != null
        );
        assertThat(model.responseType).isNull();
        assertThat(drafts.savedPayload).isNull();
    }

    @Test
    void shouldConfirmEditedCharacterDraftIntoFormalCharactersAndRemoveDraft() throws Exception {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-character-001", "novel-001", "CHARACTERS",
                new CharacterDraftListVO(List.of(new CharacterDraftVO(
                        "林澈", "男主角", "男", "二十七岁", "清瘦克制",
                        "理性谨慎", "在旧城长大", "核心谜案相关"
                ))), Instant.parse("2026-08-29T01:00:00Z")
        ));
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        List<StoryCharacterVO> result = service.confirmCharacters(
                "novel-001", "draft-character-001", List.of(new CharacterDraftVO(
                        "林澈·修订", "男主角", "男", "二十八岁", "更显疲惫",
                        "理性但开始动摇", "修订后的人物经历", "确认后的备注"
                ))
        );

        System.out.printf(
                "confirmed character draft: count=%d, code=%s, removed=%s%n",
                result.size(), result.get(0).characterCode(), drafts.removedIds
        );
        JsonNode state = new ObjectMapper().readTree(result.get(0).currentStateJson());
        System.out.printf(
                "AI Confirm 初始化：code=%s, state=%s, life=%s, status=%s%n",
                result.get(0).characterCode(), result.get(0).currentStateJson(),
                result.get(0).lifeStatus(), result.get(0).status()
        );
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("林澈·修订");
        assertThat(result.get(0).characterCode()).startsWith("char-");
        assertThat(state.isObject()).isTrue();
        assertThat(result.get(0).currentStateJson()).isEqualTo("{}");
        assertThat(result.get(0).lifeStatus()).isEqualTo("ALIVE");
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
        assertThat(repository.addedCharacters).hasSize(1);
        assertThat(drafts.removedIds).containsExactly("draft-character-001");
        assertThat(drafts.find("novel-001", "draft-character-001")).isEmpty();
    }

    @Test
    void shouldDiscardCharacterDraftWithoutWritingFormalCharacters() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-character-001", "novel-001", "CHARACTERS",
                new CharacterDraftListVO(List.of()), Instant.parse("2026-08-29T01:00:00Z")
        ));
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        service.discardCharacterDraft("novel-001", "draft-character-001");

        System.out.printf("discarded character draft: removed=%s%n", drafts.removedIds);
        assertThat(repository.addedCharacters).isEmpty();
        assertThat(drafts.removedIds).containsExactly("draft-character-001");
        assertThat(drafts.find("novel-001", "draft-character-001")).isEmpty();
    }

    @Test
    void shouldRejectConfirmWhenCharacterNameAlreadyExists() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-character-001", "novel-001", "CHARACTERS",
                new CharacterDraftListVO(List.of(new CharacterDraftVO(
                        "顾言", "女主角", "女", "二十五岁", "短发",
                        "冷静", "调查钟楼失踪案", "核心谜案相关"
                ))), Instant.parse("2026-08-29T01:00:00Z")
        ));
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        assertThatThrownBy(() -> service.confirmCharacters(
                "novel-001", "draft-character-001", List.of(new CharacterDraftVO(
                        " 顾言 ", "女主角", "女", "二十五岁", "短发",
                        "冷静", "修订后的人物经历", "重复姓名"
                ))
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(
                        ResponseCode.ILLEGAL_PARAMETER.getCode()
                ));

        System.out.printf(
                "duplicate character confirmation rejected: formalWrites=%d, removed=%s%n",
                repository.addedCharacters.size(), drafts.removedIds
        );
        assertThat(repository.addedCharacters).isEmpty();
        assertThat(drafts.removedIds).isEmpty();
        assertThat(drafts.find("novel-001", "draft-character-001")).isPresent();
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private Class<?> responseType;
        private String systemPrompt;
        private String userPrompt;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            this.responseType = responseType;
            if (responseType == CharacterDraftListVO.class) {
                return (T) new CharacterDraftListVO(List.of(new CharacterDraftVO(
                        "林澈", "男主角", "男", "二十七岁", "清瘦克制",
                        "理性谨慎", "在旧城长大，擅长追查线索", "与核心谜案有个人牵连"
                )));
            }
            throw new AssertionError("unexpected response type: " + responseType);
        }
    }

    private static final class RecordingDraftRepository implements IPlanningDraftRepository {
        private final List<PlanningDraftVO> values = new ArrayList<>();
        private final List<String> removedIds = new ArrayList<>();
        private Object savedPayload;

        private void seed(PlanningDraftVO value) {
            values.add(value);
        }

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            savedPayload = payload;
            PlanningDraftVO value = new PlanningDraftVO(
                    "draft-character-001", projectCode, draftType, payload,
                    Instant.parse("2026-08-29T01:00:00Z")
            );
            values.add(value);
            return value;
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return values.stream()
                    .filter(value -> value.projectCode().equals(projectCode)
                            && value.draftId().equals(draftId))
                    .findFirst();
        }

        @Override
        public void remove(String projectCode, String draftId) {
            removedIds.add(draftId);
            values.removeIf(value -> value.projectCode().equals(projectCode)
                    && value.draftId().equals(draftId));
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }

    private static final class RecordingPlanningRepository implements IPlanningRepository {
        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return null;
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            return List.of(
                    new OutlineNodeVO(
                            "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                            "回响之城全书", "主角必须查清钟楼失踪案，最终找回城市记忆",
                            1, 100, "READY"
                    ),
                    new OutlineNodeVO(
                            "VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                            "旧城钟声", "主角在旧城区追查失踪案的源头",
                            1, 30, "READY"
                    ),
                    new OutlineNodeVO(
                            "VOL_002", "BOOK_001", OutlineNodeKindEnum.VOLUME, 2,
                            "南境回声", "监察使将成为本卷核心阻力，主角必须在第三卷前取得关键证据",
                            31, 60, "PLANNED"
                    )
            );
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return false;
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return false;
        }
    }

    private static final class RecordingProjectRepository implements INovelProjectRepository {
        private final List<StoryCharacterVO> addedCharacters = new ArrayList<>();
        private final List<StoryCharacterVO> updatedCharacters = new ArrayList<>();

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(projectCode, "回响之城", "城市奇幻", 100, 2500, 0, "DRAFT");
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            return new StoryBibleVO(
                    "钟楼下的失踪案", "记忆与归属", "主角对抗抹除记忆的组织",
                    "保留城市记忆，也接受无法找回的一部分过去", "现代城市，钟楼保存着旧城区的集体记忆",
                    "{\"name\":\"回响\",\"mechanism\":\"读取钟楼回声\",\"cost\":\"丢失当天记忆\",\"ranks\":[\"听见\"]}",
                    "[\"钟楼每晚只能响一次\"]", "第三人称限知", "CONFIRMED"
            );
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            return List.of(new StoryCharacterVO(
                    "char-existing", "顾言", "女主角", "女", "二十五岁", "短发",
                    "冷静", "调查钟楼失踪案", "核心谜案相关", "未知", "ALIVE", "ACTIVE"
            ));
        }

        @Override
        public StoryCharacterVO addCharacter(String projectCode, StoryCharacterVO character) {
            addedCharacters.add(character);
            return character;
        }

        @Override
        public StoryCharacterVO updateCharacter(String projectCode, String characterCode, StoryCharacterVO character) {
            updatedCharacters.add(character);
            return character;
        }

        @Override
        public NovelProjectVO createProject(NovelProjectVO project) {
            return project;
        }

        @Override
        public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
            return bible;
        }

        @Override
        public GeneratedChapterVO overwriteChapterContent(String projectCode, int chapterNumber, String title, String content, int wordCount) {
            return new GeneratedChapterVO(chapterNumber, title, content, wordCount, "FINALIZED");
        }

        @Override
        public void deleteChapter(String projectCode, int chapterNumber) {
        }

        @Override
        public void deleteCharacter(String projectCode, String characterCode) {
            throw new UnsupportedOperationException("当前测试不涉及人物删除");
        }

        @Override
        public GeneratedChapterVO findChapter(String projectCode, int chapterNumber) {
            return null;
        }

        @Override
        public List<GeneratedChapterVO> findChapters(String projectCode) {
            return new ArrayList<>();
        }

        @Override
        public List<NovelProjectVO> findProjects() {
            return new ArrayList<>();
        }

        @Override
        public List<VolumeChapterGroupVO> findChaptersByVolume(String projectCode) {
            return new ArrayList<>();
        }
    }
}
