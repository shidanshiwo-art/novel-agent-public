package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.enums.QualitySeverity;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 新 Pipeline 的质量审核边界。
 *
 * <p>节点将质量审核范围收敛到六项，并校验严重程度只能是
 * MUST_FIX/OPTIONAL。没有注入 reviewer 的直接构造入口保持离线 deterministic 行为。</p>
 */
@Component
public class QualityReviewNode implements NodeAction<ChapterGraphState> {

    public static final String NODE = "QUALITY_REVIEW";
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "OUTLINE_COMPLETION",
            "CAUSAL_LOGIC",
            "CHARACTER_MOTIVATION",
            "PLOT_PROGRESSION",
            "REDUNDANT_INVESTIGATION",
            "PROSE_NATURALNESS"
    );

    private final QualityReviewer reviewer;

    /** 兼容离线领域测试；不调用模型。 */
    public QualityReviewNode() {
        this.reviewer = null;
    }

    @Autowired
    public QualityReviewNode(ModelQualityReviewer reviewer) {
        this.reviewer = reviewer;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        ReviewContext reviewContext = state.reviewContext().orElse(null);
        List<QualityFinding> findings = reviewer == null || reviewContext == null
                ? state.qualityFindings()
                : reviewer.review(reviewContext);
        findings = normalizeAndValidate(findings);
        return Map.of(
                ChapterGraphKeys.QUALITY_FINDINGS, findings,
                ChapterGraphKeys.CURRENT_NODE, NODE,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE)
        );
    }

    /**
     * 统一质量问题类型命名，并拒绝六项之外的 reviewer 输出。
     * 允许自然语言 label 作为输入，但写回 State 时始终使用稳定的英文 key。
     */
    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("QualityFinding.type 不能为空");
        }
        String normalized = type.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "OUTLINE_COMPLETION", "OUTLINE_MISS" -> "OUTLINE_COMPLETION";
            case "CAUSAL_LOGIC" -> "CAUSAL_LOGIC";
            case "CHARACTER_MOTIVATION" -> "CHARACTER_MOTIVATION";
            case "PLOT_PROGRESSION" -> "PLOT_PROGRESSION";
            case "REDUNDANT_INVESTIGATION", "REDUNDANT_PLOT" ->
                    "REDUNDANT_INVESTIGATION";
            case "PROSE_NATURALNESS", "PROSE" -> "PROSE_NATURALNESS";
            default -> normalized;
        };
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的质量审核类型: " + type);
        }
        return normalized;
    }

    private List<QualityFinding> normalizeAndValidate(List<QualityFinding> findings) {
        List<QualityFinding> normalized = new ArrayList<>();
        if (findings == null) {
            return List.of();
        }
        for (QualityFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            if (finding.getSeverity() == null
                    || (finding.getSeverity() != QualitySeverity.MUST_FIX
                    && finding.getSeverity() != QualitySeverity.OPTIONAL)) {
                throw new IllegalArgumentException(
                        "QualityFinding.severity 只能是 MUST_FIX 或 OPTIONAL");
            }
            normalized.add(QualityFinding.builder()
                    .type(normalizeType(finding.getType()))
                    .evidence(finding.getEvidence())
                    .problem(finding.getProblem())
                    .repairIntent(finding.getRepairIntent())
                    .affectedRange(finding.getAffectedRange())
                    .severity(finding.getSeverity())
                    .build());
        }
        return List.copyOf(normalized);
    }
}
