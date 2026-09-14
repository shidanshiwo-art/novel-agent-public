package cn.ninth.novel.infrastructure.experiment;

import java.util.List;

/** V1-current/V1-improved 章节运行的机器可读报告。 */
public record V1ComparisonReport(
        V1ComparisonManifest manifest,
        List<ChapterResult> chapters
) {

    public V1ComparisonReport {
        if (manifest == null) {
            throw new IllegalArgumentException("实验清单不能为空");
        }
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }

    public record ChapterResult(
            int chapter,
            String variant,
            String projectCode,
            String generationRunId,
            int draftCalls,
            int reviewCalls,
            int reviseCalls,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Long latencyMs,
            MemoryAbExperimentReport.MemoryContextMetrics memoryContext,
            CanonicalGrowth canonicalGrowth,
            ReconcileMetrics reconcileMetrics,
            PreviousCanonicalRecall previousCanonicalRecall,
            String finalStatus
    ) {

        public ChapterResult {
            if (chapter < 1 || variant == null || variant.isBlank()
                    || projectCode == null || projectCode.isBlank()
                    || finalStatus == null || finalStatus.isBlank()) {
                throw new IllegalArgumentException("章节对照结果字段不完整");
            }
            memoryContext = memoryContext == null
                    ? MemoryAbExperimentReport.MemoryContextMetrics.empty() : memoryContext;
            canonicalGrowth = canonicalGrowth == null
                    ? CanonicalGrowth.empty() : canonicalGrowth;
            reconcileMetrics = reconcileMetrics == null
                    ? ReconcileMetrics.empty() : reconcileMetrics;
            previousCanonicalRecall = previousCanonicalRecall == null
                    ? PreviousCanonicalRecall.none() : previousCanonicalRecall;
            if (draftCalls < 0 || reviewCalls < 0 || reviseCalls < 0) {
                throw new IllegalArgumentException("模型调用次数不能为负数");
            }
        }
    }

    public record CanonicalGrowth(
            long commitCount,
            long acceptedVersionCount,
            long eventCount,
            long factCount,
            long projectionCount,
            long outboxCount
    ) {

        public CanonicalGrowth {
            if (commitCount < 0 || acceptedVersionCount < 0 || eventCount < 0
                    || factCount < 0 || projectionCount < 0 || outboxCount < 0) {
                throw new IllegalArgumentException("Canonical growth 不能为负数");
            }
        }

        public static CanonicalGrowth empty() {
            return new CanonicalGrowth(0, 0, 0, 0, 0, 0);
        }
    }

    /** Gate 成功提交后记录的 Reconcile 操作统计。 */
    public record ReconcileMetrics(
            long addCount,
            long reinforceCount,
            long supersedeCount,
            long invalidateCount,
            long noopCount
    ) {

        public ReconcileMetrics {
            if (addCount < 0 || reinforceCount < 0 || supersedeCount < 0
                    || invalidateCount < 0 || noopCount < 0) {
                throw new IllegalArgumentException("Reconcile 指标不能为负数");
            }
        }

        public long totalCount() {
            return addCount + reinforceCount + supersedeCount + invalidateCount + noopCount;
        }

        public double reinforceNoopRatio() {
            return totalCount() == 0
                    ? 0.0d
                    : (double) (reinforceCount + noopCount) / totalCount();
        }

        public static ReconcileMetrics empty() {
            return new ReconcileMetrics(0, 0, 0, 0, 0);
        }
    }

    public record PreviousCanonicalRecall(
            int sourceChapter,
            boolean canonicalSelected,
            long selectedItems,
            long contextTokens
    ) {

        public PreviousCanonicalRecall {
            if (sourceChapter < 0 || selectedItems < 0 || contextTokens < 0) {
                throw new IllegalArgumentException("Canonical recall 指标无效");
            }
        }

        public static PreviousCanonicalRecall none() {
            return new PreviousCanonicalRecall(0, false, 0, 0);
        }
    }
}
