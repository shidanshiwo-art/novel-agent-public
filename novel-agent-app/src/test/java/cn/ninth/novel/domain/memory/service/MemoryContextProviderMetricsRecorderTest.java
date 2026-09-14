package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.adapter.repository.IMemoryRetrievalMetricsRepository;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsQuery;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryContextProviderMetricsRecorderTest {

    @Test
    void recordsSelectionReasonsAndRouteWithoutContextContent() {
        RecordingRepository repository = new RecordingRepository();
        MemoryContextProviderMetricsRecorder recorder = new MemoryContextProviderMetricsRecorder(
                new MemoryContextProvider(),
                new MemoryRetrievalMetricsCollector(),
                repository);

        recorder.provide(
                new MemoryQuerySpec(
                        MemoryProfile.DRAFT,
                        Set.of("state"), Set.of("state"), Set.of("loop"), Set.of(),
                        Set.of("missing")),
                new MemoryBudgetSpec(2, 4, 4, 4, 4, 4),
                List.of(
                        MemoryContextItem.of("state", MemoryContextCategory.CURRENT_STATES,
                                "当前状态正文", 3),
                        MemoryContextItem.of("loop", MemoryContextCategory.OPEN_LOOPS,
                                "未解决伏笔正文", 2),
                        MemoryContextItem.of("summary", MemoryContextCategory.CONSOLIDATED,
                                "不满足 DRAFT 信号的摘要正文", 1)),
                3,
                "metrics-unit-project",
                "draft-run",
                MemoryMode.V1);

        MemoryRetrievalObservation observation = repository.saved.get(0);
        System.out.printf(
                "Memory recorder route=%s candidates=%d selected=%d filtered=%d budgetTrim=%d notFound=%d items=%s%n",
                observation.route().memoryMode(), observation.candidateCount(),
                observation.selectedCount(), observation.filteredCount(),
                observation.budgetTrimCount(), observation.notFoundCount(),
                observation.items().stream()
                        .map(item -> item.itemId() + ":" + item.decision())
                        .toList());

        assertThat(observation.route().memoryMode()).isEqualTo("V1");
        assertThat(observation.route().canonicalRequested()).isTrue();
        assertThat(observation.route().canonicalHit()).isTrue();
        assertThat(observation.route().legacyFallbackRequested()).isFalse();
        assertThat(observation.route().legacyFallbackHit()).isFalse();
        assertThat(observation.candidateCount()).isEqualTo(3);
        assertThat(observation.filteredCount()).isEqualTo(2);
        assertThat(observation.selectedCount()).isEqualTo(1);
        assertThat(observation.trimmedCount()).isEqualTo(1);
        assertThat(observation.notFoundCount()).isEqualTo(1);
        assertThat(observation.retrievalMissCount()).isZero();
        assertThat(observation.intentionalTrimCount()).isEqualTo(1);
        assertThat(observation.budgetTrimCount()).isEqualTo(1);
        assertThat(observation.items()).allSatisfy(item -> {
            assertThat(item).hasNoNullFieldsOrProperties();
            assertThat(item.getClass().getRecordComponents())
                    .extracting(component -> component.getName())
                    .doesNotContain("content");
        });
        assertThat(observation.items()).extracting(item -> item.decision())
                .containsExactlyInAnyOrder("SELECTED", "INTENTIONAL_TRIM", "BUDGET_TRIM");
    }

    @Test
    void recordsLegacyFallbackAsRouteAndKeepsItOutOfRetrievalMiss() {
        RecordingRepository repository = new RecordingRepository();
        MemoryContextProviderMetricsRecorder recorder = new MemoryContextProviderMetricsRecorder(
                new MemoryContextProvider(),
                new MemoryRetrievalMetricsCollector(),
                repository);
        LegacyFallbackRouter router = new LegacyFallbackRouter(
                recorder, new LegacyFallbackMetricsCollector());

        router.provide(
                MemoryMode.V1,
                new MemoryQuerySpec(MemoryProfile.DRAFT),
                MemoryBudgetSpec.defaultP0(),
                List.of(),
                () -> List.of(MemoryContextItem.bridge(
                        "legacy-loop", MemoryContextCategory.OPEN_LOOPS,
                        "Legacy bridge 内容", 5)),
                6,
                true,
                true,
                true,
                false,
                "metrics-unit-project",
                "fallback-run");

        MemoryRetrievalObservation observation = repository.saved.get(0);
        System.out.printf(
                "Memory fallback route requested=%s hit=%s miss=%d origin=%s%n",
                observation.route().legacyFallbackRequested(),
                observation.route().legacyFallbackHit(),
                observation.retrievalMissCount(),
                observation.items().get(0).canonicalOrLegacy());

        assertThat(observation.route().canonicalRequested()).isTrue();
        assertThat(observation.route().canonicalHit()).isFalse();
        assertThat(observation.route().legacyFallbackRequested()).isTrue();
        assertThat(observation.route().legacyFallbackHit()).isTrue();
        assertThat(observation.retrievalMissCount()).isEqualTo(1);
        assertThat(observation.items()).singleElement()
                .extracting(item -> item.canonicalOrLegacy())
                .isEqualTo("LEGACY");
    }

    private static final class RecordingRepository implements IMemoryRetrievalMetricsRepository {
        private final List<MemoryRetrievalObservation> saved = new ArrayList<>();

        @Override
        public void save(MemoryRetrievalObservation observation) {
            saved.add(observation);
        }

        @Override
        public List<MemoryRetrievalObservation> find(MemoryRetrievalMetricsQuery query) {
            return saved.stream()
                    .filter(observation -> observation.projectCode().equals(query.projectCode()))
                    .toList();
        }
    }
}
