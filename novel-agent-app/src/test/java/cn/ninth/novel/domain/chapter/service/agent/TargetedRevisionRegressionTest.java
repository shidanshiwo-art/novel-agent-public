package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckInput;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairPriority;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairSource;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewPipelineRouter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Targeted Revision、窄输入 Regression 和不可回流路由验收。 */
class TargetedRevisionRegressionTest {

    @Test
    void revisionPromptShouldContainOnlyDraftAndRequiredItems() {
        RecordingModelPort model = new RecordingModelPort("修订后的片段");
        RepairPlanItem required = item("required-1", "补足郝乐从行政楼到宿舍的过渡", "郝乐出现在宿舍");
        RepairPlanItem optional = optionalItem("optional-1", "优化一处措辞", "措辞证据");
        ChapterGraphState state = state(
                Map.of(
                        ChapterGraphKeys.DRAFT, "郝乐出现在宿舍。其他正常段落保持不变。",
                        ChapterGraphKeys.REPAIR_PLAN,
                        RepairPlan.builder().items(List.of(required, optional)).build(),
                        ChapterGraphKeys.REVISION_ROUND, 1
                ));

        Map<String, Object> update = new TargetedRevisionNode(model).apply(state);

        assertThat(update.get(ChapterGraphKeys.DRAFT)).isEqualTo("修订后的片段");
        assertThat(model.userPrompt)
                .contains("郝乐出现在宿舍", "补足郝乐从行政楼到宿舍的过渡")
                .doesNotContain("优化一处措辞", "措辞证据", "整体优化全文");
        assertThat(model.systemPrompt)
                .contains("最小正文范围", "禁止重写整章", "禁止主动寻找新的优化点");
        assertThat(update.get(ChapterGraphKeys.REVISION_BEFORE_AFFECTED_TEXT))
                .isEqualTo(Map.of("required-1", "郝乐出现在宿舍"));
        System.out.printf("Targeted Revision 窄输入：promptChars=%d，requiredOnly=true，draftUpdated=%s%n",
                model.userPrompt.length(),
                update.get(ChapterGraphKeys.DRAFT));
    }

    @Test
    void revisionTwoShouldUseOnlyRemainingRepairPlan() {
        RecordingModelPort model = new RecordingModelPort("第二轮修订正文");
        RepairPlanItem first = item("required-1", "修复第一个问题", "第一个证据");
        RepairPlanItem remaining = item("required-2", "修复剩余位置问题", "剩余证据");
        ChapterGraphState state = state(Map.of(
                ChapterGraphKeys.DRAFT, "剩余问题正文",
                ChapterGraphKeys.REPAIR_PLAN,
                RepairPlan.builder().items(List.of(first, remaining)).build(),
                ChapterGraphKeys.REMAINING_REPAIR_PLAN,
                RepairPlan.builder().items(List.of(remaining)).build(),
                ChapterGraphKeys.REVISION_ROUND, 2
        ));

        new TargetedRevisionNode(model).apply(state);

        assertThat(model.userPrompt).contains("修复剩余位置问题", "剩余证据")
                .doesNotContain("修复第一个问题", "第一个证据");
        System.out.printf("Revision #2 remainingRepairPlan：containsRemaining=true，containsOriginalFirst=false%n");
    }

    @Test
    void regressionInputShouldContainOnlyRequiredBeforeAfterAndRelevantEvidence() {
        RepairPlanItem required = item("required-1", "修复位置衔接", "郝乐出现在宿舍");
        RepairPlanItem optional = optionalItem("optional-1", "优化措辞", "措辞证据");
        CapturingVerifier verifier = new CapturingVerifier(RegressionCheckResult.builder()
                .repairId("required-1")
                .status(RegressionStatus.RESOLVED)
                .newHardRegression(false)
                .reason("已解决")
                .build());
        ChapterGraphState state = state(Map.of(
                ChapterGraphKeys.DRAFT, "郝乐出现在宿舍",
                ChapterGraphKeys.REPAIR_PLAN,
                RepairPlan.builder().items(List.of(required, optional)).build(),
                ChapterGraphKeys.REVISION_BEFORE_AFFECTED_TEXT,
                Map.of("required-1", "郝乐此前在行政楼"),
                ChapterGraphKeys.CONTINUITY_FINDINGS,
                List.of(ContinuityFinding.builder()
                        .type("CHARACTER_STATE")
                        .entity("郝乐")
                        .severity(ContinuitySeverity.HARD)
                        .currentEvidence("郝乐出现在宿舍")
                        .historicalEvidence("郝乐此前在行政楼")
                        .build())
        ));

        Map<String, Object> update = new RegressionCheckNode(verifier).apply(state);

        RegressionCheckInput input = verifier.input;
        assertThat(input.originalRepairPlan().getItems()).containsExactly(required);
        assertThat(input.beforeRevisionAffectedText())
                .containsEntry("required-1", "郝乐此前在行政楼");
        assertThat(input.afterRevisionAffectedText())
                .containsEntry("required-1", "郝乐出现在宿舍");
        assertThat(input.relevantContinuityEvidence())
                .containsExactly("郝乐出现在宿舍", "郝乐此前在行政楼");
        assertThat(update.get(ChapterGraphKeys.REMAINING_REPAIR_PLAN))
                .isEqualTo(RepairPlan.builder().build());
        System.out.printf("Regression 窄输入：required=%d，before=%s，after=%s，evidence=%s%n",
                input.originalRepairPlan().getItems().size(),
                input.beforeRevisionAffectedText(),
                input.afterRevisionAffectedText(),
                input.relevantContinuityEvidence());
    }

    @Test
    void resolvedWithoutNewHardRegressionShouldPass() {
        CommandResult result = route(1, RegressionCheckResult.builder()
                .repairId("required-1")
                .status(RegressionStatus.RESOLVED)
                .newHardRegression(false)
                .build());

        assertThat(result.gotoNode()).isEqualTo(ReviewPipelineRouter.PASS);
        System.out.printf("Regression resolved 路由：newHard=false，goto=%s%n", result.gotoNode());
    }

    @Test
    void unresolvedOrNewHardRegressionShouldGoToRevisionTwoNotReviewNodes() {
        CommandResult unresolved = route(1, RegressionCheckResult.builder()
                .repairId("required-1")
                .status(RegressionStatus.UNRESOLVED)
                .newHardRegression(false)
                .build());
        CommandResult newHard = route(1, RegressionCheckResult.builder()
                .repairId("required-1")
                .status(RegressionStatus.RESOLVED)
                .newHardRegression(true)
                .build());

        assertThat(unresolved.gotoNode()).isEqualTo(ReviewPipelineRouter.REVISION_2);
        assertThat(newHard.gotoNode()).isEqualTo(ReviewPipelineRouter.REVISION_2);
        assertThat(List.of(unresolved.gotoNode(), newHard.gotoNode()))
                .doesNotContain(ReviewPipelineRouter.QUALITY_REVIEW,
                        ReviewPipelineRouter.CONTINUITY_VALIDATION);
        System.out.printf("Regression 不回流：unresolved→%s，newHard→%s%n",
                unresolved.gotoNode(), newHard.gotoNode());
    }

    @Test
    void finalRegressionShouldOnlyPassOrRequireHuman() {
        RepairPlanItem remaining = item("required-1", "仍需修复", "剩余证据");
        ChapterGraphState unresolved = state(Map.of(
                ChapterGraphKeys.REVISION_ROUND, 2,
                ChapterGraphKeys.REMAINING_REPAIR_PLAN,
                RepairPlan.builder().items(List.of(remaining)).build(),
                ChapterGraphKeys.DRAFT, "剩余问题正文"
        ));
        ChapterGraphState resolved = state(Map.of(
                ChapterGraphKeys.REVISION_ROUND, 2,
                ChapterGraphKeys.REMAINING_REPAIR_PLAN,
                RepairPlan.builder().build(),
                ChapterGraphKeys.DRAFT, "正文"
        ));

        CommandResult unresolvedRoute = routeFinal(unresolved);
        CommandResult resolvedRoute = routeFinal(resolved);

        assertThat(unresolvedRoute.gotoNode()).isEqualTo(ReviewPipelineRouter.HUMAN);
        assertThat(resolvedRoute.gotoNode()).isEqualTo(ReviewPipelineRouter.PASS);
        assertThat(List.of(unresolvedRoute.gotoNode(), resolvedRoute.gotoNode()))
                .doesNotContain(ReviewPipelineRouter.QUALITY_REVIEW,
                        ReviewPipelineRouter.CONTINUITY_VALIDATION);
        System.out.printf("Final Regression 终点：remaining→%s，empty→%s%n",
                unresolvedRoute.gotoNode(), resolvedRoute.gotoNode());
    }

    private CommandResult route(int revisionRound, RegressionCheckResult result) {
        return new CommandResult(new ReviewPipelineRouter().afterRegressionCheck(state(Map.of(
                ChapterGraphKeys.REVISION_ROUND, revisionRound,
                ChapterGraphKeys.REGRESSION_RESULT, result
        ))).gotoNode());
    }

    private CommandResult routeFinal(ChapterGraphState state) {
        Map<String, Object> update = new FinalRegressionNode().apply(state);
        return new CommandResult(new ReviewPipelineRouter().afterFinalRegression(
                stateWithFinalResult(state, update)).gotoNode());
    }

    private ChapterGraphState stateWithFinalResult(
            ChapterGraphState state,
            Map<String, Object> update
    ) {
        Map<String, Object> values = new java.util.HashMap<>(state.data());
        values.putAll(update);
        return state(values);
    }

    private ChapterGraphState state(Map<String, Object> values) {
        return new ChapterGraphState(values);
    }

    private RepairPlanItem item(String id, String problem, String evidence) {
        return item(id, problem, evidence, RepairPriority.REQUIRED);
    }

    private RepairPlanItem optionalItem(String id, String problem, String evidence) {
        return item(id, problem, evidence, RepairPriority.OPTIONAL);
    }

    private RepairPlanItem item(
            String id,
            String problem,
            String evidence,
            RepairPriority priority
    ) {
        return RepairPlanItem.builder()
                .id(id)
                .source(RepairSource.QUALITY)
                .problem(problem)
                .evidence(evidence)
                .affectedRange(evidence)
                .repairIntent(problem)
                .priority(priority)
                .build();
    }

    private record CommandResult(String gotoNode) {
    }

    private static final class CapturingVerifier implements RegressionVerifier {
        private final RegressionCheckResult result;
        private RegressionCheckInput input;

        private CapturingVerifier(RegressionCheckResult result) {
            this.result = result;
        }

        @Override
        public RegressionCheckResult verify(RegressionCheckInput input) {
            this.input = input;
            return result;
        }
    }

    private static final class RecordingModelPort implements IChapterModelPort {
        private final String revisedDraft;
        private String systemPrompt;
        private String userPrompt;

        private RecordingModelPort(String revisedDraft) {
            this.revisedDraft = revisedDraft;
        }

        @Override
        public Flux<String> stream(String systemPrompt, String userPrompt) {
            return Flux.empty();
        }

        @Override
        public String call(String systemPrompt, String userPrompt) {
            return revisedDraft;
        }

        @Override
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return responseType.cast(revisedDraft);
        }

        @Override
        public ChapterModelResponse<String> callWithUsage(
                String systemPrompt,
                String userPrompt,
                String stage,
                int attempt
        ) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return new ChapterModelResponse<>(revisedDraft, null, null);
        }
    }
}
