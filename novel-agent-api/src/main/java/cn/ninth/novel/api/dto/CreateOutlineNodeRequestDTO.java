package cn.ninth.novel.api.dto;

public record CreateOutlineNodeRequestDTO(
        String parentNodeCode,
        String title,
        String summary,
        Integer startChapter,
        Integer endChapter
) {
}
