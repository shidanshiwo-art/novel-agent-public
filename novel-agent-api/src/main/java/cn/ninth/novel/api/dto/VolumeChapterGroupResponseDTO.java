package cn.ninth.novel.api.dto;

import java.util.List;

public record VolumeChapterGroupResponseDTO(
        String volumeCode,
        Integer sequenceNo,
        String title,
        Integer startChapter,
        Integer endChapter,
        String status,
        List<GeneratedChapterResponseDTO> chapters
) {
}
