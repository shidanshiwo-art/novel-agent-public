package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.domain.memory.model.MemoryMode;

import java.util.List;

/** V1-current/V1-improved 数据隔离复制结果。 */
public record V1ComparisonIsolationReport(
        String experimentId,
        List<Environment> environments
) {

    public V1ComparisonIsolationReport {
        environments = environments == null ? List.of() : List.copyOf(environments);
    }

    public record Environment(
            String variant,
            String projectCode,
            MemoryMode memoryMode,
            long finalizedChapterCount,
            long outlineCount,
            long chapterPlanCount,
            long canonicalCommitCount,
            long acceptedVersionCount,
            long eventCount,
            long factCount,
            long projectionCount,
            long generationMetricCount,
            long retrievalMetricCount,
            long promptTraceCount
    ) {
    }
}
