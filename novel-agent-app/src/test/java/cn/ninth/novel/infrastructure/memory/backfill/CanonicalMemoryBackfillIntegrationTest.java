package cn.ninth.novel.infrastructure.memory.backfill;

import cn.ninth.novel.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 明确开启后才对真实项目执行一次性 backfill；默认测试不会写入 novel-001。
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "runCanonicalBackfill", matches = "true")
class CanonicalMemoryBackfillIntegrationTest {

    @Autowired
    private CanonicalMemoryBackfillService backfillService;

    @Test
    void shouldBackfillNovel001ChaptersOneToTenThroughCanonicalGate() {
        CanonicalMemoryBackfillReport report = backfillService.backfill("novel-001", 1, 10);

        System.out.printf(
                "Canonical backfill novel-001 1~10：accepted=%d/%d(%d%%), "
                        + "events=%d, facts=%d, openLoops=%d, projections=%d, "
                        + "rejected=%d, invalid=%d, missing=%s%n",
                report.acceptedChapterCount(), report.expectedAcceptedChapters(),
                report.coveragePercent(), report.eventCount(), report.factCount(),
                report.openLoopCount(), report.projectionCount(),
                report.rejectedCandidateCount(), report.invalidCandidateCount(),
                report.chaptersWithoutValidCanonicalMemory());
        report.chapters().forEach(chapter -> System.out.printf(
                "  chapter=%d accepted=%s candidates=%d events=%d facts=%d "
                        + "openLoops=%d projections=%d version=%s hash=%s%n",
                chapter.chapterNumber(), chapter.accepted(), chapter.candidateCount(),
                chapter.eventCount(), chapter.factCount(), chapter.openLoopCount(),
                chapter.projectionCount(), chapter.sourceVersion(), chapter.contentHash()));

        assertThat(report.acceptedChapterCount()).isEqualTo(10);
        assertThat(report.coveragePercent()).isEqualTo(100);
        assertThat(report.chaptersWithoutValidCanonicalMemory()).isEmpty();
        assertThat(report.eventCount()).isGreaterThanOrEqualTo(10);
        assertThat(report.factCount()).isGreaterThan(0);
        assertThat(report.projectionCount()).isGreaterThan(0);
        assertThat(report.rejectedCandidateCount()).isZero();
        assertThat(report.invalidCandidateCount()).isZero();
    }
}
