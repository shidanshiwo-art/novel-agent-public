package cn.ninth.novel.domain.memory.model;

/** Canonical Commit 的事务结果。 */
public record MemoryCommitResult(
        String commitKey,
        boolean alreadyCommitted,
        int eventCount,
        int factCount,
        int projectionCount,
        int outboxCount
) {

    public MemoryCommitResult {
        if (commitKey == null || commitKey.isBlank()) {
            throw new IllegalArgumentException("commitKey 不能为空");
        }
        if (eventCount < 0 || factCount < 0 || projectionCount < 0 || outboxCount < 0) {
            throw new IllegalArgumentException("提交计数不能小于 0");
        }
    }

    public static MemoryCommitResult alreadyCommitted(String commitKey) {
        return new MemoryCommitResult(commitKey, true, 0, 0, 0, 0);
    }
}
