package cn.ninth.novel.domain.planning.model.valobj;

import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;

public record OutlineNodeVO(
        String nodeCode,
        String parentNodeCode,
        OutlineNodeKindEnum nodeKind,
        Integer sequenceNo,
        String title,
        String summary,
        Integer startChapter,
        Integer endChapter,
        String status
) {
}
