package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/**
 * 一次生产检索请求的旁路观测。
 *
 * <p>该对象只含路由、计数和 Context 元数据，禁止携带 Context 正文。</p>
 */
public record MemoryRetrievalObservation(
        String projectCode,
        int chapterNumber,
        MemoryProfile profile,
        String generationId,
        MemoryRetrievalRoute route,
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
        List<MemoryRetrievalContextItem> items
) {

    public MemoryRetrievalObservation {
        projectCode = required(projectCode, "projectCode");
        if (chapterNumber < 1) {
            throw new IllegalArgumentException("chapterNumber 必须为正数");
        }
        profile = Objects.requireNonNull(profile, "profile 不能为空");
        generationId = generationId == null || generationId.isBlank()
                ? null : generationId.trim();
        route = Objects.requireNonNull(route, "route 不能为空");
        if (candidateCount < 0 || filteredCount < 0 || selectedCount < 0
                || trimmedCount < 0 || retrievalLatencyMillis < 0 || estimatedTokens < 0
                || notFoundCount < 0 || retrievalMissCount < 0
                || intentionalTrimCount < 0 || budgetTrimCount < 0) {
            throw new IllegalArgumentException("retrieval metrics 不能包含负数");
        }
        if (selectedCount + trimmedCount != filteredCount) {
            throw new IllegalArgumentException(
                    "selectedCount + trimmedCount 必须等于 filteredCount");
        }
        if (intentionalTrimCount + filteredCount != candidateCount) {
            throw new IllegalArgumentException(
                    "intentionalTrimCount + filteredCount 必须等于 candidateCount");
        }
        if (trimmedCount != budgetTrimCount) {
            throw new IllegalArgumentException(
                    "trimmedCount 必须等于 budgetTrimCount");
        }
        items = immutable(items);
    }

    private static List<MemoryRetrievalContextItem> immutable(
            List<MemoryRetrievalContextItem> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        values.forEach(item -> Objects.requireNonNull(item, "items 不能包含 null"));
        return List.copyOf(values);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
