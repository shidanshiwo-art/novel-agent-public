package cn.ninth.novel.domain.planning.model.valobj;

/**
 * 单章计划 Prompt 使用的 Story Bible 创作语义投影。
 *
 * <p>不携带数据库字段或 JSON 存储形式，只保留模型规划本章所需的故事设定。</p>
 */
public record StoryBibleBriefVO(
        String oneSentencePremise,
        String coreTheme,
        String mainConflict,
        String endingDirection,
        String worldBackground,
        String powerSystem,
        String styleGuide
) {
}
