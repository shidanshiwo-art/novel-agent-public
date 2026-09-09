package cn.ninth.novel.domain.chapter.model;

import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChapterPlanModelTest {

    @Test
    void shouldExposePersistentChapterPlanModels() throws Exception {
        ChapterOutlineVO outline = new ChapterOutlineVO(
                12,
                "ARC_009",
                "钟楼残页",
                "林渊确认残页上的议会徽记来源。",
                "READY"
        );
        ChapterPlanEntity entity = ChapterPlanEntity.builder()
                .id(21L)
                .projectId(7L)
                .outlineNodeId(9L)
                .chapterNumber(outline.chapterNumber())
                .title(outline.title())
                .summary(outline.summary())
                .status(outline.status())
                .build();

        assertEquals(12, outline.chapterNumber());
        assertEquals("钟楼残页", entity.getTitle());
        assertEquals(9L, entity.getOutlineNodeId());
        assertEquals(
                List.of("id", "projectId", "outlineNodeId", "chapterNumber", "title", "summary", "status"),
                List.of(
                        field(ChapterPlanEntity.class, "id").getName(),
                        field(ChapterPlanEntity.class, "projectId").getName(),
                        field(ChapterPlanEntity.class, "outlineNodeId").getName(),
                        field(ChapterPlanEntity.class, "chapterNumber").getName(),
                        field(ChapterPlanEntity.class, "title").getName(),
                        field(ChapterPlanEntity.class, "summary").getName(),
                        field(ChapterPlanEntity.class, "status").getName()
                )
        );

        System.out.printf(
                "ChapterPlanModelTest plan chapter=%d title=%s entityId=%d%n",
                outline.chapterNumber(), outline.title(), entity.getId()
        );
    }

    private Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }
}
