package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

/** generation_metrics 表持久化对象。 */
@Data
public class GenerationMetricsPO {

    private Long projectId;
    private Integer chapterNumber;
    private String generationSessionId;
    private Integer draftCalls;
    private Integer reviewCalls;
    private Integer reviseCalls;
    private Integer compressionCalls;
    private Integer reviseRounds;
    private Integer retryCount;
    private Boolean humanIntervened;
    private Long generationDurationMs;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Integer finalWordCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
