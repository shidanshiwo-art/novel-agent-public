package cn.ninth.novel.api.dto;

/** Memory Retrieval Context 条目元数据；不包含正文。 */
public record MemoryRetrievalContextItemResponseDTO(
        String itemId,
        String category,
        String sourceType,
        int sourceChapter,
        String canonicalOrLegacy,
        int estimatedTokens,
        boolean selected,
        boolean trimmed,
        String decision
) {
}
