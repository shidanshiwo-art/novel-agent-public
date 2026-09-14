package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/**
 * Canonical Gate 的完整输入。所有来源均来自最终正文和当前工作流的 staging。
 */
public record MemoryCommitRequest(
        String projectCode,
        int chapterNumber,
        String finalContent,
        MemorySourceVersion finalSourceVersion,
        List<MemoryCandidate> candidates,
        List<MemoryOperationDecision> decisions,
        List<CanonicalProjection> projections,
        List<MemoryConflict> conflicts,
        boolean acceptCurrent,
        String commitKey
) {

    public MemoryCommitRequest {
        projectCode = required(projectCode, "projectCode");
        if (chapterNumber < 0) {
            throw new IllegalArgumentException("chapterNumber 不能小于 0");
        }
        finalContent = content(finalContent, "finalContent");
        finalSourceVersion = Objects.requireNonNull(
                finalSourceVersion, "finalSourceVersion 不能为空");
        candidates = immutableList(candidates, "candidates");
        decisions = immutableList(decisions, "decisions");
        projections = immutableList(projections, "projections");
        conflicts = immutableList(conflicts, "conflicts");
        commitKey = required(commitKey, "commitKey");
    }

    public static MemoryCommitRequest forCandidates(
            String projectCode,
            int chapterNumber,
            String finalContent,
            MemorySourceVersion finalSourceVersion,
            List<MemoryCandidate> candidates
    ) {
        return new MemoryCommitRequest(
                projectCode, chapterNumber, finalContent, finalSourceVersion,
                candidates, List.of(), List.of(), List.of(), false,
                defaultCommitKey(projectCode, chapterNumber, finalSourceVersion));
    }

    public MemoryCommitRequest withDecisions(List<MemoryOperationDecision> value) {
        return new MemoryCommitRequest(
                projectCode, chapterNumber, finalContent, finalSourceVersion,
                candidates, value, projections, conflicts, acceptCurrent, commitKey);
    }

    public MemoryCommitRequest withAcceptCurrent(boolean value) {
        return new MemoryCommitRequest(
                projectCode, chapterNumber, finalContent, finalSourceVersion,
                candidates, decisions, projections, conflicts, value, commitKey);
    }

    private static String defaultCommitKey(
            String projectCode,
            int chapterNumber,
            MemorySourceVersion sourceVersion
    ) {
        return projectCode + ":" + chapterNumber + ":"
                + sourceVersion.chapterVersion() + ":" + sourceVersion.contentHash();
    }

    private static <T> List<T> immutableList(List<T> values, String field) {
        if (values == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " 不能包含 null");
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
