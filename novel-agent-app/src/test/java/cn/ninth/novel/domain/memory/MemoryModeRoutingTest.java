package cn.ninth.novel.domain.memory;

import cn.ninth.novel.domain.chapter.adapter.repository.IContextRepository;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.service.data.ChapterContextLoader;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import cn.ninth.novel.domain.memory.service.MemoryContextProviderMetricsRecorder;
import cn.ninth.novel.domain.memory.service.MemoryRetrievalMetricsCollector;
import cn.ninth.novel.domain.memory.adapter.repository.IMemoryRetrievalMetricsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class MemoryModeRoutingTest {

    @Test
    void legacyDoesNotReadCanonicalRepository() {
        IContextRepository repository = mock(IContextRepository.class);
        MemoryContextItem legacyState = MemoryContextItem.bridge(
                "legacy-state",
                MemoryContextCategory.CURRENT_STATES,
                "Legacy story_summary 状态",
                4);
        when(repository.findMemoryContextItems("project-1", 5))
                .thenReturn(List.of(legacyState));
        when(repository.findCanonicalMemoryContextItems("project-1", 5))
                .thenThrow(new AssertionError("LEGACY 不应读取 Canonical Memory"));

        ChapterContextAggregate context = new ChapterContextLoader(repository)
                .loadContext("project-1", 5, MemoryMode.LEGACY);
        MemoryContextPack pack = context.getMemoryContextPack();

        System.out.printf(
                "Memory LEGACY route: items=%s, canonicalReads=0%n",
                pack.items().stream().map(MemoryContextItem::itemId).toList());
        assertThat(pack.items()).containsExactly(legacyState);
        verify(repository, never()).findCanonicalMemoryContextItems("project-1", 5);
        verify(repository).findMemoryContextItems("project-1", 5);
    }

    @Test
    void v1ReadsCanonicalRepository() {
        IContextRepository repository = mock(IContextRepository.class);
        MemoryContextItem canonicalState = canonicalState();
        when(repository.findCanonicalMemoryContextItems("project-1", 5))
                .thenReturn(List.of(canonicalState));

        ChapterContextAggregate context = new ChapterContextLoader(repository)
                .loadContext("project-1", 5, MemoryMode.V1);

        System.out.printf(
                "Memory V1 canonical route: items=%s%n",
                context.getMemoryContextPack().items().stream()
                        .map(MemoryContextItem::itemId).toList());
        assertThat(context.getMemoryContextPack().items())
                .containsExactly(canonicalState);
        verify(repository).findCanonicalMemoryContextItems("project-1", 5);
    }

    @Test
    void v1CanonicalHitDoesNotTriggerLegacyFallback() {
        IContextRepository repository = mock(IContextRepository.class);
        when(repository.findCanonicalMemoryContextItems("project-1", 5))
                .thenReturn(List.of(canonicalState()));
        when(repository.findLegacyMemoryContextItems("project-1", 5))
                .thenThrow(new AssertionError("Canonical 命中时不应触发 Legacy fallback"));

        ChapterContextAggregate context = new ChapterContextLoader(repository)
                .loadContext("project-1", 5, MemoryMode.V1);

        System.out.printf(
                "Memory V1 no-fallback route: items=%s%n",
                context.getMemoryContextPack().items().stream()
                        .map(MemoryContextItem::itemId).toList());
        assertThat(context.getMemoryContextPack().items())
                .extracting(MemoryContextItem::canonicalOrLegacy)
                .containsOnly("CANONICAL");
        verify(repository, never()).findLegacyMemoryContextItems("project-1", 5);
    }

    @Test
    void v1CanonicalUnavailableUsesObservableLegacyFallback() {
        IContextRepository repository = mock(IContextRepository.class);
        MemoryContextItem legacyState = MemoryContextItem.bridge(
                "legacy-state",
                MemoryContextCategory.CURRENT_STATES,
                "Legacy fallback 状态",
                4);
        when(repository.findCanonicalMemoryContextItems("project-1", 5))
                .thenReturn(List.of());
        when(repository.findLegacyMemoryContextItems("project-1", 5))
                .thenReturn(List.of(legacyState));

        IMemoryRetrievalMetricsRepository metricsRepository =
                mock(IMemoryRetrievalMetricsRepository.class);
        MemoryContextProviderMetricsRecorder recorder =
                new MemoryContextProviderMetricsRecorder(
                        new MemoryContextProvider(),
                        new MemoryRetrievalMetricsCollector(),
                        metricsRepository);
        ChapterContextAggregate context = new ChapterContextLoader(repository, recorder)
                .loadContext("project-1", 5, MemoryMode.V1, "run-1");

        ArgumentCaptor<MemoryRetrievalObservation> observations =
                ArgumentCaptor.forClass(MemoryRetrievalObservation.class);
        verify(metricsRepository, atLeastOnce()).save(observations.capture());
        boolean fallbackObserved = observations.getAllValues().stream().anyMatch(observation ->
                observation.route().memoryMode().equals(MemoryMode.V1.name())
                        && observation.route().canonicalRequested()
                        && observation.route().legacyFallbackRequested()
                        && observation.route().legacyFallbackHit());

        System.out.printf(
                "Memory V1 fallback route: items=%s, fallbackObserved=%s%n",
                context.getMemoryContextPack().items().stream()
                        .map(MemoryContextItem::itemId).toList(),
                fallbackObserved);
        assertThat(context.getMemoryContextPack().items()).containsExactly(legacyState);
        assertThat(fallbackObserved).isTrue();
        verify(repository).findCanonicalMemoryContextItems("project-1", 5);
        verify(repository).findLegacyMemoryContextItems("project-1", 5);
    }

    private MemoryContextItem canonicalState() {
        return MemoryContextItem.canonical(
                "CANONICAL_FACT",
                "canonical-state",
                MemoryContextCategory.CURRENT_STATES,
                "Canonical 当前状态",
                4,
                "chapter-v1",
                "FACT_ACTIVE",
                null,
                1);
    }
}
