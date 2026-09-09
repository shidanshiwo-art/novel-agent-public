package cn.ninth.novel.api.dto;

public record CreateNovelProjectRequestDTO(
        String projectCode,
        String title,
        String genre,
        Integer targetChapterCount,
        Integer wordsPerChapter
) {
}
