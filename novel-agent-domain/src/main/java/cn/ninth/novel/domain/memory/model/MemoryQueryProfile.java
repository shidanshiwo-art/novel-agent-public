package cn.ninth.novel.domain.memory.model;

/**
 * P0.1 旧版 Profile 名称，仅用于兼容已有调用方。
 *
 * @deprecated 使用 {@link MemoryProfile}。
 */
@Deprecated
public enum MemoryQueryProfile {
    PLAN,
    DRAFT,
    REVIEW
}
