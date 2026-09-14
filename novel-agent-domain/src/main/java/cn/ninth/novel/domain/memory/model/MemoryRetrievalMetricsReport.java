package cn.ninth.novel.domain.memory.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated retrieval observations for one profile and one corpus scale.
 */
public record MemoryRetrievalMetricsReport(
        MemoryQueryProfile profile,
        int chapterCount,
        long requestCount,
        long p50RetrievalLatencyMillis,
        long p95RetrievalLatencyMillis,
        long totalContextTokenUsage,
        long totalContextItemCount,
        long totalSelectedItemCount,
        long totalTrimmedItemCount,
        long totalCandidateScanCount,
        long legacyFallbackHitCount,
        long legacyMissCount,
        long legacyComparableRequestCount,
        double legacyMissRate,
        Map<MemoryRetrievalCategory, Long> categoryUsage
) {

    public MemoryRetrievalMetricsReport {
        profile = Objects.requireNonNull(profile, "profile 不能为空");
        if (chapterCount < 1) {
            throw new IllegalArgumentException("chapterCount 必须为正数");
        }
        if (requestCount < 0
                || p50RetrievalLatencyMillis < 0
                || p95RetrievalLatencyMillis < 0
                || totalContextTokenUsage < 0
                || totalContextItemCount < 0
                || totalSelectedItemCount < 0
                || totalTrimmedItemCount < 0
                || totalCandidateScanCount < 0
                || legacyFallbackHitCount < 0
                || legacyMissCount < 0
                || legacyComparableRequestCount < 0) {
            throw new IllegalArgumentException("metrics 不能包含负数");
        }
        if (legacyComparableRequestCount == 0 && legacyMissRate != 0.0d) {
            throw new IllegalArgumentException("无可比较请求时 legacyMissRate 必须为 0");
        }
        if (legacyMissRate < 0.0d || legacyMissRate > 1.0d) {
            throw new IllegalArgumentException("legacyMissRate 必须位于 [0, 1]");
        }
        categoryUsage = immutableCategoryUsage(categoryUsage);
    }

    public static MemoryRetrievalMetricsReport empty(
            MemoryQueryProfile profile,
            int chapterCount) {
        return new MemoryRetrievalMetricsReport(
                profile,
                chapterCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0d,
                Map.of());
    }

    private static Map<MemoryRetrievalCategory, Long> immutableCategoryUsage(
            Map<MemoryRetrievalCategory, Long> values) {
        EnumMap<MemoryRetrievalCategory, Long> normalized =
                new EnumMap<>(MemoryRetrievalCategory.class);
        for (MemoryRetrievalCategory category : MemoryRetrievalCategory.values()) {
            Long count = values == null ? null : values.get(category);
            if (count == null) {
                count = 0L;
            }
            if (count < 0) {
                throw new IllegalArgumentException("categoryUsage 不能包含负数");
            }
            normalized.put(category, count);
        }
        return Collections.unmodifiableMap(normalized);
    }
}
