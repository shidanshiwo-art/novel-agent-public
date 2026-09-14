package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 新 Review Pipeline 的确定性路由骨架。
 *
 * <p>路由只消费结构化结果，不参与 Continuity 或 Quality 判断，
 * 自动返修轮次严格限制为两轮。</p>
 */
@Component
public class ReviewPipelineRouter implements CommandAction<ChapterGraphState> {

    public static final String PREPARE_REVIEW_CONTEXT = "PREPARE_REVIEW_CONTEXT";
    public static final String CONTINUITY_VALIDATION = "CONTINUITY_VALIDATION";
    public static final String QUALITY_REVIEW = "QUALITY_REVIEW";
    public static final String REPAIR_PLAN = "REPAIR_PLAN";
    public static final String REVISION_1 = "REVISION_1";
    public static final String REVISION_2 = "REVISION_2";
    public static final String REGRESSION_CHECK = "REGRESSION_CHECK";
    public static final String FINAL_REGRESSION = "FINAL_REGRESSION";
    public static final String PASS = "PASS";
    public static final String HUMAN = "HUMAN";

    @Override
    public Command apply(ChapterGraphState state, RunnableConfig config) {
        return switch (state.currentNode().orElse(REPAIR_PLAN)) {
            case REPAIR_PLAN -> afterRepairPlan(state);
            case REGRESSION_CHECK -> afterRegressionCheck(state);
            case FINAL_REGRESSION -> afterFinalRegression(state);
            default -> afterRepairPlan(state);
        };
    }

    /** REPAIR_PLAN 后决定直接通过还是进入第 1/2 轮返修。 */
    public Command afterRepairPlan(ChapterGraphState state) {
        RepairPlan plan = state.repairPlan().orElse(RepairPlan.builder().build());
        if (!plan.hasRequiredItems()) {
            return passCommand();
        }

        return switch (state.revisionRound()) {
            case 0 -> revisionCommand(REVISION_1, 1);
            case 1 -> revisionCommand(REVISION_2, 2);
            case 2 -> new Command(FINAL_REGRESSION);
            default -> throw new IllegalStateException("revisionRound 超出允许范围");
        };
    }

    public Command afterRepairPlan(ChapterGraphState state, RunnableConfig config) {
        return afterRepairPlan(state);
    }

    /** 一轮返修后的回归路由。 */
    public Command afterRegressionCheck(ChapterGraphState state) {
        RegressionCheckResult result = state.regressionResult().orElse(null);
        if (result == null) {
            return new Command(HUMAN, Map.of(
                    ChapterGraphKeys.FINAL_DECISION, HUMAN));
        }
        if (result.isResolved() && !result.isNewHardRegression()) {
            return passCommand();
        }
        if (state.revisionRound() < ChapterGraphState.MAX_REVISION_ROUND) {
            return state.revisionRound() == 0
                    ? revisionCommand(REVISION_1, 1)
                    : revisionCommand(REVISION_2, ChapterGraphState.MAX_REVISION_ROUND);
        }
        return new Command(FINAL_REGRESSION);
    }

    public Command afterRegressionCheck(ChapterGraphState state, RunnableConfig config) {
        return afterRegressionCheck(state);
    }

    /** 最终回归后只允许通过或转人工，不返回旧 Full Review。 */
    public Command afterFinalRegression(ChapterGraphState state) {
        RegressionCheckResult result = state.regressionResult().orElse(null);
        if (result != null
                && result.getStatus() == RegressionStatus.RESOLVED
                && !result.isNewHardRegression()) {
            return passCommand();
        }
        return new Command(HUMAN, Map.of(
                ChapterGraphKeys.FINAL_DECISION, HUMAN));
    }

    public Command afterFinalRegression(ChapterGraphState state, RunnableConfig config) {
        return afterFinalRegression(state);
    }

    private Command revisionCommand(String node, int nextRound) {
        if (nextRound > ChapterGraphState.MAX_REVISION_ROUND) {
            throw new IllegalStateException("revisionRound 最大只能到 2");
        }
        return new Command(node, Map.of(ChapterGraphKeys.REVISION_ROUND, nextRound));
    }

    private Command passCommand() {
        return new Command(PASS, Map.of(ChapterGraphKeys.FINAL_DECISION, PASS));
    }
}
