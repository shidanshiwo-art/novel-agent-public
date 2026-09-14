package cn.ninth.novel.memory.fixture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryFixtureFactoryTest {

    @Test
    void continuityFixtureContainsChapterFiveToTenRegressionCases() {
        ContinuityFixture fixture = ContinuityFixtureFactory.create();

        assertThat(fixture.chapters())
                .extracting(FixtureChapter::chapterNumber)
                .containsExactly(5, 6, 7, 8, 9, 10);
        assertThat(fixture.chapter(9).versions()).hasSize(2);
        assertThat(fixture.chapter(9).acceptedVersion().versionId())
                .isEqualTo(ContinuityFixtureFactory.FINAL_CHAPTER_9_VERSION);

        assertThat(fixture.memoryItem("continuity-c5-crystal-basement").content())
                .contains("地下室");
        assertThat(fixture.memoryItem("continuity-c7-crystal-carriage").content())
                .contains("马车暗格");
        assertThat(fixture.memoryItem("continuity-c6-luyao-knowledge").content())
                .contains("古籍");
        assertThat(fixture.memoryItem("continuity-c7-ability-rule").content())
                .contains("主动激活", "消耗一枚灵息");
        assertThat(fixture.memoryItem("continuity-c8-activate-ability").content())
                .contains("主动激活", "消耗一枚灵息");
        assertThat(fixture.memoryItem("continuity-c10-injury-continues").content())
                .contains("仍未愈合");

        List<FixtureMemoryItem> sameOriginConfirmations = fixture.memoryItems().stream()
                .filter(item -> item.id().contains("same-origin-confirmed"))
                .toList();
        assertThat(sameOriginConfirmations)
                .extracting(FixtureMemoryItem::sourceChapter)
                .containsExactly(8, 9, 10);

        FixtureMemoryItem wrongMemory = fixture.memoryItem("continuity-c9-wrong-crystal-location");
        assertThat(wrongMemory.lifecycle()).isEqualTo(FixtureLifecycle.INVALIDATED);
        assertThat(wrongMemory.sourceVersion())
                .isEqualTo(ContinuityFixtureFactory.WRONG_CHAPTER_9_VERSION);
        assertThat(fixture.candidate(ContinuityFixtureFactory.WRONG_CHAPTER_9_CANDIDATE).candidateStatus())
                .isEqualTo(FixtureCandidateStatus.STALE);
        assertThat(fixture.candidate(ContinuityFixtureFactory.FINAL_CHAPTER_9_CANDIDATE).candidateStatus())
                .isEqualTo(FixtureCandidateStatus.READY_FOR_GATE);
        assertThat(fixture.candidate(ContinuityFixtureFactory.FINAL_CHAPTER_9_CANDIDATE).supersedesCandidateId())
                .isEqualTo(ContinuityFixtureFactory.WRONG_CHAPTER_9_CANDIDATE);
        assertThat(fixture.chapter(9).acceptedVersion().contentHash())
                .isEqualTo(fixture.candidate(ContinuityFixtureFactory.FINAL_CHAPTER_9_CANDIDATE).contentHash());

        String chapterNineTime = fixture.chapter(9).acceptedVersion().storyTime();
        String chapterTenTime = fixture.chapter(10).acceptedVersion().storyTime();
        assertThat(chapterNineTime).isLessThan(chapterTenTime);
        System.out.printf(
                "Continuity fixture chapters=%s sameOriginConfirmations=%d staleCandidate=%s time=%s<%s%n",
                fixture.chapters().stream().map(FixtureChapter::chapterNumber).toList(),
                sameOriginConfirmations.size(),
                ContinuityFixtureFactory.WRONG_CHAPTER_9_CANDIDATE,
                chapterNineTime,
                chapterTenTime);
    }

    @Test
    void scalabilityFixturesCoverAllRequestedSizesAndNoiseShapes() {
        List<ScalabilityFixture> fixtures = ScalabilityFixtureFactory.all();

        assertThat(fixtures).extracting(ScalabilityFixture::chapterCount)
                .containsExactly(10, 50, 100, 200, 300);
        for (ScalabilityFixture fixture : fixtures) {
            int chapterCount = fixture.chapterCount();
            assertThat(fixture.chapters()).hasSize(chapterCount);
            assertThat(fixture.itemsOfKind(FixtureMemoryKind.EVENT))
                    .hasSizeGreaterThanOrEqualTo(chapterCount);
            assertThat(fixture.memoryItems().stream()
                    .filter(item -> item.subject().equals("普通路人")))
                    .hasSize(chapterCount);
            assertThat(fixture.memoryItems().stream()
                    .filter(item -> item.subject().equals("普通饮食")))
                    .hasSize(chapterCount);
            assertThat(fixture.itemsOfKind(FixtureMemoryKind.OPEN_LOOP)).hasSize(2);
            assertThat(fixture.itemsOfKind(FixtureMemoryKind.WORLD_RULE)).hasSize(1);
            assertThat(fixture.itemsOfKind(FixtureMemoryKind.CHARACTER_CHANGE)).hasSize(1);
            assertThat(fixture.itemsOfKind(FixtureMemoryKind.CONSOLIDATED)).hasSize(1);
            assertThat(fixture.memoryItems().stream()
                    .filter(item -> !item.longTerm()))
                    .hasSizeGreaterThanOrEqualTo(chapterCount * 3);
            assertThat(fixture.memoryItems().stream()
                    .filter(FixtureMemoryItem::longTerm)
                    .map(FixtureMemoryItem::subject))
                    .contains("失踪封印", "失联信使", "夜行能力", "沈夜");

            System.out.printf(
                    "Scalability fixture chapters=%d memories=%d ordinaryEvents=%d openLoops=%d noise=%d%n",
                    chapterCount,
                    fixture.memoryItems().size(),
                    fixture.itemsOfKind(FixtureMemoryKind.EVENT).size(),
                    fixture.itemsOfKind(FixtureMemoryKind.OPEN_LOOP).size(),
                    fixture.memoryItems().stream().filter(item -> !item.longTerm()).count());
        }
    }
}
