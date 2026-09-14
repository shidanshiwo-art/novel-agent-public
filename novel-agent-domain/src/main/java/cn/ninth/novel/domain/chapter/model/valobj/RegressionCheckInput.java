package cn.ninth.novel.domain.chapter.model.valobj;

import java.util.List;
import java.util.Map;

/**
 * Regression 专用窄输入。
 *
 * <p>这里只携带原始必修返修项、受影响片段的前后版本和相关连续性证据，
 * 不携带完整章纲、完整正文、全量 Memory 或 Quality Review 上下文。</p>
 */
public record RegressionCheckInput(
        RepairPlan originalRepairPlan,
        Map<String, String> beforeRevisionAffectedText,
        Map<String, String> afterRevisionAffectedText,
        List<String> relevantContinuityEvidence
) {

    public RegressionCheckInput {
        originalRepairPlan = originalRepairPlan == null
                ? RepairPlan.builder().build() : originalRepairPlan;
        beforeRevisionAffectedText = beforeRevisionAffectedText == null
                ? Map.of() : Map.copyOf(beforeRevisionAffectedText);
        afterRevisionAffectedText = afterRevisionAffectedText == null
                ? Map.of() : Map.copyOf(afterRevisionAffectedText);
        relevantContinuityEvidence = relevantContinuityEvidence == null
                ? List.of() : List.copyOf(relevantContinuityEvidence);
    }
}
