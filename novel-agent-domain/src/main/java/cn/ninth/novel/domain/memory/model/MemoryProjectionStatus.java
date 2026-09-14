package cn.ninth.novel.domain.memory.model;

/** 派生投影自身的生命周期状态，不代表来源事实的真伪状态。 */
public enum MemoryProjectionStatus {
    PROJECTION_ACTIVE,
    PROJECTION_STALE,
    PROJECTION_ARCHIVED
}
