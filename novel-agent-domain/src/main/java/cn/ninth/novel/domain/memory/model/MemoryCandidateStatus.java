package cn.ninth.novel.domain.memory.model;

/** Candidate 的临时生命周期状态，不表示 Canonical 状态。 */
public enum MemoryCandidateStatus {
    PROVISIONAL,
    STALE,
    REJECTED,
    READY_FOR_GATE
}
