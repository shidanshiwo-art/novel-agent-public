package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.LegacyFallbackMetricsReport;
import cn.ninth.novel.domain.memory.model.LegacyFallbackObservation;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyFallbackMetricsCollectorTest {

    @Test
    void fallbackStaysEnabledWhenAnyExitCriterionIsMissing() {
        LegacyFallbackMetricsCollector collector = readyForSamples();
        recordComparableSamples(collector, 30, false);
        collector.recordCurrentArcCoreCompleteness(false);

        LegacyFallbackMetricsReport report = collector.report();

        assertThat(report.backfillCoverage()).isEqualTo(0.95d);
        assertThat(report.recentComparableRequestCount()).isEqualTo(90);
        assertThat(report.fallbackEnabledByDefault()).isTrue();
        assertThat(report.exitCriteriaMet()).isFalse();
        assertThat(report.blockingReasons()).contains("当前 ARC 核心对象不完整");
        System.out.printf(
                "Legacy fallback remains enabled coverage=%.2f comparable=%d missRates=%s consecutive=%d reasons=%s%n",
                report.backfillCoverage(),
                report.recentComparableRequestCount(),
                report.missRateByProfile(),
                report.consecutiveNoFallbackCount(),
                report.blockingReasons());
    }

    @Test
    void fallbackExitsOnlyAfterAllProfilesMeetMinimumAndThresholds() {
        LegacyFallbackMetricsCollector collector = readyForSamples();
        collector.recordCurrentArcCoreCompleteness(true);
        recordComparableSamples(collector, 30, false);

        LegacyFallbackMetricsReport report = collector.report();

        assertThat(report.fallbackEnabledByDefault()).isFalse();
        assertThat(report.exitCriteriaMet()).isTrue();
        assertThat(report.blockingReasons()).isEmpty();
        for (MemoryProfile profile : MemoryProfile.values()) {
            assertThat(report.comparableRequestCount(profile)).isEqualTo(30);
            assertThat(report.missRate(profile)).isZero();
        }
        System.out.printf(
                "Legacy fallback exit eligible coverage=%.2f comparable=%d hits=%d misses=%d consecutive=%d%n",
                report.backfillCoverage(),
                report.recentComparableRequestCount(),
                report.legacyFallbackHitCount(),
                report.legacyMissCount(),
                report.consecutiveNoFallbackCount());
    }

    @Test
    void missRateAndFallbackHitResetKeepFallbackEnabled() {
        LegacyFallbackMetricsCollector missCollector = readyForSamples();
        missCollector.recordCurrentArcCoreCompleteness(true);
        recordComparableSamples(missCollector, 30, false);
        missCollector.record(new LegacyFallbackObservation(
                MemoryProfile.PLAN, true, false, true));
        missCollector.record(new LegacyFallbackObservation(
                MemoryProfile.PLAN, true, false, true));

        LegacyFallbackMetricsReport missReport = missCollector.report();
        assertThat(missReport.missRate(MemoryProfile.PLAN)).isGreaterThan(0.05d);
        assertThat(missReport.fallbackEnabledByDefault()).isTrue();

        LegacyFallbackMetricsCollector resetCollector = readyForSamples();
        resetCollector.recordCurrentArcCoreCompleteness(true);
        recordComparableSamples(resetCollector, 30, false);
        assertThat(resetCollector.report().exitCriteriaMet()).isTrue();
        resetCollector.record(new LegacyFallbackObservation(
                MemoryProfile.REVIEW, true, true, false));
        LegacyFallbackMetricsReport resetReport = resetCollector.report();
        assertThat(resetReport.consecutiveNoFallbackCount()).isZero();
        assertThat(resetReport.fallbackEnabledByDefault()).isTrue();
        System.out.printf(
                "Legacy fallback guard missRate PLAN=%.4f afterMissEnabled=%s afterHitConsecutive=%d%n",
                missReport.missRate(MemoryProfile.PLAN),
                missReport.fallbackEnabledByDefault(),
                resetReport.consecutiveNoFallbackCount());
    }

    private static LegacyFallbackMetricsCollector readyForSamples() {
        LegacyFallbackMetricsCollector collector = new LegacyFallbackMetricsCollector();
        collector.recordBackfill(100, 95);
        return collector;
    }

    private static void recordComparableSamples(
            LegacyFallbackMetricsCollector collector,
            int count,
            boolean fallbackHit) {
        for (MemoryProfile profile : MemoryProfile.values()) {
            for (int index = 0; index < count; index++) {
                collector.record(new LegacyFallbackObservation(
                        profile, true, fallbackHit, false));
            }
        }
    }
}
