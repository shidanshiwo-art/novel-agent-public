package cn.ninth.novel.memory.metrics;

import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryQueryProfile;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalCategory;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalSample;
import cn.ninth.novel.memory.fixture.FixtureMemoryItem;
import cn.ninth.novel.memory.fixture.FixtureMemoryKind;
import cn.ninth.novel.memory.fixture.ScalabilityFixture;
import cn.ninth.novel.memory.fixture.ScalabilityFixtureFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic retrieval observations over the Task 3 scalability corpora.
 *
 * <p>The values are replay measurements supplied to the collector; this helper does not make
 * admission decisions and does not enlarge any context budget.</p>
 */
public final class MemoryRetrievalMetricsFixtureFactory {

    public static final int REQUESTS_PER_PROFILE = 30;
    public static final long CONTEXT_TOKEN_BUDGET = 512;
    public static final int CONTEXT_ITEM_BUDGET = 16;

    private MemoryRetrievalMetricsFixtureFactory() {
    }

    public static List<MemoryRetrievalSample> allSamples() {
        List<MemoryRetrievalSample> samples = new ArrayList<>();
        for (ScalabilityFixture fixture : ScalabilityFixtureFactory.all()) {
            samples.addAll(samplesFor(fixture));
        }
        return List.copyOf(samples);
    }

    public static List<MemoryRetrievalSample> samplesFor(ScalabilityFixture fixture) {
        List<MemoryRetrievalSample> samples = new ArrayList<>();
        for (MemoryQueryProfile profile : MemoryQueryProfile.values()) {
            List<FixtureMemoryItem> selectedItems = selectedItems(fixture, profile);
            List<String> selectedIds = selectedItems.stream()
                    .map(FixtureMemoryItem::id)
                    .toList();
            List<String> trimmedIds = fixture.memoryItems().stream()
                    .map(FixtureMemoryItem::id)
                    .filter(id -> !selectedIds.contains(id))
                    .toList();
            Map<MemoryRetrievalCategory, Integer> categoryUsage = categoryUsage(selectedItems);
            long contextTokenCount = estimateTokens(selectedItems);

            for (int request = 0; request < REQUESTS_PER_PROFILE; request++) {
                samples.add(MemoryRetrievalSample.of(
                        profile,
                        fixture.chapterCount(),
                        contextTokenCount,
                        selectedIds,
                        trimmedIds,
                        fixture.candidates().size(),
                        latencyMillis(profile, fixture.chapterCount(), request),
                        request == 0,
                        request == 1,
                        true,
                        categoryUsage));
            }
        }
        return List.copyOf(samples);
    }

    /** Converts the shared scalability corpus into the domain provider's input shape. */
    public static List<MemoryContextItem> contextItemsFor(ScalabilityFixture fixture) {
        return fixture.memoryItems().stream()
                .map(MemoryRetrievalMetricsFixtureFactory::toContextItem)
                .toList();
    }

    private static MemoryContextItem toContextItem(FixtureMemoryItem item) {
        MemoryContextCategory category = switch (item.kind()) {
            case WORLD_RULE -> MemoryContextCategory.RULES;
            case OPEN_LOOP -> MemoryContextCategory.OPEN_LOOPS;
            case FACT, STATE, CHARACTER_KNOWLEDGE, CHARACTER_CHANGE ->
                    MemoryContextCategory.CURRENT_STATES;
            case CONSOLIDATED -> MemoryContextCategory.CONSOLIDATED;
            case EVENT, EPISODIC_DETAIL -> MemoryContextCategory.EPISODES;
        };
        if (item.lifecycle() == cn.ninth.novel.memory.fixture.FixtureLifecycle.ARCHIVED) {
            return MemoryContextItem.archived(
                    item.id(), category, item.content(), item.sourceChapter());
        }
        return MemoryContextItem.of(item.id(), category, item.content(), item.sourceChapter());
    }

    private static List<FixtureMemoryItem> selectedItems(
            ScalabilityFixture fixture,
            MemoryQueryProfile profile) {
        List<FixtureMemoryItem> selected = new ArrayList<>();
        selected.add(first(fixture, FixtureMemoryKind.WORLD_RULE));
        selected.addAll(fixture.itemsOfKind(FixtureMemoryKind.OPEN_LOOP));
        selected.add(first(fixture, FixtureMemoryKind.CHARACTER_CHANGE));
        if (profile == MemoryQueryProfile.PLAN || profile == MemoryQueryProfile.REVIEW) {
            selected.add(first(fixture, FixtureMemoryKind.CONSOLIDATED));
        }
        if (profile == MemoryQueryProfile.REVIEW) {
            selected.addAll(fixture.itemsOfKind(FixtureMemoryKind.EVENT).stream()
                    .limit(3)
                    .toList());
        }
        return List.copyOf(selected);
    }

    private static FixtureMemoryItem first(
            ScalabilityFixture fixture,
            FixtureMemoryKind kind) {
        return fixture.itemsOfKind(kind).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("fixture lacks " + kind));
    }

    private static Map<MemoryRetrievalCategory, Integer> categoryUsage(
            List<FixtureMemoryItem> selectedItems) {
        EnumMap<MemoryRetrievalCategory, Integer> usage =
                new EnumMap<>(MemoryRetrievalCategory.class);
        for (MemoryRetrievalCategory category : MemoryRetrievalCategory.values()) {
            usage.put(category, 0);
        }
        for (FixtureMemoryItem item : selectedItems) {
            usage.compute(categoryFor(item.kind()), (category, count) -> count + 1);
        }
        return usage;
    }

    private static MemoryRetrievalCategory categoryFor(FixtureMemoryKind kind) {
        return switch (kind) {
            case WORLD_RULE -> MemoryRetrievalCategory.RULES;
            case OPEN_LOOP -> MemoryRetrievalCategory.OPEN_LOOPS;
            case FACT, STATE, CHARACTER_KNOWLEDGE, CHARACTER_CHANGE ->
                    MemoryRetrievalCategory.CURRENT_STATES;
            case CONSOLIDATED -> MemoryRetrievalCategory.CONSOLIDATED;
            case EVENT, EPISODIC_DETAIL -> MemoryRetrievalCategory.EPISODES;
        };
    }

    private static long estimateTokens(List<FixtureMemoryItem> selectedItems) {
        int characters = selectedItems.stream()
                .mapToInt(item -> item.content().length())
                .sum();
        return Math.max(1L, (characters + 1L) / 2L);
    }

    private static long latencyMillis(
            MemoryQueryProfile profile,
            int chapterCount,
            int request) {
        long profileBase = switch (profile) {
            case PLAN -> 4L;
            case DRAFT -> 7L;
            case REVIEW -> 10L;
        };
        return profileBase + chapterCount / 10L + request * 2L;
    }
}
