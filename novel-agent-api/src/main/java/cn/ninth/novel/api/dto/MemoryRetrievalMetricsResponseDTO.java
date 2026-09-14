package cn.ninth.novel.api.dto;

import java.util.List;

/** 一次 PLAN/DRAFT/REVIEW Memory Retrieval 观测；不包含完整 Prompt 或正文。 */
public record MemoryRetrievalMetricsResponseDTO(
        String projectCode,
        int chapterNumber,
        String profile,
        String generationId,
        String memoryMode,
        boolean canonicalRequested,
        boolean canonicalHit,
        boolean legacyFallbackRequested,
        boolean legacyFallbackHit,
        int candidateCount,
        int filteredCount,
        int selectedCount,
        int trimmedCount,
        long retrievalLatencyMillis,
        long estimatedTokens,
        int notFoundCount,
        int retrievalMissCount,
        int intentionalTrimCount,
        int budgetTrimCount,
        List<MemoryRetrievalContextItemResponseDTO> items
) {
}
