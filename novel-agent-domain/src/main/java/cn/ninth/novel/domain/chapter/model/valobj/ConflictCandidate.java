package cn.ninth.novel.domain.chapter.model.valobj;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase A 由结构化 Memory/State 产生的连续性冲突候选。
 *
 * <p>候选不是最终问题结论；只有经过 Phase B 语义复核后，才会形成
 * {@link ContinuityFinding}。类型严格限制在五类连续性检查范围内。</p>
 */
public record ConflictCandidate(
        String type,
        String entity,
        String currentEvidence,
        String historicalEvidence,
        List<String> relevantEvents,
        String currentStoryTime,
        Integer sourceChapter,
        boolean canonicalHistoricalState,
        String reason
) {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "CHARACTER_STATE", "ITEM_STATE", "ABILITY_RULE", "KNOWLEDGE", "TIMELINE");

    public ConflictCandidate {
        type = required(type, "type");
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的连续性候选类型: " + type);
        }
        entity = required(entity, "entity");
        currentEvidence = required(currentEvidence, "currentEvidence");
        historicalEvidence = required(historicalEvidence, "historicalEvidence");
        relevantEvents = relevantEvents == null ? List.of() : List.copyOf(relevantEvents);
        currentStoryTime = optional(currentStoryTime);
        if (sourceChapter != null && sourceChapter < 0) {
            throw new IllegalArgumentException("sourceChapter 不能小于 0");
        }
        reason = optional(reason);
    }

    public boolean isSupportedType() {
        return SUPPORTED_TYPES.contains(type);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
