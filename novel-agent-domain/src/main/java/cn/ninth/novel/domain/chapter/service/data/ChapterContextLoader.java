package cn.ninth.novel.domain.chapter.service.data;


import cn.ninth.novel.domain.chapter.adapter.repository.IContextRepository;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.service.LegacyFallbackMetricsCollector;
import cn.ninth.novel.domain.memory.service.LegacyFallbackRouter;
import cn.ninth.novel.domain.memory.service.MemoryContextProviderMetricsRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ChapterContextLoader implements IDataService{

    private final IContextRepository contextRepository;
    private final LegacyFallbackRouter legacyFallbackRouter;
    private final MemoryContextProviderMetricsRecorder metricsRecorder;

    public ChapterContextLoader(IContextRepository contextRepository) {
        this(contextRepository, new LegacyFallbackRouter(
                new LegacyFallbackMetricsCollector()));
    }

    public ChapterContextLoader(
            IContextRepository contextRepository,
            LegacyFallbackRouter legacyFallbackRouter
    ) {
        this.contextRepository = contextRepository;
        this.legacyFallbackRouter = legacyFallbackRouter;
        this.metricsRecorder = new MemoryContextProviderMetricsRecorder();
    }

    @Autowired
    public ChapterContextLoader(
            IContextRepository contextRepository,
            MemoryContextProviderMetricsRecorder metricsRecorder
    ) {
        this.contextRepository = contextRepository;
        this.metricsRecorder = metricsRecorder;
        this.legacyFallbackRouter = new LegacyFallbackRouter(
                metricsRecorder, new LegacyFallbackMetricsCollector());
    }

    @Override
    public ChapterContextAggregate loadContext(String projectCode, int chapterNumber) {
        return loadContext(projectCode, chapterNumber, MemoryMode.defaultMode(), null);
    }

    @Override
    public ChapterContextAggregate loadContext(
            String projectCode,
            int chapterNumber,
            MemoryMode memoryMode
    ) {
        return loadContext(projectCode, chapterNumber, memoryMode, null);
    }

    @Override
    public ChapterContextAggregate loadContext(
            String projectCode,
            int chapterNumber,
            MemoryMode memoryMode,
            String generationId
    ) {
        MemoryMode resolvedMode = memoryMode == null
                ? MemoryMode.defaultMode() : memoryMode;
        var canonicalCandidates = resolvedMode == MemoryMode.LEGACY
                ? java.util.List.<MemoryContextItem>of()
                : contextRepository.findCanonicalMemoryContextItems(
                        projectCode, chapterNumber);
        if (canonicalCandidates == null) {
            canonicalCandidates = java.util.List.of();
        }
        MemoryContextPack memoryContextPack = loadDraftMemoryContext(
                projectCode, chapterNumber, resolvedMode, canonicalCandidates, generationId);
        MemoryContextPack reviewMemoryContextPack = loadReviewMemoryContext(
                projectCode, chapterNumber, generationId, resolvedMode, canonicalCandidates);

        return ChapterContextAggregate.builder()
                .project(contextRepository.loadProject(projectCode))
                .storyBible(contextRepository.loadStoryBible(projectCode))
                .chapterPlan(contextRepository.loadChapterPlan(projectCode, chapterNumber))
                .arc(contextRepository.loadCurrentArc(projectCode, chapterNumber))
                .characters(contextRepository.loadCharacters(projectCode))
                .history(contextRepository.loadHistory(projectCode,chapterNumber))
                .memoryContextPack(memoryContextPack)
                .reviewMemoryContextPack(reviewMemoryContextPack)
                .build();
    }

    private MemoryContextPack loadDraftMemoryContext(
            String projectCode,
            int chapterNumber,
            MemoryMode memoryMode,
            java.util.List<MemoryContextItem> canonicalCandidates,
            String generationId
    ) {
        var legacyCandidates = memoryMode == MemoryMode.LEGACY
                ? contextRepository.findMemoryContextItems(projectCode, chapterNumber)
                : null;
        var candidates = memoryMode == MemoryMode.LEGACY
                ? legacyCandidates
                : canonicalCandidates;
        if (candidates == null) {
            candidates = java.util.List.of();
        }
        Set<String> currentStateIds = candidates.stream()
                .filter(item -> item.category() == MemoryContextCategory.CURRENT_STATES)
                .map(MemoryContextItem::itemId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> openLoopIds = candidates.stream()
                .filter(item -> item.category() == MemoryContextCategory.OPEN_LOOPS)
                .map(MemoryContextItem::itemId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        MemoryQuerySpec querySpec = new MemoryQuerySpec(
                MemoryProfile.DRAFT,
                currentStateIds,
                currentStateIds,
                openLoopIds,
                Set.of(),
                Set.of());
        MemoryContextPack result = legacyFallbackRouter.provide(
                memoryMode,
                querySpec,
                MemoryBudgetSpec.defaultP0(),
                canonicalCandidates,
                memoryMode == MemoryMode.LEGACY
                        ? () -> legacyCandidates
                        : () -> contextRepository.findLegacyMemoryContextItems(
                                projectCode, chapterNumber),
                chapterNumber,
                false,
                false,
                memoryMode != MemoryMode.LEGACY && canonicalCandidates.isEmpty(),
                false,
                projectCode,
                generationId);
        return result.totalItemCount() == 0 ? null : result;
    }

    private MemoryContextPack loadReviewMemoryContext(
            String projectCode,
            int chapterNumber,
            String generationId,
            MemoryMode memoryMode,
            java.util.List<MemoryContextItem> canonicalCandidates
    ) {
        if (memoryMode == MemoryMode.LEGACY || canonicalCandidates.isEmpty()) {
            return null;
        }
        MemoryContextPack result = metricsRecorder.provide(
                MemoryQuerySpec.review(),
                MemoryBudgetSpec.defaultP0(),
                canonicalCandidates,
                chapterNumber,
                projectCode,
                generationId,
                memoryMode);
        return result.totalItemCount() == 0 ? null : result;
    }
}
