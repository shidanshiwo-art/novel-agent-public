package cn.ninth.novel.domain.planning.model.valobj;

import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineNodeVOTest {

    @Test
    void shouldExposeOnlyUnifiedOutlineNodeFields() {
        OutlineNodeVO node = new OutlineNodeVO(
                "ARC_001", "VOLUME_001", OutlineNodeKindEnum.ARC, 1,
                "第一剧情弧", "主角进入冲突中心", 1, 20, "PLANNED"
        );

        assertThat(EnumSet.allOf(OutlineNodeKindEnum.class))
                .containsExactly(
                        OutlineNodeKindEnum.BOOK,
                        OutlineNodeKindEnum.VOLUME,
                        OutlineNodeKindEnum.ARC
                );
        assertThat(node.nodeCode()).isEqualTo("ARC_001");
        assertThat(node.parentNodeCode()).isEqualTo("VOLUME_001");
        assertThat(node.nodeKind()).isEqualTo(OutlineNodeKindEnum.ARC);
        assertThat(node.sequenceNo()).isEqualTo(1);
        assertThat(node.title()).isEqualTo("第一剧情弧");
        assertThat(node.summary()).isEqualTo("主角进入冲突中心");
        assertThat(node.startChapter()).isEqualTo(1);
        assertThat(node.endChapter()).isEqualTo(20);
        assertThat(node.status()).isEqualTo("PLANNED");

        System.out.printf("outline node model: kind=%s, code=%s, range=%d-%d%n",
                node.nodeKind(), node.nodeCode(), node.startChapter(), node.endChapter());
    }
}
