package cn.ninth.novel.memory.fixture;

/**
 * Temporary candidate status. This is intentionally separate from canonical lifecycle.
 */
public enum FixtureCandidateStatus {
    PROVISIONAL,
    STALE,
    REJECTED,
    READY_FOR_GATE
}
