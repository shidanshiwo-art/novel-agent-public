package cn.ninth.novel.domain.memory.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One retrieval observation. It is diagnostic data only and has no effect on retrieval decisions.
 */
public record MemoryRetrievalSample(
        MemoryQueryProfile profile,
        int chapterCount,
        long contextTokenCount,
        int contextItemCount,
        List<String> selectedItems,
        List<String> trimmedItems,
        long candidateScanCount,
        long retrievalLatencyMillis,
        boolean legacyFallbackHit,
        boolean legacyMiss,
        boolean legacyComparable,
        Map<MemoryRetrievalCategory, Integer> categoryUsage
) {

    public MemoryRetrievalSample {
        profile = Objects.requireNonNull(profile, "profile 不能为空");
        if (chapterCount < 1) {
            throw new IllegalArgumentException("chapterCount 必须为正数");
        }
        if (contextTokenCount < 0) {
            throw new IllegalArgumentException("contextTokenCount 不能小于 0");
        }
        if (contextItemCount < 0) {
            throw new IllegalArgumentException("contextItemCount 不能小于 0");
        }
        selectedItems = immutableIds(selectedItems, "selectedItems");
        trimmedItems = immutableIds(trimmedItems, "trimmedItems");
        if (contextItemCount != selectedItems.size()) {
            throw new IllegalArgumentException("contextItemCount 必须等于 selectedItems 数量");
        }
        if (!Collections.disjoint(selectedItems, trimmedItems)) {
            throw new IllegalArgumentException("selectedItems 与 trimmedItems 不能重叠");
        }
        if (candidateScanCount < 0) {
            throw new IllegalArgumentException("candidateScanCount 不能小于 0");
        }
        if (retrievalLatencyMillis < 0) {
            throw new IllegalArgumentException("retrievalLatencyMillis 不能小于 0");
        }
        if (legacyMiss && !legacyComparable) {
            throw new IllegalArgumentException("legacyMiss 只能计入可比较请求");
        }
        categoryUsage = immutableCategoryUsage(categoryUsage);
    }

    public static MemoryRetrievalSample of(
            MemoryQueryProfile profile,
            int chapterCount,
            long contextTokenCount,
            List<String> selectedItems,
            List<String> trimmedItems,
            long candidateScanCount,
            long retrievalLatencyMillis,
            boolean legacyFallbackHit,
            boolean legacyMiss,
            boolean legacyComparable,
            Map<MemoryRetrievalCategory, Integer> categoryUsage) {
        List<String> selected = selectedItems == null ? List.of() : selectedItems;
        return new MemoryRetrievalSample(
                profile,
                chapterCount,
                contextTokenCount,
                selected.size(),
                selected,
                trimmedItems,
                candidateScanCount,
                retrievalLatencyMillis,
                legacyFallbackHit,
                legacyMiss,
                legacyComparable,
                categoryUsage);
    }

    private static List<String> immutableIds(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能包含空 ID");
            }
            normalized.add(value.trim());
        }
        if (normalized.size() != values.size()) {
            throw new IllegalArgumentException(field + " 不能包含重复 ID");
        }
        return List.copyOf(normalized);
    }

    private static Map<MemoryRetrievalCategory, Integer> immutableCategoryUsage(
            Map<MemoryRetrievalCategory, Integer> values) {
        EnumMap<MemoryRetrievalCategory, Integer> normalized =
                new EnumMap<>(MemoryRetrievalCategory.class);
        for (MemoryRetrievalCategory category : MemoryRetrievalCategory.values()) {
            Integer count = values == null ? null : values.get(category);
            if (count == null) {
                count = 0;
            }
            if (count < 0) {
                throw new IllegalArgumentException("categoryUsage 不能包含负数");
            }
            normalized.put(category, count);
        }
        if (values != null && values.keySet().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("categoryUsage 不能包含 null 类别");
        }
        return Collections.unmodifiableMap(normalized);
    }
}
