package cn.ninth.novel.domain.memory.model;

/** 生产 Memory Retrieval 观测查询条件；未提供的过滤条件表示不限。 */
public record MemoryRetrievalMetricsQuery(
        String projectCode,
        Integer chapterNumber,
        MemoryProfile profile,
        String generationId
) {

    public MemoryRetrievalMetricsQuery {
        if (projectCode == null || projectCode.isBlank()) {
            throw new IllegalArgumentException("projectCode 不能为空");
        }
        projectCode = projectCode.trim();
        if (chapterNumber != null && chapterNumber < 1) {
            throw new IllegalArgumentException("chapterNumber 必须为正数");
        }
        generationId = generationId == null || generationId.isBlank()
                ? null : generationId.trim();
    }
}
