package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/**
 * P0.2 Reconcile 的临时决策。
 *
 * <p>该对象只描述应执行的操作及其匹配来源，不直接修改 Fact/Event，也不执行
 * Canonical Commit。调用方仍需在后续门禁中决定是否接受 staging 结果。</p>
 */
public record MemoryOperationDecision(
        MemoryOperation operation,
        MemoryCandidate candidate,
        MemoryFact relatedFact,
        MemoryEvent relatedEvent,
        String reason
) {

    public MemoryOperationDecision {
        operation = Objects.requireNonNull(operation, "operation 不能为空");
        candidate = Objects.requireNonNull(candidate, "candidate 不能为空");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空");
        }
        reason = reason.trim();
    }

    public MemoryOperationDecision(
            MemoryOperation operation,
            MemoryCandidate candidate,
            MemoryFact relatedFact,
            String reason
    ) {
        this(operation, candidate, relatedFact, null, reason);
    }

    public MemoryOperationDecision(
            MemoryOperation operation,
            MemoryCandidate candidate,
            String reason
    ) {
        this(operation, candidate, null, null, reason);
    }

    public MemoryOperation getOperation() {
        return operation;
    }

    public MemoryCandidate getCandidate() {
        return candidate;
    }

    public MemoryFact getRelatedFact() {
        return relatedFact;
    }

    public MemoryEvent getRelatedEvent() {
        return relatedEvent;
    }

    public String getReason() {
        return reason;
    }
}
