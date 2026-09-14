package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.LegacyFallbackMetricsReport;
import cn.ninth.novel.domain.memory.model.LegacyFallbackObservation;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.model.MemoryContextSelection;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalRoute;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 在新 ContextProvider 与 legacy bridge 之间执行默认回退门禁。 */
public final class LegacyFallbackRouter {

    private final MemoryContextProvider provider;
    private final LegacyFallbackMetricsCollector metricsCollector;
    private final MemoryContextProviderMetricsRecorder metricsRecorder;

    public LegacyFallbackRouter(LegacyFallbackMetricsCollector metricsCollector) {
        this(new MemoryContextProvider(), metricsCollector);
    }

    public LegacyFallbackRouter(
            MemoryContextProvider provider,
            LegacyFallbackMetricsCollector metricsCollector) {
        this.provider = Objects.requireNonNull(provider, "provider 不能为空");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector 不能为空");
        this.metricsRecorder = new MemoryContextProviderMetricsRecorder(
                this.provider, new MemoryRetrievalMetricsCollector());
    }

    /** 生产路径使用共享的持久化记录器；旧构造器继续服务领域测试。 */
    public LegacyFallbackRouter(
            MemoryContextProviderMetricsRecorder metricsRecorder,
            LegacyFallbackMetricsCollector metricsCollector) {
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder 不能为空");
        this.provider = metricsRecorder.provider();
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector 不能为空");
    }

    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Collection<MemoryContextItem> legacyBridgeCandidates,
            int chapterCount,
            boolean comparable,
            boolean legacyMiss) {
        return provide(
                querySpec,
                budget,
                canonicalCandidates,
                legacyBridgeCandidates,
                chapterCount,
                comparable,
                legacyMiss,
                false,
                false);
    }

    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Collection<MemoryContextItem> legacyBridgeCandidates,
            int chapterCount,
            boolean comparable,
            boolean legacyMiss,
            boolean forceLegacyFallback) {
        return provide(
                querySpec,
                budget,
                canonicalCandidates,
                legacyBridgeCandidates,
                chapterCount,
                comparable,
                legacyMiss,
                false,
                forceLegacyFallback);
    }

    /** 带生产 generation/run 关联的路由入口。 */
    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Collection<MemoryContextItem> legacyBridgeCandidates,
            int chapterNumber,
            boolean comparable,
            boolean legacyMiss,
            boolean legacySourceMissing,
            boolean forceLegacyFallback,
            String projectCode,
            String generationId) {
        return provideInternal(
                querySpec, budget, canonicalCandidates, legacyBridgeCandidates,
                chapterNumber, comparable, legacyMiss, legacySourceMissing,
                forceLegacyFallback, projectCode, generationId);
    }

    /**
     * 按实验模式提供记忆上下文。
     *
     * <p>V1 使用 supplier 延迟读取 Legacy bridge，因此 Canonical 主召回成功时不会触发
     * Legacy 查询。LEGACY 模式完全忽略 canonicalCandidates。AUTO 保留迁移期间的兼容行为。</p>
     */
    public MemoryContextPack provide(
            MemoryMode mode,
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Supplier<Collection<MemoryContextItem>> legacyBridgeSupplier,
            int chapterCount,
            boolean comparable,
            boolean legacyMiss,
            boolean legacySourceMissing,
            boolean forceLegacyFallback
    ) {
        return provide(
                mode,
                querySpec,
                budget,
                canonicalCandidates,
                legacyBridgeSupplier,
                chapterCount,
                comparable,
                legacyMiss,
                legacySourceMissing,
                forceLegacyFallback,
                null,
                null);
    }

    /** 带生产 generation/run 关联的实验路由入口。 */
    public MemoryContextPack provide(
            MemoryMode mode,
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Supplier<Collection<MemoryContextItem>> legacyBridgeSupplier,
            int chapterCount,
            boolean comparable,
            boolean legacyMiss,
            boolean legacySourceMissing,
            boolean forceLegacyFallback,
            String projectCode,
            String generationId
    ) {
        MemoryMode resolvedMode = mode == null ? MemoryMode.defaultMode() : mode;
        long startedAt = System.nanoTime();
        if (resolvedMode == MemoryMode.LEGACY) {
            Collection<MemoryContextItem> legacyCandidates = loadLegacy(legacyBridgeSupplier);
            MemoryQuerySpec legacyQuerySpec = queryWithCandidateSignals(
                    querySpec, legacyCandidates);
            MemoryContextSelection legacySelection = metricsRecorder.select(
                    legacyQuerySpec,
                    budget,
                    legacyCandidates);
            MemoryContextPack legacyPack = legacySelection.pack();
            metricsRecorder.record(
                    querySpec,
                    chapterCount,
                    projectCode,
                    generationId,
                    legacySelection,
                    legacyCandidates,
                    elapsedMillis(startedAt),
                    new MemoryRetrievalRoute(
                            resolvedMode.name(), false, false, false, false),
                    comparable && legacyMiss,
                    comparable);
            metricsCollector.record(new LegacyFallbackObservation(
                    querySpec.memoryProfile(), comparable, false,
                    comparable && legacyMiss));
            return legacyPack;
        }

        MemoryContextSelection canonicalSelection = metricsRecorder.select(
                querySpec, budget, canonicalCandidates);
        MemoryContextPack canonicalPack = canonicalSelection.pack();
        LegacyFallbackMetricsReport beforeRequest = metricsCollector.report();
        boolean fallbackRequested = forceLegacyFallback
                || (beforeRequest.fallbackEnabledByDefault()
                && (legacySourceMissing
                || canonicalPack.totalItemCount() == 0
                || legacyMiss));
        if (!fallbackRequested) {
            metricsRecorder.record(
                    querySpec,
                    chapterCount,
                    projectCode,
                    generationId,
                    canonicalSelection,
                    canonicalCandidates,
                    elapsedMillis(startedAt),
                    new MemoryRetrievalRoute(
                            resolvedMode.name(), true,
                            canonicalPack.totalItemCount() > 0, false, false),
                    comparable && legacyMiss,
                    comparable);
            metricsCollector.record(new LegacyFallbackObservation(
                    querySpec.memoryProfile(), comparable, false,
                    comparable && legacyMiss));
            return canonicalPack;
        }

        Collection<MemoryContextItem> legacyCandidates = loadLegacy(legacyBridgeSupplier);
        MemoryQuerySpec legacyQuerySpec = queryWithCandidateSignals(
                querySpec, legacyCandidates);
        MemoryContextSelection legacySelection = metricsRecorder.select(
                legacyQuerySpec,
                budget,
                legacyCandidates);
        MemoryContextPack legacyPack = legacySelection.pack();
        boolean fallbackHit = legacyPack.totalItemCount() > 0;
        MemoryContextSelection resultSelection = canonicalSelection;
        MemoryContextPack result = canonicalPack;
        Collection<MemoryContextItem> resultCandidates = canonicalCandidates;
        if (fallbackRequested) {
            Collection<MemoryContextItem> combined = combine(
                    canonicalCandidates, legacyCandidates);
            resultSelection = metricsRecorder.select(
                    queryWithCandidateSignals(querySpec, combined),
                    budget,
                    combined);
            result = resultSelection.pack();
            resultCandidates = combined;
        }
        metricsRecorder.record(
                querySpec,
                chapterCount,
                projectCode,
                generationId,
                resultSelection,
                resultCandidates,
                elapsedMillis(startedAt),
                new MemoryRetrievalRoute(
                        resolvedMode.name(), true,
                        canonicalPack.totalItemCount() > 0,
                        true,
                        fallbackHit),
                comparable && (legacyMiss || !fallbackHit),
                comparable);
        metricsCollector.record(new LegacyFallbackObservation(
                querySpec.memoryProfile(), comparable, fallbackHit,
                comparable && (legacyMiss || !fallbackHit)));
        org.slf4j.LoggerFactory.getLogger(LegacyFallbackRouter.class).info(
                "Memory fallback observed: mode={}, profile={}, chapter={}, requested={}, hit={}, canonicalItems={}, legacyItems={}",
                resolvedMode,
                querySpec.memoryProfile(),
                chapterCount,
                true,
                fallbackHit,
                canonicalPack.totalItemCount(),
                legacyPack.totalItemCount());
        return result;
    }

    /**
     * @param legacySourceMissing 表示 canonical 源暂时没有覆盖当前请求，
     *                            即使本次仍有 ARC/rule 等本地 canonical context 也应尝试 bridge
     * @param forceLegacyFallback 仅用于显式诊断/人工回退；不改变默认退出判定。
     */
    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Collection<MemoryContextItem> legacyBridgeCandidates,
            int chapterCount,
            boolean comparable,
            boolean legacyMiss,
            boolean legacySourceMissing,
            boolean forceLegacyFallback) {
        return provideInternal(
                querySpec, budget, canonicalCandidates, legacyBridgeCandidates,
                chapterCount, comparable, legacyMiss, legacySourceMissing,
                forceLegacyFallback, null, null);
    }

    private MemoryContextPack provideInternal(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> canonicalCandidates,
            Collection<MemoryContextItem> legacyBridgeCandidates,
            int chapterCount,
            boolean comparable,
            boolean legacyMiss,
            boolean legacySourceMissing,
            boolean forceLegacyFallback,
            String projectCode,
            String generationId) {
        long startedAt = System.nanoTime();
        MemoryContextSelection canonicalSelection = metricsRecorder.select(
                querySpec, budget, canonicalCandidates);
        MemoryContextPack canonicalPack = canonicalSelection.pack();
        LegacyFallbackMetricsReport beforeRequest = metricsCollector.report();
        boolean defaultFallbackEnabled = beforeRequest.fallbackEnabledByDefault();
        boolean fallbackRequested = forceLegacyFallback
                || (defaultFallbackEnabled
                && (legacySourceMissing || canonicalPack.totalItemCount() == 0 || legacyMiss));

        MemoryContextSelection resultSelection = canonicalSelection;
        MemoryContextPack result = canonicalPack;
        boolean fallbackHit = false;
        if (fallbackRequested) {
            MemoryContextSelection legacySelection = metricsRecorder.select(
                    querySpec, budget, legacyBridgeCandidates);
            MemoryContextPack legacyPack = legacySelection.pack();
            Collection<MemoryContextItem> combined = combine(
                    canonicalCandidates, legacyBridgeCandidates);
            resultSelection = metricsRecorder.select(
                    querySpec,
                    budget,
                    combined);
            result = resultSelection.pack();
            fallbackHit = legacyPack.totalItemCount() > 0;
        }
        boolean canonicalHit = canonicalPack.totalItemCount() > 0;
        MemoryRetrievalRoute route = new MemoryRetrievalRoute(
                fallbackHit
                        ? (canonicalHit ? "CANONICAL_AND_LEGACY" : "LEGACY_FALLBACK")
                        : MemoryRetrievalRoute.mode(canonicalHit, false),
                true,
                canonicalHit,
                fallbackRequested,
                fallbackHit);
        metricsRecorder.record(
                querySpec,
                chapterCount,
                projectCode,
                generationId,
                resultSelection,
                fallbackRequested
                        ? combine(canonicalCandidates, legacyBridgeCandidates)
                        : canonicalCandidates,
                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L),
                route,
                legacyMiss,
                comparable);
        MemoryProfile profile = querySpec.memoryProfile();
        metricsCollector.record(new LegacyFallbackObservation(
                profile, comparable, fallbackHit, legacyMiss));
        return result;
    }

    public LegacyFallbackMetricsCollector metricsCollector() {
        return metricsCollector;
    }

    private Collection<MemoryContextItem> loadLegacy(
            Supplier<Collection<MemoryContextItem>> supplier
    ) {
        if (supplier == null) {
            return List.of();
        }
        Collection<MemoryContextItem> candidates = supplier.get();
        return candidates == null ? List.of() : candidates;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static MemoryQuerySpec queryWithCandidateSignals(
            MemoryQuerySpec querySpec,
            Collection<MemoryContextItem> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return querySpec;
        }
        java.util.HashSet<String> currentStates = new java.util.HashSet<>(
                querySpec.currentSceneItemIds());
        currentStates.addAll(querySpec.currentEffectiveStateItemIds());
        java.util.HashSet<String> openLoops = new java.util.HashSet<>(
                querySpec.openOrProgressedOpenLoopIds());
        java.util.HashSet<String> rules = new java.util.HashSet<>(
                querySpec.hitWorldRuleIds());
        for (MemoryContextItem item : candidates) {
            if (item == null) {
                continue;
            }
            switch (item.category()) {
                case CURRENT_STATES -> currentStates.add(item.itemId());
                case OPEN_LOOPS -> openLoops.add(item.itemId());
                case RULES -> rules.add(item.itemId());
                case CONSOLIDATED, EPISODES -> { }
            }
        }
        return new MemoryQuerySpec(
                querySpec.memoryProfile(),
                currentStates,
                currentStates,
                openLoops,
                rules,
                querySpec.explicitlyRequestedIds());
    }

    private static List<MemoryContextItem> combine(
            Collection<MemoryContextItem> canonicalCandidates,
            Collection<MemoryContextItem> legacyBridgeCandidates) {
        java.util.ArrayList<MemoryContextItem> candidates = new java.util.ArrayList<>();
        if (canonicalCandidates != null) {
            candidates.addAll(canonicalCandidates);
        }
        if (legacyBridgeCandidates != null) {
            candidates.addAll(legacyBridgeCandidates);
        }
        return List.copyOf(candidates);
    }
}
