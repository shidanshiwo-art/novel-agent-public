package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.PowerSystemDraftVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleDraftVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.service.NovelProjectService;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleGenerationTest {

    @Test
    void shouldGenerateStoryBibleDraftWithoutReturningFormalMetadata() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        NovelProjectService service = new NovelProjectService(
                new RecordingProjectRepository(), model, drafts
        );

        PlanningDraftVO result = service.generateStoryBible(
                "novel-001", "冷峻的城市奇幻，主角能听见旧建筑的记忆"
        );

        StoryBibleDraftVO payload = (StoryBibleDraftVO) result.payload();
        System.out.printf(
                "story bible draft: type=%s, premise=%s, levels=%s%n",
                result.draftType(), payload.oneSentencePremise(),
                payload.powerSystem().levels()
        );
        assertThat(model.responseTypes).containsExactly(StoryBibleDraftVO.class);
        assertThat(model.systemPrompt).isEqualTo(PlanningPrompts.STORY_BIBLE_INIT_SYSTEM);
        assertThat(model.userPrompt)
                .contains("项目标题=回响之城", "题材=城市奇幻", "预计章节数=100")
                .contains("用户补充要求=冷峻的城市奇幻，主角能听见旧建筑的记忆")
                .doesNotContain("项目=", "人物=", "大纲=", "历史=", "projectCode");
        assertThat(model.systemPrompt)
                .contains("\"description\"", "\"levels\"", "\"supplement\"")
                .contains("灵根、血脉、天赋、属性、职业、魔法亲和、异能类型、装备体系")
                .contains("只有当前题材确实涉及", "涉及的内容要完整保留")
                .doesNotContain("必须完整写入这两个字段，不得省略")
                .doesNotContain("\"mechanism\"", "\"cost\"", "\"ranks\"");
        assertThat(result.draftType()).isEqualTo("STORY_BIBLE");
        assertThat(payload).isEqualTo(new StoryBibleDraftVO(
                "城市记忆中的失踪案", "记忆与归属", "主角对抗抹除城市记忆的组织",
                "城市恢复记忆，主角承担失去一部分自我的代价", "现代城市下埋藏着会记忆的旧建筑",
                new PowerSystemDraftVO("回响", "触碰建筑即可读取残留记忆", "听见、共鸣", "会混淆自身记忆"),
                List.of("已经发生的历史不能被直接改写"), "第三人称限知，克制而有余韵"
        ));
    }

    @Test
    void shouldKeepStoryBibleIndependentFromFormalCharacterNames() {
        String[] prompts = {
                PlanningPrompts.STORY_BIBLE_INIT_SYSTEM,
                PlanningPrompts.STORY_BIBLE_REVISION_SYSTEM
        };

        for (String prompt : prompts) {
            System.out.printf("Story Bible 角色边界 Prompt：%s%n", prompt);
            assertThat(prompt)
                    .contains("本阶段只定义故事方向和世界设定，不创建正式人物。")
                    .contains("如果需要描述人物，只描述其剧情功能、身份或处境")
                    .contains("对于尚未正式创建的人物，不要主动起具体姓名。")
                    .contains("用户补充要求中明确给出了人物姓名，可以尊重该姓名")
                    .contains("oneSentencePremise、mainConflict、worldBackground、endingDirection")
                    .doesNotContain("不得自行生成“林渊”“苏晚”等具体姓名。", "Story Bible 不依赖正式角色姓名");
        }
    }

    @Test
    void shouldPreserveGenreSpecificConceptsInsideGenericPowerFields() {
        RecordingModelPort model = new RecordingModelPort();
        String description = "灵根决定施术通道，血脉影响身体承载，天赋与属性共同塑造异能类型；职业决定训练路径。";
        String supplement = "魔法亲和决定可学习的法术，装备体系提供额外能力；其他题材特有概念均以自然语言保留。";
        model.bibleResponse = new StoryBibleDraftVO(
                "题材概念保真测试", "主题", "矛盾", "结局", "世界",
                new PowerSystemDraftVO("通用体系", description, "初阶、进阶", supplement),
                List.of("规则"), "文风"
        );
        NovelProjectService service = new NovelProjectService(
                new RecordingProjectRepository(), model, new RecordingDraftRepository()
        );

        StoryBibleDraftVO payload = (StoryBibleDraftVO) service
                .generateStoryBible("novel-001", "保留所有题材特有概念")
                .payload();

        System.out.printf(
                "power system natural language preserved: description=%s, supplement=%s%n",
                payload.powerSystem().description(), payload.powerSystem().supplement()
        );
        assertThat(payload.powerSystem().description()).isEqualTo(description);
        assertThat(payload.powerSystem().supplement()).isEqualTo(supplement);
    }

    @Test
    void shouldReviseFormalStoryBibleWithOnlyCurrentBibleAndRequirement() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingProjectRepository repository = new RecordingProjectRepository();
        repository.existingBible = new StoryBibleVO(
                "钟楼下的失踪案", "记忆与归属", "主角对抗抹除记忆的组织",
                "保留城市记忆，也接受无法找回的一部分过去", "现代城市，钟楼保存着旧城区的集体记忆",
                "{\"name\":\"回响\",\"description\":\"读取钟楼回声\",\"levels\":\"听见\",\"supplement\":\"丢失当天记忆\"}",
                "[\"钟楼每晚只能响一次\",\"残页不能被撕毁\"]", "第三人称限知，克制而有余韵", "CONFIRMED"
        );
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        PlanningDraftVO result = service.generateStoryBible(
                "novel-001", "保留钟楼和残页规则，弱化超自然表现，整体更偏现实悬疑。"
        );

        System.out.printf(
                "story bible revision draft: type=%s, prompt=%s%n",
                result.draftType(), model.userPrompt
        );
        assertThat(model.responseTypes).containsExactly(StoryBibleDraftVO.class);
        assertThat(model.systemPrompt).isEqualTo(PlanningPrompts.STORY_BIBLE_REVISION_SYSTEM);
        assertThat(model.systemPrompt)
                .contains("只有当前题材确实涉及", "涉及的内容要完整保留")
                .doesNotContain("必须完整写入这两个字段，不得省略");
        assertThat(model.userPrompt)
                .contains("【当前正式 Story Bible】")
                .contains("一句话故事=钟楼下的失踪案")
                .contains("世界背景=现代城市，钟楼保存着旧城区的集体记忆")
                .contains("特殊体系\n名称：回响\n说明：读取钟楼回声\n等级/境界：听见\n补充设定：丢失当天记忆")
                .contains("不可违反的规则\n- 钟楼每晚只能响一次\n- 残页不能被撕毁")
                .contains("【用户调整要求】\n保留钟楼和残页规则，弱化超自然表现，整体更偏现实悬疑。")
                .doesNotContain("项目标题=", "题材=", "预计章节数=", "powerSystemJson", "hardRulesJson", "{", "}");
        assertThat(result.draftType()).isEqualTo("STORY_BIBLE");
        assertThat(drafts.values).hasSize(1);
    }

    @Test
    void shouldRenderHistoricalStoryBibleStructuresAsNaturalLanguage() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingProjectRepository repository = new RecordingProjectRepository();
        repository.existingBible = new StoryBibleVO(
                "旧城异闻", "记忆与代价", "主角与失控力量的冲突", "主角学会承担代价",
                "旧城隐藏着异常力量", "{\"境界\":[\"锻体\",\"凝气\"],\"锻体\":\"只能强化肉身\"}",
                "{\"rules\":[\"能力必须付出代价\",\"历史不能直接改写\"]}",
                "克制的第三人称限知", "CONFIRMED"
        );
        NovelProjectService service = new NovelProjectService(repository, model, drafts);

        service.generateStoryBible("novel-001", "保留旧有力量层级");

        System.out.printf("historical story bible prompt: %s%n", model.userPrompt);
        assertThat(model.userPrompt)
                .contains("等级/境界：\n- 锻体\n- 凝气")
                .contains("锻体：只能强化肉身")
                .contains("不可违反的规则\n- 能力必须付出代价\n- 历史不能直接改写")
                .doesNotContain("{", "}");
    }

    @Test
    void shouldConfirmEditedDraftIntoFormalStoryBibleAndRemoveDraft() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-bible", "novel-001", "STORY_BIBLE",
                new StoryBibleDraftVO(
                        "原始梗概", "原始主题", "原始矛盾", "原始结局", "原始世界",
                        new PowerSystemDraftVO("原始体系", "原始说明", "一阶", "原始补充设定"),
                        List.of("原始规则"), "原始文风"
                ),
                Instant.parse("2026-08-29T01:00:00Z")
        ));
        RecordingProjectRepository repository = new RecordingProjectRepository();
        NovelProjectService service = new NovelProjectService(
                repository, model, drafts
        );

        StoryBibleVO saved = service.confirmStoryBible(
                "novel-001",
                "draft-bible",
                new StoryBibleVO(
                        "修订后的梗概", "修订后的主题", "修订后的矛盾", "修订后的结局", "修订后的世界",
                        "{\"name\":\"回响\",\"description\":\"灵根决定施术通道，血脉影响身体承载，天赋与属性共同塑造异能类型；职业决定训练路径。\",\"levels\":\"听见、共鸣\",\"supplement\":\"魔法亲和决定可学习的法术，装备体系提供额外能力；其他题材特有概念均以自然语言保留。\"}",
                        "[\"历史不能直接改写\",\"能力必须付出代价\"]", "第三人称限知", "DRAFT"
                )
        );

        System.out.printf(
                "confirmed story bible: status=%s, power=%s, removed=%s%n",
                saved.status(), saved.powerSystemJson(), drafts.removedIds
        );
        assertThat(model.responseTypes).isEmpty();
        assertThat(repository.savedBible.status()).isEqualTo("CONFIRMED");
        assertThat(repository.savedBible.powerSystemJson()).isEqualTo(
                "{\"name\":\"回响\",\"description\":\"灵根决定施术通道，血脉影响身体承载，天赋与属性共同塑造异能类型；职业决定训练路径。\",\"levels\":\"听见、共鸣\",\"supplement\":\"魔法亲和决定可学习的法术，装备体系提供额外能力；其他题材特有概念均以自然语言保留。\"}"
        );
        assertThat(repository.savedBible.hardRulesJson()).isEqualTo(
                "[\"历史不能直接改写\",\"能力必须付出代价\"]"
        );
        assertThat(drafts.removedIds).containsExactly("draft-bible");
    }

    @Test
    void shouldAllowPowerSystemToBeEmptyWhenGenreDoesNotNeedOne() {
        RecordingModelPort model = new RecordingModelPort();
        model.bibleResponse = new StoryBibleDraftVO(
                "普通人的返乡故事", "故乡与选择", "主人公与现实生活的冲突",
                "回到能够继续生活的地方", "当代小城，遵循现实社会与技术边界",
                null, List.of("关键人物不能凭空改变已经发生的事实"), "克制的第三人称限知"
        );
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        NovelProjectService service = new NovelProjectService(
                new RecordingProjectRepository(), model, drafts
        );

        PlanningDraftVO result = service.generateStoryBible("novel-001", "返乡题材");

        System.out.printf("story bible without power system: payload=%s%n", result.payload());
        assertThat(((StoryBibleDraftVO) result.payload()).powerSystem()).isNull();
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private final List<Class<?>> responseTypes = new ArrayList<>();
        private String userPrompt;
        private String systemPrompt;
        private StoryBibleDraftVO bibleResponse = new StoryBibleDraftVO(
                "城市记忆中的失踪案", "记忆与归属", "主角对抗抹除城市记忆的组织",
                "城市恢复记忆，主角承担失去一部分自我的代价", "现代城市下埋藏着会记忆的旧建筑",
                new PowerSystemDraftVO("回响", "触碰建筑即可读取残留记忆", "听见、共鸣", "会混淆自身记忆"),
                List.of("已经发生的历史不能被直接改写"), "第三人称限知，克制而有余韵"
        );

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            responseTypes.add(responseType);
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            if (responseType == StoryBibleDraftVO.class) {
                return (T) bibleResponse;
            }
            throw new AssertionError("unexpected response type: " + responseType);
        }
    }

    private static final class RecordingDraftRepository implements IPlanningDraftRepository {
        private final List<PlanningDraftVO> values = new ArrayList<>();
        private final List<String> removedIds = new ArrayList<>();

        private void seed(PlanningDraftVO value) {
            values.add(value);
        }

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            PlanningDraftVO value = new PlanningDraftVO(
                    "draft-generated", projectCode, draftType, payload,
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
            values.removeIf(value -> value.projectCode().equals(projectCode));
        }
    }

    private static final class RecordingProjectRepository
            implements INovelProjectRepository {
        private StoryBibleVO savedBible;
        private StoryBibleVO existingBible;

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(projectCode, "回响之城", "城市奇幻", 100, 2500, 0, "DRAFT");
        }

        @Override
        public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
            savedBible = bible;
            return bible;
        }

        @Override
        public NovelProjectVO createProject(NovelProjectVO project) {
            return project;
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            return existingBible;
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            return List.of();
        }

        @Override
        public StoryCharacterVO addCharacter(
                String projectCode, StoryCharacterVO character
        ) {
            return character;
        }

        @Override
        public StoryCharacterVO updateCharacter(
                String projectCode, String characterCode, StoryCharacterVO character
        ) {
            return character;
        }

        @Override
        public cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO overwriteChapterContent(
                String projectCode, int chapterNumber, String title, String content, int wordCount
        ) {
            return null;
        }

        @Override
        public void deleteChapter(String projectCode, int chapterNumber) {
        }

        @Override
        public void deleteCharacter(String projectCode, String characterCode) {
            throw new UnsupportedOperationException("当前测试不涉及人物删除");
        }

        @Override
        public cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO findChapter(
                String projectCode, int chapterNumber
        ) {
            return null;
        }

        @Override
        public List<cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO> findChapters(
                String projectCode
        ) {
            return List.of();
        }

        @Override
        public List<NovelProjectVO> findProjects() {
            return List.of();
        }

        @Override
        public List<cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO> findChaptersByVolume(
                String projectCode
        ) {
            return List.of();
        }
    }
}
