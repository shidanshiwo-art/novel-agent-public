package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity;
import cn.ninth.novel.domain.chapter.model.valobj.enums.QualitySeverity;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairPriority;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairSource;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import cn.ninth.novel.domain.chapter.service.agent.ContinuityValidationNode;
import cn.ninth.novel.domain.chapter.service.agent.FinalRegressionNode;
import cn.ninth.novel.domain.chapter.service.agent.QualityReviewNode;
import cn.ninth.novel.domain.chapter.service.agent.RepairPlanNode;
import cn.ninth.novel.domain.chapter.service.agent.RegressionCheckNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviewPipelineRevisionNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviewPreparationNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.Command;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.ChapterGraphStateSerializer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** Review Pipeline 数据模型、路由上限和空节点工作流骨架测试。 */
class ReviewPipelineTest {

    @Test
    void shouldStartPipelineAndPassWhenThereIsNoRequiredRepair() throws Exception {
        NodeOutput<ChapterGraphState> output = graph().invokeFinal(
                GraphInput.args(Map.of(
                        ChapterGraphKeys.WORKFLOW_ID, "review-pipeline-no-required",
                        ChapterGraphKeys.DRAFT, "待审核正文"
                )),
                RunnableConfig.builder().threadId("review-pipeline-no-required").build()
        ).orElseThrow();

        assertThat(output.isEND()).isTrue();
        assertThat(output.state().finalDecision()).contains(ReviewPipelineRouter.PASS);
        assertThat(output.state().completedStages())
                .containsExactly(
                        ReviewPreparationNode.NODE,
                        ContinuityValidationNode.NODE,
                        QualityReviewNode.NODE,
                        RepairPlanNode.NODE
                );
        System.out.printf(
                "Review Pipeline 启动检查：无 REQUIRED，completed=%s，decision=%s%n",
                output.state().completedStages(),
                output.state().finalDecision().orElse(null)
        );
    }

    @Test
    void shouldRouteRequiredRepairToRevisionOne() {
        ChapterGraphState state = requiredState(0);

        Command command = new ReviewPipelineRouter()
                .afterRepairPlan(state);

        assertThat(command.gotoNode()).isEqualTo(ReviewPipelineRouter.REVISION_1);
        assertThat(command.update())
                .containsEntry(ChapterGraphKeys.REVISION_ROUND, 1);
        System.out.printf(
                "REPAIR_PLAN 路由检查：REQUIRED=%s，goto=%s，nextRound=%s%n",
                state.repairPlan().orElseThrow().hasRequiredItems(),
                command.gotoNode(),
                command.update().get(ChapterGraphKeys.REVISION_ROUND)
        );
    }

    @Test
    void shouldSerializeAndRestoreNewPipelineState() {
        ReviewContext reviewContext = ReviewContext.builder()
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(3)
                        .title("第三章")
                        .summary("推进冲突")
                        .build())
                .currentDraft("当前正文")
                .continuityContext(Map.of("history", "历史证据"))
                .qualityContext(Map.of("scope", "章节质量"))
                .build();
        ContinuityFinding continuityFinding = ContinuityFinding.builder()
                .type("ITEM_STATE")
                .entity("钥匙")
                .severity(ContinuitySeverity.HARD)
                .currentEvidence("当前证据")
                .historicalEvidence("历史证据")
                .sourceChapter(2)
                .reason("位置冲突")
                .confidence(0.95)
                .build();
        QualityFinding qualityFinding = QualityFinding.builder()
                .type("PACING")
                .evidence("质量证据")
                .problem("节奏过快")
                .repairIntent("补足转折")
                .affectedRange("第 2 段")
                .severity(QualitySeverity.OPTIONAL)
                .build();
        RepairPlanItem planItem = RepairPlanItem.builder()
                .id("continuity-1")
                .source(RepairSource.CONTINUITY)
                .problem("位置冲突")
                .evidence("当前证据")
                .affectedRange("第 1 段")
                .repairIntent("修复位置")
                .priority(RepairPriority.REQUIRED)
                .build();
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.REVIEW_SESSION_ID, "review-session-1",
                ChapterGraphKeys.REVIEW_CONTEXT, reviewContext,
                ChapterGraphKeys.CONTINUITY_FINDINGS, List.of(continuityFinding),
                ChapterGraphKeys.QUALITY_FINDINGS, List.of(qualityFinding),
                ChapterGraphKeys.REPAIR_PLAN, RepairPlan.builder()
                        .items(List.of(planItem)).build(),
                ChapterGraphKeys.REVISION_ROUND, 2,
                ChapterGraphKeys.REGRESSION_RESULT, RegressionCheckResult.builder()
                        .repairId("continuity-1")
                        .status(RegressionStatus.RESOLVED)
                        .reason("已修复")
                        .build(),
                ChapterGraphKeys.FINAL_DECISION, ReviewPipelineRouter.PASS
        ));

        ChapterCheckpointStateCodec codec = new ChapterCheckpointStateCodec(new ObjectMapper());
        Map<String, Object> restored = codec.decode(codec.encode(state.data()));
        ChapterGraphState restoredState = new ChapterGraphState(restored);

        assertThat(restoredState.reviewSessionId()).contains("review-session-1");
        assertThat(restoredState.reviewContext()).hasValueSatisfying(value ->
                assertThat(value.getCurrentDraft()).isEqualTo("当前正文"));
        assertThat(restoredState.continuityFindings()).containsExactly(continuityFinding);
        assertThat(restoredState.qualityFindings()).containsExactly(qualityFinding);
        assertThat(restoredState.repairPlan()).hasValueSatisfying(value ->
                assertThat(value.requiredItems()).containsExactly(planItem));
        assertThat(restoredState.revisionRound()).isEqualTo(2);
        assertThat(restoredState.regressionResult()).hasValueSatisfying(value ->
                assertThat(value.getStatus()).isEqualTo(RegressionStatus.RESOLVED));
        assertThat(restoredState.finalDecision()).contains(ReviewPipelineRouter.PASS);
        System.out.printf(
                "Review Pipeline State checkpoint 检查：session=%s，findings=%d/%d，round=%d，decision=%s%n",
                restoredState.reviewSessionId().orElse(null),
                restoredState.continuityFindings().size(),
                restoredState.qualityFindings().size(),
                restoredState.revisionRound(),
                restoredState.finalDecision().orElse(null)
        );
    }

    @Test
    void shouldKeepRevisionRoundAtMostTwo() {
        ReviewPipelineRouter router = new ReviewPipelineRouter();

        Command second = router.afterRepairPlan(requiredState(1));
        assertThat(second.gotoNode()).isEqualTo(ReviewPipelineRouter.REVISION_2);
        assertThat(second.update()).containsEntry(ChapterGraphKeys.REVISION_ROUND, 2);

        ChapterGraphState roundTwo = requiredState(2);
        assertThat(router.afterRepairPlan(roundTwo).gotoNode())
                .isEqualTo(ReviewPipelineRouter.FINAL_REGRESSION);
        System.out.println("revisionRound 上限检查：0 → 1 → 2，round=2 后不再生成第 3 轮");
    }

    @Test
    void shouldNotReturnToFullReviewAfterRevisionTwo() {
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.CURRENT_NODE, ReviewPipelineRouter.FINAL_REGRESSION,
                ChapterGraphKeys.REVISION_ROUND, 2,
                ChapterGraphKeys.REGRESSION_RESULT,
                RegressionCheckResult.builder()
                        .repairId("continuity-1")
                        .status(RegressionStatus.UNRESOLVED)
                        .newHardRegression(true)
                        .reason("测试用新硬回归")
                        .build()
        ));

        Command command = new ReviewPipelineRouter().afterFinalRegression(state);

        assertThat(command.gotoNode()).isEqualTo(ReviewPipelineRouter.HUMAN);
        assertThat(command.gotoNode()).isNotEqualTo("REVIEW");
        System.out.printf(
                "Revision #2 后路由检查：round=%d，newHardRegression=%s，goto=%s%n",
                state.revisionRound(),
                state.regressionResult().orElseThrow().isNewHardRegression(),
                command.gotoNode()
        );
    }

    private ChapterGraphState requiredState(int revisionRound) {
        Map<String, Object> planUpdate = new RepairPlanNode().apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTINUITY_FINDINGS,
                List.of(ContinuityFinding.builder()
                        .type("TIMELINE")
                        .severity(ContinuitySeverity.HARD)
                        .reason("时间顺序冲突")
                        .build())
        )));
        RepairPlan plan = (RepairPlan) planUpdate.get(ChapterGraphKeys.REPAIR_PLAN);
        return new ChapterGraphState(Map.of(
                ChapterGraphKeys.REPAIR_PLAN, plan,
                ChapterGraphKeys.REVISION_ROUND, revisionRound
        ));
    }

    private CompiledGraph<ChapterGraphState> graph() throws GraphStateException {
        ReviewPreparationNode prepare = new ReviewPreparationNode();
        ContinuityValidationNode continuity = new ContinuityValidationNode();
        QualityReviewNode quality = new QualityReviewNode();
        RepairPlanNode plan = new RepairPlanNode();
        ReviewPipelineRevisionNode revision = new ReviewPipelineRevisionNode();
        RegressionCheckNode regression = new RegressionCheckNode();
        FinalRegressionNode finalRegression = new FinalRegressionNode();
        ReviewPipelineRouter router = new ReviewPipelineRouter();
        return new StateGraph<>(
                ChapterGraphState.SCHEMA,
                new ChapterGraphStateSerializer(
                        new ChapterCheckpointStateCodec(new ObjectMapper()))
        )
                .addNode(ReviewPreparationNode.NODE, node_async(prepare::apply))
                .addNode(ContinuityValidationNode.NODE, node_async(continuity::apply))
                .addNode(QualityReviewNode.NODE, node_async(quality::apply))
                .addNode(RepairPlanNode.NODE, node_async(plan::apply))
                .addNode(ReviewPipelineRouter.REVISION_1, node_async(revision::apply))
                .addNode(ReviewPipelineRouter.REVISION_2, node_async(revision::apply))
                .addNode(RegressionCheckNode.NODE, node_async(regression::apply))
                .addNode(FinalRegressionNode.NODE, node_async(finalRegression::apply))
                .addEdge(START, ReviewPreparationNode.NODE)
                .addEdge(ReviewPreparationNode.NODE, ContinuityValidationNode.NODE)
                .addEdge(ContinuityValidationNode.NODE, QualityReviewNode.NODE)
                .addEdge(QualityReviewNode.NODE, RepairPlanNode.NODE)
                .addConditionalEdges(RepairPlanNode.NODE, command_async(router::afterRepairPlan), Map.of(
                        ReviewPipelineRouter.PASS, END,
                        ReviewPipelineRouter.REVISION_1, ReviewPipelineRouter.REVISION_1,
                        ReviewPipelineRouter.REVISION_2, ReviewPipelineRouter.REVISION_2,
                        ReviewPipelineRouter.FINAL_REGRESSION, FinalRegressionNode.NODE,
                        ReviewPipelineRouter.HUMAN, END
                ))
                .addEdge(ReviewPipelineRouter.REVISION_1, RegressionCheckNode.NODE)
                .addEdge(ReviewPipelineRouter.REVISION_2, FinalRegressionNode.NODE)
                .addConditionalEdges(RegressionCheckNode.NODE, command_async(router::afterRegressionCheck), Map.of(
                        ReviewPipelineRouter.PASS, END,
                        ReviewPipelineRouter.REVISION_1, ReviewPipelineRouter.REVISION_1,
                        ReviewPipelineRouter.REVISION_2, ReviewPipelineRouter.REVISION_2,
                        ReviewPipelineRouter.FINAL_REGRESSION, FinalRegressionNode.NODE,
                        ReviewPipelineRouter.HUMAN, END
                ))
                .addConditionalEdges(FinalRegressionNode.NODE, command_async(router::afterFinalRegression), Map.of(
                        ReviewPipelineRouter.PASS, END,
                        ReviewPipelineRouter.HUMAN, END
                ))
                .compile(CompileConfig.builder().releaseThread(true).build());
    }
}
