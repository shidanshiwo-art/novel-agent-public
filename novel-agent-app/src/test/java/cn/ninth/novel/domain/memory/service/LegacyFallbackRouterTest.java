package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.LegacyFallbackObservation;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyFallbackRouterTest {

    @Test
    void routerUsesBridgeBeforeExitAndStopsDefaultFallbackAfterExit() {
        MemoryContextItem legacyLoop = MemoryContextItem.bridge(
                "legacy-loop", MemoryContextCategory.OPEN_LOOPS,
                "旧摘要中的未解决伏笔", 9);
        MemoryQuerySpec querySpec = new MemoryQuerySpec(
                MemoryProfile.PLAN,
                Set.of(),
                Set.of(),
                Set.of("legacy-loop"),
                Set.of(),
                Set.of());
        MemoryBudgetSpec budget = new MemoryBudgetSpec(512, 5, 5, 10, 6, 3);

        LegacyFallbackMetricsCollector beforeExitMetrics = new LegacyFallbackMetricsCollector();
        LegacyFallbackRouter beforeExitRouter = new LegacyFallbackRouter(beforeExitMetrics);
        MemoryContextPack fallbackPack = beforeExitRouter.provide(
                querySpec, budget, List.of(), List.of(legacyLoop), 10, true, false);

        assertThat(fallbackPack.items()).containsExactly(legacyLoop);
        assertThat(beforeExitMetrics.report().legacyFallbackHitCount()).isEqualTo(1);
        assertThat(beforeExitMetrics.report().consecutiveNoFallbackCount()).isZero();

        LegacyFallbackMetricsCollector afterExitMetrics = new LegacyFallbackMetricsCollector();
        afterExitMetrics.recordBackfill(100, 95);
        afterExitMetrics.recordCurrentArcCoreCompleteness(true);
        for (MemoryProfile profile : MemoryProfile.values()) {
            for (int index = 0; index < 30; index++) {
                afterExitMetrics.record(new LegacyFallbackObservation(
                        profile, true, false, false));
            }
        }
        assertThat(afterExitMetrics.report().exitCriteriaMet()).isTrue();

        LegacyFallbackRouter afterExitRouter = new LegacyFallbackRouter(afterExitMetrics);
        MemoryContextPack noDefaultFallbackPack = afterExitRouter.provide(
                querySpec, budget, List.of(), List.of(legacyLoop), 10, true, true);

        assertThat(noDefaultFallbackPack.items()).isEmpty();
        assertThat(afterExitMetrics.report().legacyFallbackHitCount()).isZero();
        assertThat(afterExitMetrics.report().fallbackEnabledByDefault()).isFalse();
        System.out.printf(
                "Legacy router beforeExitHit=%d afterExitFallbackHit=%d afterExitEnabled=%s%n",
                beforeExitMetrics.report().legacyFallbackHitCount(),
                afterExitMetrics.report().legacyFallbackHitCount(),
                afterExitMetrics.report().fallbackEnabledByDefault());
    }
}
