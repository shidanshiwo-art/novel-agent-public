package cn.ninth.novel.api.dto;

public record NovelProjectResponseDTO(
        String projectCode,
        String title,
        String genre,
        Integer targetChapterCount,
        Integer wordsPerChapter,
        Integer currentChapterNumber,
        String status
) {
}
