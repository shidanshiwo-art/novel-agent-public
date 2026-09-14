package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckInput;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;

import java.util.ArrayList;
import java.util.List;

/** Regression 的窄范围判定端口，只回答原始返修是否解决及是否出现新 HARD 回归。 */
@FunctionalInterface
public interface RegressionVerifier {

    RegressionCheckResult verify(RegressionCheckInput input);

    /** 离线默认实现：仅以受影响片段是否发生变化作为保守确定性信号。 */
    static RegressionVerifier deterministic() {
        return input -> {
            List<String> unresolved = new ArrayList<>();
            for (RepairPlanItem item : input.originalRepairPlan().requiredItems()) {
                String before = input.beforeRevisionAffectedText().get(item.getId());
                String after = input.afterRevisionAffectedText().get(item.getId());
                if (before == null || after == null || before.equals(after)
                        || after.isBlank()) {
                    unresolved.add(item.getId());
                }
            }
            String repairId = unresolved.isEmpty()
                    ? input.originalRepairPlan().requiredItems().stream()
                    .map(RepairPlanItem::getId).findFirst().orElse(null)
                    : unresolved.get(0);
            return RegressionCheckResult.builder()
                    .repairId(repairId)
                    .status(unresolved.isEmpty()
                            ? RegressionStatus.RESOLVED : RegressionStatus.UNRESOLVED)
                    .unresolvedRepairIds(unresolved)
                    .newHardRegression(false)
                    .reason(unresolved.isEmpty()
                            ? "所有原始 REQUIRED 返修项的受影响片段已发生变化"
                            : "仍有 REQUIRED 返修项的受影响片段未确认解决")
                    .build();
        };
    }

    /** 离线占位实现：没有前后片段时不擅自判定已解决。 */
    static RegressionVerifier noOp() {
        return input -> RegressionCheckResult.builder()
                .repairId(input.originalRepairPlan().requiredItems().stream()
                        .map(RepairPlanItem::getId).findFirst().orElse(null))
                .status(RegressionStatus.UNRESOLVED)
                .unresolvedRepairIds(input.originalRepairPlan().requiredItems().stream()
                        .map(RepairPlanItem::getId).toList())
                .newHardRegression(false)
                .reason("未配置 Regression verifier")
                .build();
    }
}
