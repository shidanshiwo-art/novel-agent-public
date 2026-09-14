package cn.ninth.novel.domain.memory.model;

/** Legacy bridge 默认退出策略；所有条件必须同时满足。 */
public record LegacyFallbackPolicy(
        int recentComparableWindowSize,
        int minComparableSamplesPerProfile,
        double backfillCoverageThreshold,
        double missRateThreshold,
        int consecutiveNoFallbackThreshold
) {

    public LegacyFallbackPolicy {
        if (recentComparableWindowSize < 1) {
            throw new IllegalArgumentException("recentComparableWindowSize 必须为正数");
        }
        if (minComparableSamplesPerProfile < 1
                || minComparableSamplesPerProfile > recentComparableWindowSize) {
            throw new IllegalArgumentException(
                    "minComparableSamplesPerProfile 必须在窗口范围内");
        }
        if (backfillCoverageThreshold < 0.0d || backfillCoverageThreshold > 1.0d) {
            throw new IllegalArgumentException("backfillCoverageThreshold 必须在 0~1 之间");
        }
        if (missRateThreshold < 0.0d || missRateThreshold > 1.0d) {
            throw new IllegalArgumentException("missRateThreshold 必须在 0~1 之间");
        }
        if (consecutiveNoFallbackThreshold < 1) {
            throw new IllegalArgumentException("consecutiveNoFallbackThreshold 必须为正数");
        }
    }

    public static LegacyFallbackPolicy defaults() {
        return new LegacyFallbackPolicy(50, 30, 0.95d, 0.05d, 20);
    }
}
