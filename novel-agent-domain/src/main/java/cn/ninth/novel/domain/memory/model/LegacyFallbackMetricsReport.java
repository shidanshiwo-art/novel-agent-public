package cn.ninth.novel.domain.memory.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Legacy 迁移覆盖率、回退和退出门禁的只读汇总。 */
public record LegacyFallbackMetricsReport(
        int acceptedChapterCount,
        int backfilledChapterCount,
        double backfillCoverage,
        boolean currentArcCoreComplete,
        long recentComparableRequestCount,
        long legacyFallbackHitCount,
        long legacyMissCount,
        long consecutiveNoFallbackCount,
        Map<MemoryProfile, Integer> comparableRequestCountByProfile,
        Map<MemoryProfile, Long> legacyFallbackHitCountByProfile,
        Map<MemoryProfile, Long> legacyMissCountByProfile,
        Map<MemoryProfile, Double> missRateByProfile,
        boolean fallbackEnabledByDefault,
        List<String> blockingReasons
) {

    public LegacyFallbackMetricsReport {
        if (acceptedChapterCount < 0 || backfilledChapterCount < 0) {
            throw new IllegalArgumentException("章节统计不能为负数");
        }
        if (backfilledChapterCount > acceptedChapterCount) {
            throw new IllegalArgumentException("backfilledChapterCount 不能超过 acceptedChapterCount");
        }
        if (backfillCoverage < 0.0d || backfillCoverage > 1.0d) {
            throw new IllegalArgumentException("backfillCoverage 必须在 0~1 之间");
        }
        if (recentComparableRequestCount < 0
                || legacyFallbackHitCount < 0
                || legacyMissCount < 0
                || consecutiveNoFallbackCount < 0) {
            throw new IllegalArgumentException("迁移统计不能为负数");
        }
        comparableRequestCountByProfile = immutableCounts(comparableRequestCountByProfile);
        legacyFallbackHitCountByProfile = immutableLongCounts(legacyFallbackHitCountByProfile);
        legacyMissCountByProfile = immutableLongCounts(legacyMissCountByProfile);
        missRateByProfile = immutableRates(missRateByProfile);
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }

    public boolean exitCriteriaMet() {
        return !fallbackEnabledByDefault;
    }

    public int comparableRequestCount(MemoryProfile profile) {
        return comparableRequestCountByProfile.getOrDefault(
                Objects.requireNonNull(profile, "profile 不能为空"), 0);
    }

    public double missRate(MemoryProfile profile) {
        return missRateByProfile.getOrDefault(
                Objects.requireNonNull(profile, "profile 不能为空"), 0.0d);
    }

    private static Map<MemoryProfile, Integer> immutableCounts(
            Map<MemoryProfile, Integer> source) {
        EnumMap<MemoryProfile, Integer> values = new EnumMap<>(MemoryProfile.class);
        for (MemoryProfile profile : MemoryProfile.values()) {
            Integer count = source == null ? null : source.get(profile);
            if (count == null) {
                count = 0;
            }
            if (count < 0) {
                throw new IllegalArgumentException("Profile 统计不能为负数");
            }
            values.put(profile, count);
        }
        return Collections.unmodifiableMap(values);
    }

    private static Map<MemoryProfile, Long> immutableLongCounts(
            Map<MemoryProfile, Long> source) {
        EnumMap<MemoryProfile, Long> values = new EnumMap<>(MemoryProfile.class);
        for (MemoryProfile profile : MemoryProfile.values()) {
            Long count = source == null ? null : source.get(profile);
            if (count == null) {
                count = 0L;
            }
            if (count < 0) {
                throw new IllegalArgumentException("Profile 统计不能为负数");
            }
            values.put(profile, count);
        }
        return Collections.unmodifiableMap(values);
    }

    private static Map<MemoryProfile, Double> immutableRates(
            Map<MemoryProfile, Double> source) {
        EnumMap<MemoryProfile, Double> values = new EnumMap<>(MemoryProfile.class);
        for (MemoryProfile profile : MemoryProfile.values()) {
            Double rate = source == null ? null : source.get(profile);
            if (rate == null) {
                rate = 0.0d;
            }
            if (rate < 0.0d || rate > 1.0d) {
                throw new IllegalArgumentException("Profile miss rate 必须在 0~1 之间");
            }
            values.put(profile, rate);
        }
        return Collections.unmodifiableMap(values);
    }
}
