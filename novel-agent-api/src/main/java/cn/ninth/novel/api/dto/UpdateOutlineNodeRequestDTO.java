package cn.ninth.novel.api.dto;

public record UpdateOutlineNodeRequestDTO(
        String parentNodeCode,
        String nodeKind,
        Integer sequenceNo,
        String title,
        String summary,
        Integer startChapter,
        Integer endChapter,
        String status
) {
}
