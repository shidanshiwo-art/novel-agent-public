package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

/** generation_metrics 项目汇总查询结果。 */
@Data
public class GenerationMetricsSummaryPO {

    private Long sessionCount;
    private Long draftCalls;
    private Long reviewCalls;
    private Long reviseCalls;
    private Long compressionCalls;
    private Long reviseRounds;
    private Long retryCount;
    private Long humanIntervenedCount;
    private Long generationDurationMs;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long finalWordCount;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
