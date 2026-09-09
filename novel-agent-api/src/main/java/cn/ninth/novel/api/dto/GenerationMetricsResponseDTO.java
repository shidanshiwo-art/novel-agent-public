package cn.ninth.novel.api.dto;

import java.time.LocalDateTime;

/** 单次章节生成会话的指标查询结果。 */
public record GenerationMetricsResponseDTO(
        String projectId,
        Integer chapterNumber,
        String generationSessionId,
        Integer draftCalls,
        Integer reviewCalls,
        Integer reviseCalls,
        Integer compressionCalls,
        Integer reviseRounds,
        Integer retryCount,
        Boolean humanIntervened,
        Long generationDurationMs,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        Integer finalWordCount,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
