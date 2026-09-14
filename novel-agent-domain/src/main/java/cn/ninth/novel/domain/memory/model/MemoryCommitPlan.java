package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/** Gate 完成校验后交给事务仓储的不可变提交计划。 */
public record MemoryCommitPlan(
        String projectCode,
        int chapterNumber,
        String finalContent,
        MemorySourceVersion finalSourceVersion,
        String commitKey,
        List<CanonicalEvent> events,
        List<CanonicalFact> facts,
        List<MemoryFactStatusChange> factStatusChanges,
        List<CanonicalProjection> projections,
        List<MemoryOutboxEntry> outboxEntries,
        List<MemoryConflict> nonBlockingConflicts
) {

    public MemoryCommitPlan {
        projectCode = required(projectCode, "projectCode");
        if (chapterNumber < 0) {
            throw new IllegalArgumentException("chapterNumber 不能小于 0");
        }
        finalContent = content(finalContent, "finalContent");
        finalSourceVersion = Objects.requireNonNull(
                finalSourceVersion, "finalSourceVersion 不能为空");
        commitKey = required(commitKey, "commitKey");
        events = immutableList(events, "events");
        facts = immutableList(facts, "facts");
        factStatusChanges = immutableList(factStatusChanges, "factStatusChanges");
        projections = immutableList(projections, "projections");
        outboxEntries = immutableList(outboxEntries, "outboxEntries");
        nonBlockingConflicts = immutableList(nonBlockingConflicts, "nonBlockingConflicts");
    }

    private static <T> List<T> immutableList(List<T> values, String field) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " 不能为空且不能包含 null");
        }
        return List.copyOf(values);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static String content(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
