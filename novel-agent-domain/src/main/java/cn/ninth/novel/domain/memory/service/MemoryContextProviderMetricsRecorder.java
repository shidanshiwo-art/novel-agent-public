package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.adapter.repository.IMemoryRetrievalMetricsRepository;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryContextSelection;
import cn.ninth.novel.domain.memory.model.MemoryQueryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalCategory;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalContextItem;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalRoute;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 为实际 ContextProvider 调用增加旁路检索指标。
 *
 * <p>记录发生在 provider 已经完成选择之后；该类不参与筛选、排序、预算或生命周期决策，
 * 也不修改 provider 返回的 ContextPack。持久化失败只影响观测，不影响生成。</p>
 */
@Component
public final class MemoryContextProviderMetricsRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(
            MemoryContextProviderMetricsRecorder.class);

    private final MemoryContextProvider provider;
    private final MemoryRetrievalMetricsCollector collector;
    private final IMemoryRetrievalMetricsRepository metricsRepository;

    public MemoryContextProviderMetricsRecorder() {
        this(new MemoryContextProvider(), new MemoryRetrievalMetricsCollector(), null);
    }

    public MemoryContextProviderMetricsRecorder(
            MemoryContextProvider provider,
            MemoryRetrievalMetricsCollector collector) {
        this(provider, collector, null);
    }

    public MemoryContextProviderMetricsRecorder(
            MemoryContextProvider provider,
            MemoryRetrievalMetricsCollector collector,
            IMemoryRetrievalMetricsRepository metricsRepository) {
        this.provider = Objects.requireNonNull(provider, "provider 不能为空");
        this.collector = Objects.requireNonNull(collector, "collector 不能为空");
        this.metricsRepository = metricsRepository;
    }

    @Autowired
    public MemoryContextProviderMetricsRecorder(
            IMemoryRetrievalMetricsRepository metricsRepository) {
        this(new MemoryContextProvider(), new MemoryRetrievalMetricsCollector(), metricsRepository);
    }

    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates,
            int chapterCount) {
        return provide(querySpec, budget, candidates, chapterCount, false, false, false);
    }

    /** 兼容已有内存聚合测试的调用；没有项目/运行标识时不落生产表。 */
    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates,
            int chapterCount,
            boolean legacyFallbackHit,
            boolean legacyMiss,
            boolean legacyComparable) {
        List<MemoryContextItem> input = immutableCandidates(candidates);
        long startedAt = System.nanoTime();
        MemoryContextSelection selection = provider.select(querySpec, budget, input);
        long elapsedMillis = elapsedMillis(startedAt);
        recordSample(querySpec, chapterCount, selection.pack(), input, elapsedMillis,
                legacyFallbackHit, legacyMiss, legacyComparable);
        return selection.pack();
    }

    /** 生产直接检索入口，例如 REVIEW 的主题证据包。 */
    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates,
            int chapterNumber,
            String projectCode,
            String generationId) {
        return provide(querySpec, budget, candidates, chapterNumber, projectCode,
                generationId, MemoryMode.defaultMode());
    }

    /** 生产直接检索入口，保留本次运行的 Memory mode。 */
    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates,
            int chapterNumber,
            String projectCode,
            String generationId,
            MemoryMode memoryMode) {
        List<MemoryContextItem> input = immutableCandidates(candidates);
        long startedAt = System.nanoTime();
        MemoryContextSelection selection = provider.select(querySpec, budget, input);
        long elapsedMillis = elapsedMillis(startedAt);
        boolean canonicalHit = selection.pack().items().stream()
                .anyMatch(item -> "CANONICAL".equals(item.canonicalOrLegacy()));
        MemoryMode resolvedMode = memoryMode == null
                ? MemoryMode.defaultMode() : memoryMode;
        record(querySpec, chapterNumber, projectCode, generationId, selection, input,
                elapsedMillis,
                new MemoryRetrievalRoute(
                        resolvedMode.name(),
                        resolvedMode != MemoryMode.LEGACY,
                        canonicalHit,
                        false,
                        false),
                false,
                false);
        return selection.pack();
    }

    /** Router 使用：只执行一次选择，不产生重复观测。 */
    public MemoryContextSelection select(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates) {
        return provider.select(querySpec, budget, candidates);
    }

    /** Router 使用：将最终合并后的选择作为一次完整路由观测写入。 */
    public void record(
            MemoryQuerySpec querySpec,
            int chapterNumber,
            String projectCode,
            String generationId,
            MemoryContextSelection selection,
            Collection<MemoryContextItem> candidates,
            long retrievalLatencyMillis,
            MemoryRetrievalRoute route,
            boolean legacyMiss,
            boolean legacyComparable) {
        List<MemoryContextItem> input = immutableCandidates(candidates);
        recordSample(querySpec, chapterNumber, selection.pack(), input, retrievalLatencyMillis,
                route.legacyFallbackHit(), legacyMiss, legacyComparable);
        recordObservation(querySpec, chapterNumber, projectCode, generationId, selection, input,
                retrievalLatencyMillis, route, legacyMiss, legacyComparable);
    }

    public MemoryContextProvider provider() {
        return provider;
    }

    public MemoryRetrievalMetricsCollector collector() {
        return collector;
    }

    private void recordSample(
            MemoryQuerySpec querySpec,
            int chapterNumber,
            MemoryContextPack pack,
            List<MemoryContextItem> input,
            long elapsedMillis,
            boolean legacyFallbackHit,
            boolean legacyMiss,
            boolean legacyComparable) {
        List<String> selectedIds = pack.items().stream()
                .map(MemoryContextItem::itemId)
                .toList();
        List<String> trimmedIds = input.stream()
                .map(MemoryContextItem::itemId)
                .filter(itemId -> !selectedIds.contains(itemId))
                .toList();
        collector.record(MemoryRetrievalSample.of(
                MemoryQueryProfile.valueOf(querySpec.memoryProfile().name()),
                chapterNumber,
                pack.tokenCount(),
                selectedIds,
                trimmedIds,
                input.size(),
                elapsedMillis,
                legacyFallbackHit,
                legacyMiss,
                legacyComparable,
                categoryUsage(pack)));
    }

    private void recordObservation(
            MemoryQuerySpec querySpec,
            int chapterNumber,
            String projectCode,
            String generationId,
            MemoryContextSelection selection,
            List<MemoryContextItem> input,
            long retrievalLatencyMillis,
            MemoryRetrievalRoute route,
            boolean legacyMiss,
            boolean legacyComparable) {
        if (projectCode == null || projectCode.isBlank() || metricsRepository == null) {
            return;
        }
        Set<String> explicitIds = querySpec.explicitlyRequestedIds();
        Set<String> candidateIds = input.stream()
                .map(MemoryContextItem::itemId)
                .collect(java.util.stream.Collectors.toSet());
        int notFoundCount = (int) explicitIds.stream()
                .filter(itemId -> !candidateIds.contains(itemId))
                .count();
        int retrievalMissCount = legacyComparable && legacyMiss ? 1 : 0;
        Set<MemoryContextItem> budgetTrimmed = Set.copyOf(selection.budgetTrimmedItems());
        Set<MemoryContextItem> selected = Set.copyOf(selection.pack().items());
        List<MemoryRetrievalContextItem> items = input.stream()
                .map(item -> {
                    boolean isSelected = selected.contains(item);
                    String decision = isSelected
                            ? "SELECTED"
                            : budgetTrimmed.contains(item)
                            ? "BUDGET_TRIM"
                            : "INTENTIONAL_TRIM";
                    return new MemoryRetrievalContextItem(
                            item.itemId(),
                            item.category(),
                            item.sourceType() == null ? item.category().name() : item.sourceType(),
                            item.sourceChapter(),
                            item.canonicalOrLegacy(),
                            item.estimatedTokenCount(),
                            isSelected,
                            !isSelected,
                            decision);
                })
                .toList();
        MemoryRetrievalObservation observation = new MemoryRetrievalObservation(
                projectCode,
                chapterNumber,
                querySpec.memoryProfile(),
                generationId,
                route,
                input.size(),
                selection.filteredItems().size(),
                selection.pack().totalItemCount(),
                selection.budgetTrimmedItems().size(),
                Math.max(0L, retrievalLatencyMillis),
                selection.pack().tokenCount(),
                notFoundCount,
                retrievalMissCount,
                input.size() - selection.filteredItems().size(),
                selection.budgetTrimmedItems().size(),
                items);
        try {
            metricsRepository.save(observation);
        } catch (RuntimeException exception) {
            LOG.warn("Memory Retrieval 指标写入失败，观测将被忽略，projectCode={}, chapterNumber={}",
                    projectCode, chapterNumber, exception);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static EnumMap<MemoryRetrievalCategory, Integer> categoryUsage(
            MemoryContextPack pack) {
        EnumMap<MemoryRetrievalCategory, Integer> usage =
                new EnumMap<>(MemoryRetrievalCategory.class);
        for (MemoryContextCategory category : MemoryContextCategory.values()) {
            usage.put(MemoryRetrievalCategory.valueOf(category.name()), items(pack, category).size());
        }
        return usage;
    }

    private static List<MemoryContextItem> items(
            MemoryContextPack pack,
            MemoryContextCategory category) {
        return switch (category) {
            case RULES -> pack.rules();
            case OPEN_LOOPS -> pack.openLoops();
            case CURRENT_STATES -> pack.currentStates();
            case CONSOLIDATED -> pack.consolidated();
            case EPISODES -> pack.episodes();
        };
    }

    private static List<MemoryContextItem> immutableCandidates(
            Collection<MemoryContextItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates 不能包含 null");
        }
        return List.copyOf(candidates);
    }
}
