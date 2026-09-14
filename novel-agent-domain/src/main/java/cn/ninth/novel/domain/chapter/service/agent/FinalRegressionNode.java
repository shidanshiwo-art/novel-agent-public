package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckInput;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Revision #2 后的最终窄范围 Regression。 */
@Component
public class FinalRegressionNode implements NodeAction<ChapterGraphState> {

    public static final String NODE = "FINAL_REGRESSION";
    private final RegressionVerifier verifier;

    public FinalRegressionNode() {
        this(RegressionVerifier.deterministic());
    }

    public FinalRegressionNode(RegressionVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier 不能为空");
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        RepairPlan remainingPlan = state.remainingRepairPlan()
                .orElse(RepairPlan.builder().build());
        RegressionCheckResult result;
        if (!remainingPlan.hasRequiredItems()) {
            result = RegressionCheckResult.builder()
                    .status(RegressionStatus.RESOLVED)
                    .newHardRegression(false)
                    .reason("没有剩余 REQUIRED 返修项")
                    .build();
        } else {
            RegressionCheckInput input = RegressionCheckNode.buildInput(state, remainingPlan);
            result = verifier.verify(input);
            if (result == null || result.getStatus() == null) {
                result = RegressionCheckResult.builder()
                        .repairId(remainingPlan.requiredItems().get(0).getId())
                        .status(RegressionStatus.UNRESOLVED)
                        .unresolvedRepairIds(remainingPlan.requiredItems().stream()
                                .map(RepairPlanItem::getId).toList())
                        .newHardRegression(false)
                        .reason("Final Regression verifier 未返回结果")
                        .build();
            }
        }
        return Map.of(
                ChapterGraphKeys.REGRESSION_RESULT, result,
                ChapterGraphKeys.REMAINING_REPAIR_PLAN,
                result.isResolved() && !result.isNewHardRegression()
                        ? RepairPlan.builder().build() : remainingPlan,
                ChapterGraphKeys.CURRENT_NODE, NODE,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE)
        );
    }
}
