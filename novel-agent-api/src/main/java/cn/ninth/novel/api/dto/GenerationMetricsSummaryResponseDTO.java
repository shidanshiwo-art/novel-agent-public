package cn.ninth.novel.api.dto;

import java.time.LocalDateTime;

/** 项目范围的章节生成指标汇总结果。 */
public record GenerationMetricsSummaryResponseDTO(
        String projectCode,
        long totalGeneratedChapters,
        double averageGenerationDurationMs,
        double averageReviewCalls,
        double averageReviseRounds,
        long hitlCount,
        double hitlRate,
        Long totalTokenUsage,
        double averageFinalWordCount,
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
}
