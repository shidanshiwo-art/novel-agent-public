package cn.ninth.novel.memory.metrics;

import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.model.MemoryQueryProfile;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalCategory;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsReport;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalSample;
import cn.ninth.novel.domain.memory.service.MemoryRetrievalMetricsCollector;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import cn.ninth.novel.domain.memory.service.MemoryContextProviderMetricsRecorder;
import cn.ninth.novel.memory.fixture.ScalabilityFixtureFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryRetrievalMetricsTest {

    @Test
    void collectorAggregatesRequiredMetricsByProfileAndScale() {
        MemoryRetrievalMetricsCollector collector = new MemoryRetrievalMetricsCollector();
        MemoryRetrievalMetricsFixtureFactory
                .samplesFor(ScalabilityFixtureFactory.forChapterCount(10))
                .forEach(collector::record);

        MemoryRetrievalMetricsReport plan = collector.report(MemoryQueryProfile.PLAN, 10);
        MemoryRetrievalMetricsReport draft = collector.report(MemoryQueryProfile.DRAFT, 10);
        MemoryRetrievalMetricsReport review = collector.report(MemoryQueryProfile.REVIEW, 10);

        assertThat(plan.requestCount()).isEqualTo(MemoryRetrievalMetricsFixtureFactory.REQUESTS_PER_PROFILE);
        assertThat(plan.p50RetrievalLatencyMillis()).isGreaterThan(0);
        assertThat(plan.p95RetrievalLatencyMillis()).isGreaterThanOrEqualTo(plan.p50RetrievalLatencyMillis());
        assertThat(plan.totalContextTokenUsage()).isGreaterThan(0);
        assertThat(plan.totalSelectedItemCount()).isGreaterThan(0);
        assertThat(plan.totalTrimmedItemCount()).isGreaterThan(0);
        assertThat(plan.totalCandidateScanCount()).isEqualTo(10L * plan.requestCount());
        assertThat(plan.legacyFallbackHitCount()).isEqualTo(1);
        assertThat(plan.legacyMissCount()).isEqualTo(1);
        assertThat(plan.legacyComparableRequestCount()).isEqualTo(plan.requestCount());
        assertThat(plan.legacyMissRate()).isEqualTo(1.0d / plan.requestCount());
        assertThat(plan.categoryUsage()).containsEntry(MemoryRetrievalCategory.RULES, 30L);
        assertThat(plan.categoryUsage()).containsEntry(MemoryRetrievalCategory.OPEN_LOOPS, 60L);
        assertThat(draft.profile()).isEqualTo(MemoryQueryProfile.DRAFT);
        assertThat(review.profile()).isEqualTo(MemoryQueryProfile.REVIEW);
        assertThat(review.categoryUsage().get(MemoryRetrievalCategory.EPISODES))
                .isGreaterThan(plan.categoryUsage().get(MemoryRetrievalCategory.EPISODES));

        System.out.printf(
                "Retrieval metrics scale=10 PLAN p50=%dms p95=%dms selected=%d trimmed=%d tokens=%d categories=%s scans=%d missRate=%.4f%n",
                plan.p50RetrievalLatencyMillis(),
                plan.p95RetrievalLatencyMillis(),
                plan.totalSelectedItemCount(),
                plan.totalTrimmedItemCount(),
                plan.totalContextTokenUsage(),
                plan.categoryUsage(),
                plan.totalCandidateScanCount(),
                plan.legacyMissRate());
    }

    @Test
    void scalabilityReplayKeepsFixedContextBudgetWithoutIncreasingBudget() {
        MemoryRetrievalMetricsCollector collector = new MemoryRetrievalMetricsCollector();
        for (MemoryRetrievalSample sample : MemoryRetrievalMetricsFixtureFactory.allSamples()) {
            collector.record(sample);
        }

        assertThat(collector.reportAll()).hasSize(15);
        for (int chapterCount : ScalabilityFixtureFactory.SUPPORTED_CHAPTER_COUNTS) {
            for (MemoryQueryProfile profile : MemoryQueryProfile.values()) {
                MemoryRetrievalMetricsReport report = collector.report(profile, chapterCount);
                long averageTokens = report.totalContextTokenUsage() / report.requestCount();
                long averageItems = report.totalContextItemCount() / report.requestCount();

                assertThat(averageTokens)
                        .as("%s %d章 token budget", profile, chapterCount)
                        .isLessThanOrEqualTo(MemoryRetrievalMetricsFixtureFactory.CONTEXT_TOKEN_BUDGET);
                assertThat(averageItems)
                        .as("%s %d章 item budget", profile, chapterCount)
                        .isLessThanOrEqualTo(MemoryRetrievalMetricsFixtureFactory.CONTEXT_ITEM_BUDGET);
                assertThat(report.p95RetrievalLatencyMillis())
                        .isGreaterThanOrEqualTo(report.p50RetrievalLatencyMillis());
                assertThat(report.legacyMissRate()).isEqualTo(1.0d / report.requestCount());
                assertThat(report.totalCandidateScanCount())
                        .isEqualTo((long) chapterCount * report.requestCount());

                System.out.printf(
                        "Retrieval metrics scale=%d profile=%s p50=%dms p95=%dms selected=%d trimmed=%d tokens=%d categories=%s scans=%d legacyMissRate=%.4f%n",
                        chapterCount,
                        profile,
                        report.p50RetrievalLatencyMillis(),
                        report.p95RetrievalLatencyMillis(),
                        report.totalSelectedItemCount(),
                        report.totalTrimmedItemCount(),
                        report.totalContextTokenUsage(),
                        report.categoryUsage(),
                        report.totalCandidateScanCount(),
                        report.legacyMissRate());
            }
        }
    }

    @Test
    void sampleRejectsInconsistentContextAndInvalidLegacyMiss() {
        assertThatThrownBy(() -> new MemoryRetrievalSample(
                MemoryQueryProfile.PLAN,
                10,
                20,
                2,
                List.of("selected"),
                List.of("trimmed"),
                1,
                3,
                false,
                false,
                true,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contextItemCount");

        assertThatThrownBy(() -> MemoryRetrievalSample.of(
                MemoryQueryProfile.PLAN,
                10,
                20,
                List.of("selected"),
                List.of(),
                1,
                3,
                false,
                true,
                false,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("legacyMiss");
        System.out.println("Retrieval metric sample validation rejects inconsistent counts and non-comparable misses");
    }

    @Test
    void providerRecorderCapturesActualPackWithoutChangingProviderDecision() {
        var fixture = ScalabilityFixtureFactory.forChapterCount(10);
        var candidates = MemoryRetrievalMetricsFixtureFactory.contextItemsFor(fixture);
        var querySpec = new MemoryQuerySpec(
                MemoryProfile.PLAN,
                Set.of(),
                Set.of("scale-character-change-shenye"),
                Set.of("scale-open-loop-missing-seal", "scale-open-loop-courier"),
                Set.of("scale-world-rule-nocturne-ability"),
                Set.of());
        var budget = new MemoryBudgetSpec(512, 5, 5, 10, 6, 3);
        MemoryContextPack expected = new MemoryContextProvider().provide(querySpec, budget, candidates);
        var recorder = new MemoryContextProviderMetricsRecorder(
                new MemoryContextProvider(), new MemoryRetrievalMetricsCollector());

        MemoryContextPack actual = recorder.provide(
                querySpec, budget, candidates, fixture.chapterCount(), true, false, true);
        var report = recorder.collector().report(MemoryQueryProfile.PLAN, fixture.chapterCount());

        assertThat(actual).isEqualTo(expected);
        assertThat(report.requestCount()).isEqualTo(1);
        assertThat(report.totalContextTokenUsage()).isEqualTo(expected.tokenCount());
        assertThat(report.totalContextItemCount()).isEqualTo(expected.totalItemCount());
        assertThat(report.totalSelectedItemCount()).isEqualTo(expected.totalItemCount());
        assertThat(report.totalTrimmedItemCount())
                .isEqualTo(candidates.size() - expected.totalItemCount());
        assertThat(report.totalCandidateScanCount()).isEqualTo(candidates.size());
        assertThat(report.legacyFallbackHitCount()).isEqualTo(1);
        assertThat(report.legacyMissCount()).isZero();
        assertThat(report.legacyMissRate()).isZero();
        assertThat(report.p50RetrievalLatencyMillis()).isGreaterThanOrEqualTo(0);
        System.out.printf(
                "Actual provider metrics scale=%d profile=PLAN p50=%dms p95=%dms selected=%d trimmed=%d tokens=%d categories=%s scans=%d legacyMissRate=%.4f%n",
                fixture.chapterCount(),
                report.p50RetrievalLatencyMillis(),
                report.p95RetrievalLatencyMillis(),
                report.totalSelectedItemCount(),
                report.totalTrimmedItemCount(),
                report.totalContextTokenUsage(),
                report.categoryUsage(),
                report.totalCandidateScanCount(),
                report.legacyMissRate());
    }
}
