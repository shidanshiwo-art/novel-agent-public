package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/** Gate 在同一事务中对既有 Fact 进行的状态变更。 */
public record MemoryFactStatusChange(
        String factId,
        MemoryFactStatus status,
        String reason
) {

    public MemoryFactStatusChange {
        if (factId == null || factId.isBlank()) {
            throw new IllegalArgumentException("factId 不能为空");
        }
        factId = factId.trim();
        status = Objects.requireNonNull(status, "status 不能为空");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空");
        }
        reason = reason.trim();
    }
}
