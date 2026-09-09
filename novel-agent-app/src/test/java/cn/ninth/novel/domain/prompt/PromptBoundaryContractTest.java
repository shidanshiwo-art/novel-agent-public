package cn.ninth.novel.domain.prompt;

import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.agent.SystemPrompt;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBoundaryContractTest {

    private static final List<String> FORBIDDEN_TERMS = List.of(
            "reviewIssueVOList",
            "powerSystemJson",
            "hardRulesJson",
            "characterCode",
            "BLOCKER",
            "MAJOR",
            "MINOR",
            "MALE_LEAD",
            "FEMALE_LEAD",
            "PROTAGONIST",
            "ALLY",
            "RIVAL",
            "ANTAGONIST",
            "VILLAIN",
            "SUPPORTING",
            "roleType"
    );

    @Test
    void modelPromptsMustNotExposeBackendStorageOrEnumNames() {
        List<String> prompts = List.of(
                PlanningPrompts.STORY_BIBLE_INIT_SYSTEM,
                PlanningPrompts.STORY_BIBLE_REVISION_SYSTEM,
                PlanningPrompts.ROOT_OUTLINE_SYSTEM,
                PlanningPrompts.NEXT_VOLUME_SYSTEM,
                PlanningPrompts.VOLUME_OUTLINE_SYSTEM,
                PlanningPrompts.NEXT_CHAPTER_OUTLINE_SYSTEM,
                PlanningPrompts.ARC_REGENERATION_SYSTEM,
                PlanningPrompts.CHAPTER_PLAN_SYSTEM,
                PlanningPrompts.CHARACTER_SYSTEM,
                SystemPrompt.DRAFT_SYSTEM_PROMPT,
                SystemPrompt.REVIEW_SYSTEM_PROMPT,
                SystemPrompt.REVISER_SYSTEM_PROMPT,
                SystemPrompt.COMPRESSION_SYSTEM_PROMPT
        );

        System.out.printf(
                "prompt boundary checked: prompts=%d, forbiddenTerms=%s%n",
                prompts.size(), FORBIDDEN_TERMS
        );
        assertThat(prompts).allSatisfy(prompt ->
                assertThat(prompt).doesNotContain(FORBIDDEN_TERMS.toArray(String[]::new))
        );
    }

    @Test
    void chapterPlanPromptMustTreatArcTitleAsFixedAndPlanOnlyExecution() {
        System.out.println("ChapterPlan Prompt verified: ARC title is fixed and model only plans execution");
        assertThat(PlanningPrompts.CHAPTER_PLAN_SYSTEM)
                .contains(
                        "当前章节标题已经由当前章纲确定",
                        "不得重新创作、修改或替换",
                        "只需要基于章纲生成本章写作执行计划"
                );
    }

    @Test
    void arcSummaryPromptsMustStayAtChapterOutlineGranularity() {
        List<String> arcPrompts = List.of(
                PlanningPrompts.NEXT_CHAPTER_OUTLINE_SYSTEM,
                PlanningPrompts.ARC_REGENERATION_SYSTEM
        );

        System.out.println("ARC 摘要粒度已验证：只保留章级要素，不展开场景级正文");
        assertThat(arcPrompts).allSatisfy(prompt ->
                assertThat(prompt)
                        .contains(
                                "核心事件",
                                "主要冲突",
                                "关键信息揭示",
                                "人物状态变化",
                                "结尾状态或钩子",
                                "不要展开成场景级正文",
                                "约 300～600 字左右",
                                "不是硬性字数或 Token 限制"
                        )
        );
    }

    @Test
    void arcPromptsMustKeepOneForwardTimeline() {
        List<String> arcPrompts = List.of(
                PlanningPrompts.NEXT_CHAPTER_OUTLINE_SYSTEM,
                PlanningPrompts.ARC_REGENERATION_SYSTEM
        );

        System.out.println("ARC 时间线约束已验证：事件不重复、时间线单向推进、不提前展开后续章节");
        assertThat(arcPrompts).allSatisfy(prompt ->
                assertThat(prompt).contains(
                        "同一事件只描述一次",
                        "不得在摘要后半段重新回到本章开头",
                        "不得把前一章已经发生的内容再次作为本章主体",
                        "不得提前展开下一章主要剧情",
                        "必须保持单一时间线向前推进"
                )
        );
    }

    @Test
    void chapterPlanPromptMustDecomposeArcIntoWritingBeats() {
        System.out.println("ChapterPlan 场景拆解约束已验证：只说明怎么写，不重新定义本章内容");
        assertThat(PlanningPrompts.CHAPTER_PLAN_SYSTEM)
                .contains("开场", "关键节拍", "冲突升级", "转折", "人物变化", "结尾钩子",
                        "说明本章应该如何写", "不重新定义这一章讲什么");
    }

    @Test
    void outlinePromptsMustKeepFormalCharactersInsideCharacterModule() {
        List<String> outlinePrompts = List.of(
                PlanningPrompts.ROOT_OUTLINE_SYSTEM,
                PlanningPrompts.NEXT_VOLUME_SYSTEM,
                PlanningPrompts.VOLUME_OUTLINE_SYSTEM,
                PlanningPrompts.NEXT_CHAPTER_OUTLINE_SYSTEM,
                PlanningPrompts.ARC_REGENERATION_SYSTEM
        );

        System.out.println("大纲阶段人物边界：只规划剧情，不输出未定义人物档案");
        assertThat(outlinePrompts).allSatisfy(prompt ->
                assertThat(prompt)
                        .contains("不创建、确认或保存正式人物记录")
                        .contains("不得为其输出详细人物档案")
        );
    }

    @Test
    void compressionPromptMustNotRequestCharacterStateExtraction() {
        String prompt = SystemPrompt.COMPRESSION_SYSTEM_PROMPT;

        System.out.printf(
                "compression prompt fields no longer include character states: containsCharacterStates=%s%n",
                prompt.contains("characterStates")
        );
        assertThat(prompt)
                .doesNotContain("characterStates", "持续人物状态", "人物状态");
    }

    @Test
    void compressionPromptMustKeepFutureRelevantCharacterChangesAsKeyEvents() {
        String prompt = SystemPrompt.COMPRESSION_SYSTEM_PROMPT;

        System.out.println(
                "compression keyEvents requires future-relevant actions, location, goals and major state changes"
        );
        assertThat(prompt).contains(
                "人物关键行动",
                "位置变化",
                "目标变化",
                "重大状态变化",
                "影响后续剧情"
        );
    }

    @Test
    void chapterSystemPromptsMustNotUseRuntimeStateNaming() {
        List<String> prompts = List.of(
                SystemPrompt.DRAFT_SYSTEM_PROMPT,
                SystemPrompt.REVIEW_SYSTEM_PROMPT,
                SystemPrompt.REVISER_SYSTEM_PROMPT,
                SystemPrompt.COMPRESSION_SYSTEM_PROMPT
        );

        List<String> forbiddenTerms = List.of("人物当前状态", "人物运行时状态", "角色状态快照");
        System.out.printf(
                "chapter system prompt static naming checked: prompts=%d, forbiddenTerms=%s%n",
                prompts.size(), forbiddenTerms
        );
        assertThat(prompts).allSatisfy(prompt ->
                assertThat(prompt).doesNotContain(forbiddenTerms.toArray(String[]::new))
        );
    }

    @Test
    void compressionSystemPromptShouldKeepSchemaAndCoreRules() {
        String prompt = SystemPrompt.COMPRESSION_SYSTEM_PROMPT;

        System.out.printf("compression system prompt length=%d%n", prompt.length());
        assertThat(prompt)
                .contains("短期记忆", "影响剧情连续性", "shortSummary", "keyEvents",
                        "unresolved", "endingHook", "state", "resources",
                        "abilities", "knowledge", "presence",
                        "只能使用正文明确发生或明确揭示的内容",
                        "沈夜：尸核数量为 0", "沈夜：辐射异能接近枯竭",
                        "沈夜：知道灵息确实存在", "沈夜：当前位于地下隧道区域",
                        "不组织成动态键值、事实关系或带来源与置信度的记录")
                .doesNotContain("\"entity\"", "\"attribute\"", "\"oldValue\"", "\"newValue\"",
                        "\"confidence\"", "\"source\"", "\"validFrom\"", "\"validTo\"",
                        "知识图谱抽取结果");
    }

    @Test
    void storyStateSnapshotShouldKeepOnlyFourStringListComponents() {
        List<String> components = Arrays.stream(StoryStateSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        System.out.println("StoryStateSnapshot 结构：" + components);
        assertThat(components).containsExactly("resources", "abilities", "knowledge", "presence");
        assertThat(StoryStateSnapshot.class.getRecordComponents())
                .allSatisfy(component -> assertThat(component.getType()).isEqualTo(List.class));
    }

    @Test
    void compressionSystemPromptShouldDefinePreviousStateUpdateRules() {
        String prompt = SystemPrompt.COMPRESSION_SYSTEM_PROMPT;

        System.out.println("COMPRESSION 状态更新规则：保留、更新、失效、禁止推测及轻量化约束已声明");
        assertThat(prompt)
                .contains("上一版状态没有被正文改变时，必须保留")
                .contains("正文明确定义了新状态时，更新上一版状态")
                .contains("已失效状态不能继续保留")
                .contains("不允许根据常识或剧情趋势推测状态")
                .contains("只保存后续章节忘记后会产生明显逻辑错误的状态")
                .contains("不保存琐碎动作和短暂细节")
                .contains("每个分类只保留少量真正影响后续连续性的状态")
                .contains("删除已经失效、重复或不再重要的状态")
                .contains("不设置固定条数上限");
    }

    @Test
    void draftSystemPromptShouldKeepOnlyCoreGenerationConstraints() {
        String prompt = SystemPrompt.DRAFT_SYSTEM_PROMPT;
        long nonBlankLineCount = prompt.lines()
                .filter(line -> !line.isBlank())
                .count();

        System.out.printf(
                "DRAFT System Prompt 核心约束：nonBlankLineCount=%d, planMentionCount=%d%n",
                nonBlankLineCount,
                countOccurrences(prompt, "本章计划")
        );
        assertThat(prompt)
                .contains("遵守给定的故事设定和已确认的本章计划")
                .contains("上一章正文负责直接衔接")
                .contains("只完成当前章节，不提前写完未来章节")
                .contains("只输出当前章节正文")
                .doesNotContain("最终只输出小说正文", "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.");
        assertThat(nonBlankLineCount).isLessThanOrEqualTo(8);
        assertThat(countOccurrences(prompt, "本章计划")).isEqualTo(1);
    }

    @Test
    void draftSystemPromptShouldRespectCurrentStoryState() {
        String prompt = SystemPrompt.DRAFT_SYSTEM_PROMPT;

        System.out.println("DRAFT 当前有效状态约束：不得无原因违反，状态改变必须有正文事件或信息来源");
        assertThat(prompt)
                .contains("不得无原因违反“当前有效状态”")
                .contains("如果本章改变某项状态")
                .contains("明确事件或信息来源");
    }

    @Test
    void reviewSystemPromptShouldCheckStoryStateContinuity() {
        String prompt = SystemPrompt.REVIEW_SYSTEM_PROMPT;

        System.out.println("REVIEW 当前有效状态连续性检查已覆盖资源、物品、能力、知识、人物和地点");
        assertThat(prompt)
                .contains("资源是否凭空恢复")
                .contains("物品是否无来源出现")
                .contains("能力是否突然升级")
                .contains("角色是否使用尚未获得的知识")
                .contains("人物是否无铺垫出现")
                .contains("当前地点或状态是否无原因跳变");
    }

    private int countOccurrences(String text, String value) {
        return text.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }

    @Test
    void draftSystemPromptMustNotExposeBackendImplementationDetails() {
        List<String> forbiddenTerms = List.of(
                "数据库字段",
                "状态枚举",
                "节点类型",
                "Graph",
                "DTO",
                "VO",
                "持久化",
                "sequence",
                "sequenceNo",
                "workflowId",
                "chapterPlanId",
                "后端校验"
        );

        System.out.printf(
                "DRAFT System Prompt 后端实现术语检查：forbiddenTerms=%s%n",
                forbiddenTerms
        );
        assertThat(SystemPrompt.DRAFT_SYSTEM_PROMPT)
                .doesNotContain(forbiddenTerms.toArray(String[]::new));
    }
}
