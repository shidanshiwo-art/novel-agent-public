package cn.ninth.novel.api.dto;

public record ChapterSearchResultResponseDTO(
        Integer chapterNumber,
        String title,
        String snippet,
        Integer matchCount
) {
}
