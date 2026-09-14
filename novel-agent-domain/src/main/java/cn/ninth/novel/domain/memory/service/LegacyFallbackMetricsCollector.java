package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.LegacyFallbackMetricsReport;
import cn.ninth.novel.domain.memory.model.LegacyFallbackObservation;
import cn.ninth.novel.domain.memory.model.LegacyFallbackPolicy;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalSample;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Legacy 迁移期间的滚动统计与默认 fallback 退出门禁。
 *
 * <p>每个 Profile 保留最近固定窗口；任何退出条件未满足时，
 * {@link LegacyFallbackMetricsReport#fallbackEnabledByDefault()} 都保持为 {@code true}。</p>
 */
public final class LegacyFallbackMetricsCollector {

    private final LegacyFallbackPolicy policy;
    private final EnumMap<MemoryProfile, Deque<LegacyFallbackObservation>> recentByProfile =
            new EnumMap<>(MemoryProfile.class);
    private int acceptedChapterCount;
    private int backfilledChapterCount;
    private boolean currentArcCoreComplete;
    private long consecutiveNoFallbackCount;

    public LegacyFallbackMetricsCollector() {
        this(LegacyFallbackPolicy.defaults());
    }

    public LegacyFallbackMetricsCollector(LegacyFallbackPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy 不能为空");
        for (MemoryProfile profile : MemoryProfile.values()) {
            recentByProfile.put(profile, new ArrayDeque<>());
        }
    }

    public synchronized void record(LegacyFallbackObservation observation) {
        Objects.requireNonNull(observation, "observation 不能为空");
        Deque<LegacyFallbackObservation> recent = recentByProfile.get(observation.profile());
        recent.addLast(observation);
        while (recent.size() > policy.recentComparableWindowSize()) {
            recent.removeFirst();
        }
        if (observation.legacyFallbackHit()) {
            consecutiveNoFallbackCount = 0L;
        } else {
            consecutiveNoFallbackCount++;
        }
    }

    public synchronized void record(MemoryRetrievalSample sample) {
        MemoryProfile profile = MemoryProfile.valueOf(sample.profile().name());
        record(new LegacyFallbackObservation(
                profile,
                sample.legacyComparable(),
                sample.legacyFallbackHit(),
                sample.legacyMiss()));
    }

    public synchronized void recordBackfill(int acceptedChapterCount, int backfilledChapterCount) {
        if (acceptedChapterCount < 0 || backfilledChapterCount < 0) {
            throw new IllegalArgumentException("章节统计不能为负数");
        }
        if (backfilledChapterCount > acceptedChapterCount) {
            throw new IllegalArgumentException("backfilledChapterCount 不能超过 acceptedChapterCount");
        }
        this.acceptedChapterCount = acceptedChapterCount;
        this.backfilledChapterCount = backfilledChapterCount;
    }

    public synchronized void recordCurrentArcCoreCompleteness(boolean complete) {
        currentArcCoreComplete = complete;
    }

    public synchronized LegacyFallbackMetricsReport report() {
        EnumMap<MemoryProfile, Integer> comparableCounts =
                new EnumMap<>(MemoryProfile.class);
        EnumMap<MemoryProfile, Long> fallbackHits = new EnumMap<>(MemoryProfile.class);
        EnumMap<MemoryProfile, Long> misses = new EnumMap<>(MemoryProfile.class);
        EnumMap<MemoryProfile, Double> missRates = new EnumMap<>(MemoryProfile.class);
        long comparableTotal = 0L;
        long fallbackHitTotal = 0L;
        long missTotal = 0L;

        for (MemoryProfile profile : MemoryProfile.values()) {
            List<LegacyFallbackObservation> recent =
                    List.copyOf(recentByProfile.get(profile));
            long comparable = recent.stream()
                    .filter(LegacyFallbackObservation::comparable)
                    .count();
            long hits = recent.stream()
                    .filter(LegacyFallbackObservation::legacyFallbackHit)
                    .count();
            long profileMisses = recent.stream()
                    .filter(LegacyFallbackObservation::legacyMiss)
                    .count();
            comparableCounts.put(profile, Math.toIntExact(comparable));
            fallbackHits.put(profile, hits);
            misses.put(profile, profileMisses);
            missRates.put(profile, comparable == 0L
                    ? 0.0d : (double) profileMisses / comparable);
            comparableTotal += comparable;
            fallbackHitTotal += hits;
            missTotal += profileMisses;
        }

        double coverage = acceptedChapterCount == 0
                ? 0.0d : (double) backfilledChapterCount / acceptedChapterCount;
        List<String> blockingReasons = blockingReasons(
                coverage, comparableCounts, missRates);
        return new LegacyFallbackMetricsReport(
                acceptedChapterCount,
                backfilledChapterCount,
                coverage,
                currentArcCoreComplete,
                comparableTotal,
                fallbackHitTotal,
                missTotal,
                consecutiveNoFallbackCount,
                comparableCounts,
                fallbackHits,
                misses,
                missRates,
                !blockingReasons.isEmpty(),
                blockingReasons);
    }

    public LegacyFallbackPolicy policy() {
        return policy;
    }

    private List<String> blockingReasons(
            double coverage,
            Map<MemoryProfile, Integer> comparableCounts,
            Map<MemoryProfile, Double> missRates) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (coverage < policy.backfillCoverageThreshold()) {
            reasons.add("backfill coverage 未达到阈值");
        }
        if (!currentArcCoreComplete) {
            reasons.add("当前 ARC 核心对象不完整");
        }
        for (MemoryProfile profile : MemoryProfile.values()) {
            if (comparableCounts.get(profile) < policy.minComparableSamplesPerProfile()) {
                reasons.add(profile + " 可比较样本不足");
            }
            if (missRates.get(profile) > policy.missRateThreshold()) {
                reasons.add(profile + " legacy miss rate 超过阈值");
            }
        }
        if (consecutiveNoFallbackCount < policy.consecutiveNoFallbackThreshold()) {
            reasons.add("连续无 fallback 次数不足");
        }
        return List.copyOf(reasons);
    }
}
