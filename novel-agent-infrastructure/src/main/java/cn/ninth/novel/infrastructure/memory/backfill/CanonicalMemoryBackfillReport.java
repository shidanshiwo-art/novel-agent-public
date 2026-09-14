package cn.ninth.novel.infrastructure.memory.backfill;

import java.util.List;

/**
 * Canonical Memory backfill 的可审计结果。
 *
 * <p>openLoopCount 表示使用允许的 Canonical Projection 表达、且仍未解决的
 * 正文线索数量。V1 当前没有独立 Open Loop 物理表，因此这里不会把线索伪装成
 * Fact。</p>
 */
public record CanonicalMemoryBackfillReport(
        String projectCode,
        int fromChapter,
        int toChapter,
        int expectedAcceptedChapters,
        int acceptedChapterCount,
        int eventCount,
        int factCount,
        int openLoopCount,
        int projectionCount,
        int rejectedCandidateCount,
        int invalidCandidateCount,
        List<Integer> chaptersWithoutValidCanonicalMemory,
        List<ChapterReport> chapters
) {

    public CanonicalMemoryBackfillReport {
        chaptersWithoutValidCanonicalMemory = chaptersWithoutValidCanonicalMemory == null
                ? List.of() : List.copyOf(chaptersWithoutValidCanonicalMemory);
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }

    public int coveragePercent() {
        if (expectedAcceptedChapters == 0) {
            return 100;
        }
        return acceptedChapterCount * 100 / expectedAcceptedChapters;
    }

    public record ChapterReport(
            int chapterNumber,
            String sourceVersion,
            String contentHash,
            boolean accepted,
            int candidateCount,
            int eventCount,
            int factCount,
            int openLoopCount,
            int projectionCount,
            int rejectedCandidateCount,
            int invalidCandidateCount
    ) {
    }
}
