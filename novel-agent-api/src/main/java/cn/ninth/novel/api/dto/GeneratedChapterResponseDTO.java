package cn.ninth.novel.api.dto;

public record GeneratedChapterResponseDTO(
        Integer chapterNumber,
        String title,
        String content,
        Integer wordCount,
        String status
) {
}
