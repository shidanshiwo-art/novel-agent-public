package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.DRAFT_SYSTEM_PROMPT;

class DraftMemoryContextIntegrationTest {

    @Test
    void shouldUseDraftMemoryPackAndKeepPreviousChapterBridge() {
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder()
                        .projectCode("draft-memory-project")
                        .title("记忆测试")
                        .build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(200)
                        .outlineNodeCode("ARC_200")
                        .title("北港")
                        .summary("继续调查")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_200", "VOL_1", OutlineNodeKindEnum.ARC, 1,
                        "北港", "继续调查", 200, 200, "READY"))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                199,
                                "旧近期摘要，不应覆盖统一上下文",
                                List.of(), List.of(), null)))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(199)
                                .title("上一章")
                                .content("上一章完整正文 bridge")
                                .build())
                        .build())
                .memoryContextPack(new MemoryContextPack(
                        MemoryProfile.DRAFT,
                        List.of(MemoryContextItem.of(
                                "rule-north-port", MemoryContextCategory.RULES,
                                "北港夜间必须遵守灯塔规则", 7)),
                        List.of(MemoryContextItem.of(
                                "open-loop-crystal", MemoryContextCategory.OPEN_LOOPS,
                                "残晶来源仍未解决", 7)),
                        List.of(MemoryContextItem.of(
                                "state-crystal", MemoryContextCategory.CURRENT_STATES,
                                "残晶当前位于马车暗格", 199)),
                        List.of(),
                        List.of(),
                        40))
                .build();

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);

        System.out.printf("DRAFT MemoryContextProvider 上下文命中：current=%s, loop=%s, bridge=%s%n",
                prompt.contains("残晶当前位于马车暗格"),
                prompt.contains("残晶来源仍未解决"),
                prompt.contains("上一章完整正文 bridge"));
        assertThat(prompt)
                .contains("残晶来源仍未解决", "残晶当前位于马车暗格",
                        "北港夜间必须遵守灯塔规则", "上一章完整正文 bridge")
                .doesNotContain("旧近期摘要，不应覆盖统一上下文");
    }

    @Test
    void shouldRenderBusinessMemoryLabelsAndSeparateChangedState() {
        MemoryContextItem known = MemoryContextItem.of(
                "known-origin", MemoryContextCategory.CONSOLIDATED,
                "同源结论已经确认，不需要重新调查。", 8);
        MemoryContextItem current = MemoryContextItem.of(
                "current-injury", MemoryContextCategory.CURRENT_STATES,
                "沈夜右臂仍麻木。", 10);
        MemoryContextItem changed = MemoryContextItem.of(
                "changed-location", MemoryContextCategory.CURRENT_STATES,
                "黑色残晶：旧钟楼地下室 → 北上马车暗格。", 11);
        MemoryContextItem open = MemoryContextItem.of(
                "open-buyer", MemoryContextCategory.OPEN_LOOPS,
                "买家身份仍未查明。", 11);
        MemoryContextItem rule = MemoryContextItem.of(
                "rule-marker", MemoryContextCategory.RULES,
                "标记必须在月圆时才触发。", 11);
        MemoryContextItem background = MemoryContextItem.of(
                "background-observation", MemoryContextCategory.EPISODES,
                "第三方曾观察到标记发光。", 10);
        MemoryContextItem changedEvent = MemoryContextItem.of(
                "changed-event", MemoryContextCategory.EPISODES,
                "沈夜将旧锁从地下室转移到北港。", 11);

        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("draft-label-project").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(12)
                        .outlineNodeCode("ARC_12")
                        .title("买家")
                        .summary("追查买家身份")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_12", "VOL_1", OutlineNodeKindEnum.ARC, 1,
                        "买家", "追查买家身份", 12, 12, "READY"))
                .history(ChapterHistoryVO.builder().build())
                .memoryContextPack(new MemoryContextPack(
                        MemoryProfile.DRAFT,
                        List.of(rule),
                        List.of(open),
                        List.of(current, changed),
                        List.of(known),
                        List.of(background, changedEvent),
                        80))
                .build();

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);

        System.out.printf(
                "DRAFT 业务记忆标签：known=%s, current=%s, changed=%s, open=%s, rule=%s, background=%s%n",
                prompt.contains("[KNOWN_CONFIRMED]"), prompt.contains("[CURRENT_STATE]"),
                prompt.contains("[CHANGED_STATE]"), prompt.contains("[OPEN_QUESTION]"),
                prompt.contains("[RULE]"), prompt.contains("[BACKGROUND_ONLY]"));
        assertThat(prompt)
                .contains(
                        "[KNOWN_CONFIRMED] 第8章：同源结论已经确认，不需要重新调查。",
                        "[CURRENT_STATE] 第10章：沈夜右臂仍麻木。",
                        "[CHANGED_STATE] 第11章：黑色残晶：旧钟楼地下室 → 北上马车暗格。",
                        "[OPEN_QUESTION] 第11章：买家身份仍未查明。",
                        "[RULE] 第11章：标记必须在月圆时才触发。",
                        "[BACKGROUND_ONLY] 第10章：第三方曾观察到标记发光。",
                        "[CHANGED_STATE] 第11章：沈夜将旧锁从地下室转移到北港。");
    }

    @Test
    void draftSystemPromptShouldTreatKnownFactsAsPrerequisitesAndPushNewInformation() {
        System.out.println("DRAFT 重复控制约束：已知前提、变化显式化、开放问题推进已写入系统提示词");
        assertThat(DRAFT_SYSTEM_PROMPT)
                .contains("KNOWN_CONFIRMED 只作为行动前提")
                .contains("不重新调查、怀疑或确认")
                .contains("CHANGED_STATE 必须写出旧状态到新状态的可见变化")
                .contains("OPEN_QUESTION 才是本章调查和推进的优先对象")
                .contains("KNOWN、NEW、CHANGED、UNRESOLVED")
                .contains("沿用结论、新增信息、改变状态和仍未解决的问题")
                .contains("已确认的信息不要借对话重新讲解");
    }

    @Test
    void v1DraftPackShouldNotReintroduceLegacyHistoryWhenOnlyStateIsSelected() {
        MemoryContextItem state = MemoryContextItem.canonical(
                "CANONICAL_FACT", "current-state-only", MemoryContextCategory.CURRENT_STATES,
                "残晶当前由沈夜持有。", 11, "chapter-11-final",
                "FACT_ACTIVE", "第11日", 1);
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("draft-v1-boundary").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(12).outlineNodeCode("ARC_12")
                        .title("买家").summary("追查买家").status("READY").build())
                .arc(new OutlineNodeVO(
                        "ARC_12", "VOL_1", OutlineNodeKindEnum.ARC, 1,
                        "买家", "追查买家", 12, 12, "READY"))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                10, "Legacy 重复调查摘要不应进入 V1 Draft", List.of(), List.of(), null)))
                        .build())
                .memoryContextPack(new MemoryContextPack(
                        MemoryProfile.DRAFT, List.of(), List.of(), List.of(state), List.of(), List.of(),
                        state.estimatedTokenCount()))
                .build();

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);

        System.out.printf(
                "DRAFT V1 历史边界：state=%s, legacyHistoryLeaked=%s%n",
                prompt.contains("[CURRENT_STATE] 第11章：残晶当前由沈夜持有。"),
                prompt.contains("Legacy 重复调查摘要不应进入 V1 Draft"));
        assertThat(prompt)
                .contains("[CURRENT_STATE] 第11章：残晶当前由沈夜持有。")
                .doesNotContain("Legacy 重复调查摘要不应进入 V1 Draft");
    }

    @Test
    void v1CurrentShouldKeepSameSelectedMemoryButWithoutKnownNewControl() {
        MemoryContextItem known = MemoryContextItem.of(
                "known-origin-current", MemoryContextCategory.CONSOLIDATED,
                "同源结论已经确认。", 8);
        MemoryContextItem open = MemoryContextItem.of(
                "open-buyer-current", MemoryContextCategory.OPEN_LOOPS,
                "买家身份仍未查明。", 11);
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("draft-current-boundary").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(12).outlineNodeCode("ARC_12")
                        .title("买家").summary("追查买家").status("READY").build())
                .arc(new OutlineNodeVO(
                        "ARC_12", "VOL_1", OutlineNodeKindEnum.ARC, 1,
                        "买家", "追查买家", 12, 12, "READY"))
                .history(ChapterHistoryVO.builder().build())
                .memoryContextPack(new MemoryContextPack(
                        MemoryProfile.DRAFT, List.of(), List.of(open), List.of(), List.of(known), List.of(), 10))
                .build();

        DraftChapterNode node = new DraftChapterNode(null);
        String currentPrompt = node.buildDraftUserPrompt(
                context, ChapterGenerationVariant.V1_CURRENT);
        String improvedPrompt = node.buildDraftUserPrompt(
                context, ChapterGenerationVariant.V1_IMPROVED);

        System.out.printf(
                "V1 prompt variant：currentSelected=%s, improvedSelected=%s, currentLabels=%s, improvedLabels=%s%n",
                currentPrompt.contains("同源结论已经确认。"),
                improvedPrompt.contains("同源结论已经确认。"),
                currentPrompt.contains("[KNOWN_CONFIRMED]"),
                improvedPrompt.contains("[KNOWN_CONFIRMED]"));
        assertThat(currentPrompt)
                .contains("同源结论已经确认。", "买家身份仍未查明。")
                .doesNotContain("[KNOWN_CONFIRMED]", "[OPEN_QUESTION]");
        assertThat(improvedPrompt)
                .contains("[KNOWN_CONFIRMED]", "[OPEN_QUESTION]");
    }
}
