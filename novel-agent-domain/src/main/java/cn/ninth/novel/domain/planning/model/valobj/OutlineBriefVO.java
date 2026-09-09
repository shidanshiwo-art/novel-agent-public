package cn.ninth.novel.domain.planning.model.valobj;

/**
 * 提供给单章规划上下文的大纲摘要，不包含节点身份和状态信息。
 */
public record OutlineBriefVO(
        String title,
        String summary,
        Integer startChapter,
        Integer endChapter
) {
}
