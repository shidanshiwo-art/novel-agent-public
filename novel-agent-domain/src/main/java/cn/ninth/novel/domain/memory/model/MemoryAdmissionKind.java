package cn.ninth.novel.domain.memory.model;

/** staging 工作集中的最小记忆类别。 */
public enum MemoryAdmissionKind {
    EVENT,
    FACT,
    CURRENT_STATE,
    OPEN_LOOP,
    WORLD_RULE,
    PROJECTION
}
