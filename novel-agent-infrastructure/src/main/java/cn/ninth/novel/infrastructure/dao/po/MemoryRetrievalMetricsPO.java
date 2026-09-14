package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

/** memory_retrieval_metrics 表持久化对象；contextItemsJson 只含元数据。 */
@Data
public class MemoryRetrievalMetricsPO {

    private Long id;
    private String projectCode;
    private Integer chapterNumber;
    private String profile;
    private String generationId;
    private String memoryMode;
    private Boolean canonicalRequested;
    private Boolean canonicalHit;
    private Boolean legacyFallbackRequested;
    private Boolean legacyFallbackHit;
    private Integer candidateCount;
    private Integer filteredCount;
    private Integer selectedCount;
    private Integer trimmedCount;
    private Long retrievalLatencyMs;
    private Long estimatedTokens;
    private Integer notFoundCount;
    private Integer retrievalMissCount;
    private Integer intentionalTrimCount;
    private Integer budgetTrimCount;
    private String contextItemsJson;
    private LocalDateTime createdAt;
}
