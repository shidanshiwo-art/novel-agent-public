package cn.ninth.novel.api.dto;

public record ChapterPlanResponseDTO(
        Integer chapterNumber,
        String outlineNodeCode,
        String title,
        String summary,
        String status
) {
}
