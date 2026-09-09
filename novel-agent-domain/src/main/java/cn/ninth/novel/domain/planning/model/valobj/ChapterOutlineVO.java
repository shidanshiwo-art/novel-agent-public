package cn.ninth.novel.domain.planning.model.valobj;

/**
 * 持久化章节章纲的业务查询值对象。
 * outlineNodeCode 由持久化关联查询组装，不属于模型生成内容。
 */
public record ChapterOutlineVO(
        Integer chapterNumber,
        String outlineNodeCode,
        String title,
        String summary,
        String status
) {
}
