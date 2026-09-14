package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckInput;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 新 Pipeline 的窄范围回归检查节点。
 *
 * <p>Q1 只检查原始 REQUIRED 项是否解决，Q2 只检查相关连续性证据下是否出现新的
 * HARD regression；不会重新进入 Quality Review 或 Continuity Validation。</p>
 */
@Component
public class RegressionCheckNode implements NodeAction<ChapterGraphState> {

    public static final String NODE = "REGRESSION_CHECK";
    private final RegressionVerifier verifier;

    /** 离线默认实现，不把未发生有效片段变化的返修误报为已解决。 */
    public RegressionCheckNode() {
        this(RegressionVerifier.deterministic());
    }

    public RegressionCheckNode(RegressionVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier 不能为空");
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        RepairPlan originalPlan = state.repairPlan().orElse(RepairPlan.builder().build());
        RegressionCheckInput input = buildInput(state, originalPlan);
        RegressionCheckResult result = verifier.verify(input);
        if (result == null || result.getStatus() == null) {
            result = unresolved(originalPlan, "Regression verifier 未返回结果");
        }
        RepairPlan remainingPlan = remainingPlan(originalPlan, result);
        return Map.of(
                ChapterGraphKeys.REGRESSION_RESULT, result,
                ChapterGraphKeys.REMAINING_REPAIR_PLAN, remainingPlan,
                ChapterGraphKeys.CURRENT_NODE, NODE,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE)
        );
    }

    /** 供 Final Regression 复用同一窄输入构造，不携带完整章节上下文。 */
    static RegressionCheckInput buildInput(
            ChapterGraphState state,
            RepairPlan originalPlan
    ) {
        List<RepairPlanItem> requiredItems = originalPlan == null
                ? List.of() : originalPlan.requiredItems();
        Map<String, String> before = state.revisionBeforeAffectedText();
        Map<String, String> after = TargetedRevisionNode.affectedText(
                state.draft().orElse(""), requiredItems);
        List<String> continuityEvidence = relevantContinuityEvidence(
                state.continuityFindings(), requiredItems);
        return new RegressionCheckInput(
                RepairPlan.builder().items(requiredItems).build(),
                before,
                after,
                continuityEvidence
        );
    }

    private static List<String> relevantContinuityEvidence(
            List<ContinuityFinding> findings,
            List<RepairPlanItem> requiredItems
    ) {
        List<String> evidence = new ArrayList<>();
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        for (ContinuityFinding finding : findings) {
            if (finding == null || !matches(finding, requiredItems)) {
                continue;
            }
            addIfPresent(evidence, finding.getCurrentEvidence());
            addIfPresent(evidence, finding.getHistoricalEvidence());
        }
        return List.copyOf(evidence);
    }

    private static boolean matches(
            ContinuityFinding finding,
            List<RepairPlanItem> requiredItems
    ) {
        String entity = value(finding.getEntity());
        String currentEvidence = value(finding.getCurrentEvidence());
        for (RepairPlanItem item : requiredItems) {
            String itemText = value(item.getProblem()) + value(item.getEvidence())
                    + value(item.getAffectedRange()) + value(item.getRepairIntent());
            if ((!entity.isBlank() && itemText.contains(entity))
                    || (!currentEvidence.isBlank() && itemText.contains(currentEvidence))) {
                return true;
            }
        }
        return false;
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    private RepairPlan remainingPlan(
            RepairPlan originalPlan,
            RegressionCheckResult result
    ) {
        List<RepairPlanItem> requiredItems = originalPlan == null
                ? List.of() : originalPlan.requiredItems();
        List<String> unresolvedIds = new ArrayList<>(result.getUnresolvedRepairIds());
        if (result.isUnresolved() && result.getRepairId() != null
                && !unresolvedIds.contains(result.getRepairId())) {
            unresolvedIds.add(result.getRepairId());
        }
        if (result.isUnresolved() && unresolvedIds.isEmpty()) {
            unresolvedIds.addAll(requiredItems.stream().map(RepairPlanItem::getId).toList());
        }
        // 新 HARD regression 不是原始返修项，必须保留原始 REQUIRED 计划供第二轮处理。
        if (result.isNewHardRegression() && unresolvedIds.isEmpty()) {
            unresolvedIds.addAll(requiredItems.stream().map(RepairPlanItem::getId).toList());
        }
        List<RepairPlanItem> remaining = requiredItems.stream()
                .filter(item -> unresolvedIds.contains(item.getId()))
                .toList();
        return RepairPlan.builder().items(remaining).build();
    }

    private RegressionCheckResult unresolved(RepairPlan plan, String reason) {
        List<String> ids = plan == null ? List.of() : plan.requiredItems().stream()
                .map(RepairPlanItem::getId).toList();
        return RegressionCheckResult.builder()
                .repairId(ids.stream().findFirst().orElse(null))
                .status(RegressionStatus.UNRESOLVED)
                .unresolvedRepairIds(ids)
                .newHardRegression(false)
                .reason(reason)
                .build();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
