package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.domain.memory.model.MemoryMode;

import java.util.List;

/** 复制完成后的隔离性检查结果。 */
public record MemoryAbIsolationReport(
        String experimentId,
        List<Environment> environments
) {

    public MemoryAbIsolationReport {
        environments = environments == null ? List.of() : List.copyOf(environments);
    }

    public record Environment(
            String group,
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
