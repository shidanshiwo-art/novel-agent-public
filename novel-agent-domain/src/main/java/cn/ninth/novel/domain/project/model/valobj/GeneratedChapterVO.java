package cn.ninth.novel.domain.project.model.valobj;

public record GeneratedChapterVO(
        Integer chapterNumber,
        String title,
        String content,
        Integer wordCount,
        String status
) {
}
