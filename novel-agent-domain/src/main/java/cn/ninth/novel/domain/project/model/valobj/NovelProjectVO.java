package cn.ninth.novel.domain.project.model.valobj;

public record NovelProjectVO(
        String projectCode,
        String title,
        String genre,
        /** 当前创作阶段的预计章节数，达到后需由用户调整才能继续规划。 */
        Integer targetChapterCount,
        Integer wordsPerChapter,
        Integer currentChapterNumber,
        String status
) {
}
