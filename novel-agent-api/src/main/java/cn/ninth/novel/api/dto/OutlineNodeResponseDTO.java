package cn.ninth.novel.api.dto;

public record OutlineNodeResponseDTO(
        String nodeCode,
        String parentNodeCode,
        String nodeKind,
        Integer sequenceNo,
        Integer startChapter,
        Integer endChapter,
        String title,
        String summary,
        String status
) {
}
