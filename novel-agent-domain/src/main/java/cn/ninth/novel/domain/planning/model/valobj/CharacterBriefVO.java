package cn.ninth.novel.domain.planning.model.valobj;

/**
 * 提供给单章规划上下文的人物摘要，不暴露完整人物实体。
 */
public record CharacterBriefVO(
        String name,
        String role,
        String currentGoal,
        String currentLocation
) {
}
