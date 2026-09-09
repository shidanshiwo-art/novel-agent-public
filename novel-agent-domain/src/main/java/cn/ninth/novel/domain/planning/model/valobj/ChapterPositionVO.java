package cn.ninth.novel.domain.planning.model.valobj;

/**
 * 当前章节在整部作品中的位置。
 */
public record ChapterPositionVO(
        Integer position,
        Integer total
) {
}
