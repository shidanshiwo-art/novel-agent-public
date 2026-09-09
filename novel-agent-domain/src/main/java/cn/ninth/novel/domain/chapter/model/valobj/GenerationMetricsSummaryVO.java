package cn.ninth.novel.domain.chapter.model.valobj;

import java.time.LocalDateTime;

/** 项目范围的章节生成指标汇总。 */
public record GenerationMetricsSummaryVO(
        long sessionCount,
        long draftCalls,
        long reviewCalls,
        long reviseCalls,
        long compressionCalls,
        long reviseRounds,
        long retryCount,
        long humanIntervenedCount,
        long generationDurationMs,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        long finalWordCount,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {

    public static GenerationMetricsSummaryVO empty() {
        return new GenerationMetricsSummaryVO(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, null, null, null, 0L, null, null
        );
    }

    public long hitlCount() {
        return humanIntervenedCount;
    }
}
