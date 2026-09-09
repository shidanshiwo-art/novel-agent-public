package cn.ninth.novel.domain.project.model.valobj;

import java.util.List;

public record VolumeChapterGroupVO(
        String volumeCode,
        Integer sequenceNo,
        String title,
        Integer startChapter,
        Integer endChapter,
        String status,
        List<GeneratedChapterVO> chapters
) {
    public VolumeChapterGroupVO {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }
}
