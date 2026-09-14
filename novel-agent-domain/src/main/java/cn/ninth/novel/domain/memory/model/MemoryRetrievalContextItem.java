package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/** 不含正文的单个 Context 检索观测。 */
public record MemoryRetrievalContextItem(
        String itemId,
        MemoryContextCategory category,
        String sourceType,
        int sourceChapter,
        String canonicalOrLegacy,
        int estimatedTokens,
        boolean selected,
        boolean trimmed,
        String decision
) {

    public MemoryRetrievalContextItem {
        itemId = required(itemId, "itemId");
        category = Objects.requireNonNull(category, "category 不能为空");
        sourceType = required(sourceType, "sourceType");
        canonicalOrLegacy = required(canonicalOrLegacy, "canonicalOrLegacy");
        if (sourceChapter < 0) {
            throw new IllegalArgumentException("sourceChapter 不能小于 0");
        }
        if (estimatedTokens < 0) {
            throw new IllegalArgumentException("estimatedTokens 不能小于 0");
        }
        if (selected == trimmed) {
            throw new IllegalArgumentException("selected 与 trimmed 必须一真一假");
        }
        decision = required(decision, "decision");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
