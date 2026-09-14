package cn.ninth.novel.memory.acceptance;

import cn.ninth.novel.domain.memory.model.MemoryAdmissionItem;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import cn.ninth.novel.domain.memory.service.MemoryLifecycleManager;
import cn.ninth.novel.memory.fixture.ContinuityFixture;
import cn.ninth.novel.memory.fixture.ContinuityFixtureFactory;
import cn.ninth.novel.memory.fixture.FixtureChapterVersion;
import cn.ninth.novel.memory.fixture.FixtureLifecycle;
import cn.ninth.novel.memory.fixture.FixtureMemoryItem;
import cn.ninth.novel.memory.fixture.FixtureMemoryKind;
import cn.ninth.novel.memory.fixture.ScalabilityFixture;
import cn.ninth.novel.memory.fixture.ScalabilityFixtureFactory;
import cn.ninth.novel.memory.metrics.MemoryRetrievalMetricsFixtureFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FinalScalabilityRegressionTest {

    private static final List<String> DRAFT_LONG_TERM_IDS = List.of(
            "scale-world-rule-nocturne-ability",
            "scale-character-change-shenye",
            "scale-open-loop-missing-seal",
            "scale-open-loop-courier");

    private final MemoryContextProvider provider = new MemoryContextProvider();
    private final MemoryLifecycleManager lifecycle = new MemoryLifecycleManager();

    @Test
    void allScalabilityFixturesKeepDraftContextAndWorkingSetBounded() {
        List<Integer> contextItemCounts = new ArrayList<>();
        List<Integer> contextTokenCounts = new ArrayList<>();
        List<Integer> workingSetCounts = new ArrayList<>();

        for (ScalabilityFixture fixture : ScalabilityFixtureFactory.all()) {
            List<MemoryContextItem> candidates = MemoryRetrievalMetricsFixtureFactory
                    .contextItemsFor(fixture);
            long startedAt = System.nanoTime();
            MemoryContextPack pack = provider.provide(
                    draftQuery(),
                    cn.ninth.novel.domain.memory.model.MemoryBudgetSpec.defaults(),
                    candidates);
            long latencyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            List<MemoryAdmissionItem> workingSet = lifecycle.admit(
                    fixture.memoryItems().stream()
                            .map(this::toAdmissionItem)
                            .toList(),
                    draftQuery());

            contextItemCounts.add(pack.totalItemCount());
            contextTokenCounts.add(pack.tokenCount());
            workingSetCounts.add(workingSet.size());

            System.out.printf(
                    "Final scalability scale=%d contextItems=%d contextTokens=%d workingSet=%d "
                            + "candidates=%d latency=%dms%n",
                    fixture.chapterCount(),
                    pack.totalItemCount(),
                    pack.tokenCount(),
                    workingSet.size(),
                    fixture.candidates().size(),
                    latencyMillis);

            assertThat(latencyMillis)
                    .as("DRAFT retrieval latency at %d chapters", fixture.chapterCount())
                    .isLessThan(1_000L);
            assertThat(fixture.candidates()).hasSize(fixture.chapterCount());
            assertThat(pack.items()).extracting(MemoryContextItem::itemId)
                    .containsExactlyInAnyOrderElementsOf(DRAFT_LONG_TERM_IDS);
            assertThat(pack.items()).extracting(MemoryContextItem::itemId)
                    .doesNotContain("scale-resolved-side-story-caravan")
                    .doesNotContain("scale-ch001-ordinary-event", "scale-ch001-passerby",
                            "scale-ch001-food");
            assertThat(workingSet).extracting(MemoryAdmissionItem::itemId)
                    .containsExactlyInAnyOrderElementsOf(DRAFT_LONG_TERM_IDS);

            MemoryContextItem missingSeal = pack.items().stream()
                    .filter(item -> item.itemId().equals("scale-open-loop-missing-seal"))
                    .findFirst()
                    .orElseThrow();
            assertThat(missingSeal.sourceChapter()).isEqualTo(3);
        }

        assertThat(contextItemCounts).containsOnly(contextItemCounts.get(0));
        assertThat(contextTokenCounts).containsOnly(contextTokenCounts.get(0));
        assertThat(workingSetCounts).containsOnly(workingSetCounts.get(0));
        assertThat(workingSetCounts.get(0)).isEqualTo(DRAFT_LONG_TERM_IDS.size());
    }

    @Test
    void continuityFixtureRetainsAllFinalRegressionSignals() {
        ContinuityFixture fixture = ContinuityFixtureFactory.create();
        FixtureChapterVersion wrongVersion = fixture.chapter(9).versions().get(0);
        FixtureChapterVersion finalVersion = fixture.chapter(9).acceptedVersion();

        System.out.printf(
                "Final continuity chapters=%s time=%s<%s location=%s knowledge=%s "
                        + "ability=%s injury=%s sameOrigin=%s stale=%s->%s%n",
                fixture.chapters().stream().map(chapter -> chapter.chapterNumber()).toList(),
                fixture.chapter(9).acceptedVersion().storyTime(),
                fixture.chapter(10).acceptedVersion().storyTime(),
                fixture.memoryItem("continuity-c9-crystal-carriage").content(),
                fixture.memoryItem("continuity-c9-luyao-book-source").content(),
                fixture.memoryItem("continuity-c7-ability-rule").content(),
                fixture.memoryItem("continuity-c10-injury-continues").content(),
                fixture.memoryItems().stream()
                        .filter(item -> item.id().contains("same-origin-confirmed"))
                        .map(FixtureMemoryItem::sourceChapter)
                        .toList(),
                fixture.candidate(ContinuityFixtureFactory.WRONG_CHAPTER_9_CANDIDATE)
                        .candidateStatus(),
                fixture.candidate(ContinuityFixtureFactory.FINAL_CHAPTER_9_CANDIDATE)
                        .candidateStatus());

        assertThat(fixture.chapters()).extracting(chapter -> chapter.chapterNumber())
                .containsExactly(5, 6, 7, 8, 9, 10);
        assertThat(wrongVersion.accepted()).isFalse();
        assertThat(finalVersion.accepted()).isTrue();
        assertThat(fixture.chapter(9).acceptedVersion().storyTime())
                .isLessThan(fixture.chapter(10).acceptedVersion().storyTime());
        assertThat(fixture.memoryItem("continuity-c9-crystal-carriage").content())
                .contains("北上马车暗格");
        assertThat(fixture.memoryItem("continuity-c9-luyao-book-source").content())
                .contains("古籍", "镇印残卷");
        assertThat(fixture.memoryItem("continuity-c7-ability-rule").content())
                .contains("主动激活", "消耗一枚灵息");
        assertThat(fixture.memoryItem("continuity-c10-injury-continues").content())
                .contains("胸口伤势", "未愈");
        assertThat(fixture.memoryItems().stream()
                .filter(item -> item.id().contains("same-origin-confirmed"))
                .map(FixtureMemoryItem::sourceChapter)
                .toList()).containsExactly(8, 9, 10);
        assertThat(fixture.memoryItem("continuity-c9-wrong-crystal-location").lifecycle())
                .isEqualTo(FixtureLifecycle.INVALIDATED);
        assertThat(fixture.candidate(ContinuityFixtureFactory.WRONG_CHAPTER_9_CANDIDATE)
                .candidateStatus()).isEqualTo(cn.ninth.novel.memory.fixture.FixtureCandidateStatus.STALE);
        assertThat(fixture.candidate(ContinuityFixtureFactory.FINAL_CHAPTER_9_CANDIDATE)
                .candidateStatus()).isEqualTo(cn.ninth.novel.memory.fixture.FixtureCandidateStatus.READY_FOR_GATE);
        assertThat(fixture.candidate(ContinuityFixtureFactory.FINAL_CHAPTER_9_CANDIDATE)
                .supersedesCandidateId()).isEqualTo(ContinuityFixtureFactory.WRONG_CHAPTER_9_CANDIDATE);
    }

    private MemoryQuerySpec draftQuery() {
        return new MemoryQuerySpec(
                MemoryProfile.DRAFT,
                Set.of("scale-character-change-shenye"),
                Set.of("scale-character-change-shenye"),
                Set.of("scale-open-loop-missing-seal", "scale-open-loop-courier"),
                Set.of("scale-world-rule-nocturne-ability"),
                Set.of());
    }

    private MemoryAdmissionItem toAdmissionItem(FixtureMemoryItem item) {
        return switch (item.kind()) {
            case WORLD_RULE -> MemoryAdmissionItem.worldRule(item.id());
            case OPEN_LOOP -> MemoryAdmissionItem.openLoop(
                    item.id(), item.lifecycle() == FixtureLifecycle.RESOLVED);
            case CHARACTER_CHANGE -> MemoryAdmissionItem.currentState(item.id());
            case FACT, CHARACTER_KNOWLEDGE, STATE -> MemoryAdmissionItem.fact(
                    item.id(), factStatus(item.lifecycle()));
            default -> MemoryAdmissionItem.event(
                    item.id(), item.lifecycle() != FixtureLifecycle.ACTIVE);
        };
    }

    private MemoryFactStatus factStatus(FixtureLifecycle lifecycle) {
        return switch (lifecycle) {
            case INVALIDATED -> MemoryFactStatus.FACT_INVALIDATED;
            case ARCHIVED, SUPERSEDED -> MemoryFactStatus.FACT_ARCHIVED;
            default -> MemoryFactStatus.FACT_ACTIVE;
        };
    }
}
