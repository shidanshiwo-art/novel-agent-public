package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.planning.service.IPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class ManualOutlineCrudHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldExposeManualOutlineCrudRoutes() throws Exception {
        OutlineNodeVO book = node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        );
        OutlineNodeVO volume = node(
                "volume", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "卷"
        );
        when(planningService.listOutlineTree("novel-001")).thenReturn(List.of(book, volume));
        when(planningService.createOutlineNode(eq("novel-001"), any(OutlineNodeVO.class)))
                .thenReturn(volume);
        when(planningService.updateOutlineNode(eq("novel-001"), any(OutlineNodeVO.class)))
                .thenReturn(volume);
        when(planningService.reorderOutlineNode("novel-001", "volume", 1))
                .thenReturn(volume);

        mockMvc.perform(get("/api/v1/novels/projects/novel-001/outlines/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].nodeCode").value("volume"));
        mockMvc.perform(post("/api/v1/novels/projects/novel-001/outlines/nodes")
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentNodeCode":"book",
                                  "title":"第一卷",
                                  "summary":"卷",
                                  "startChapter":1,
                                  "endChapter":100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCode").value("volume"));
        mockMvc.perform(post("/api/v1/novels/projects/novel-001/outlines/nodes/volume")
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentNodeCode":"book",
                                  "nodeKind":"VOLUME",
                                  "sequenceNo":1,
                                  "title":"第一卷（修订）",
                                  "summary":"修订后的卷",
                                  "startChapter":1,
                                  "endChapter":100,
                                  "status":"READY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("第一卷"));
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/nodes/volume/delete"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/nodes/volume/reorder"
                ).contentType("application/json").content("""
                        {"targetSequence":1}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCode").value("volume"));

        verify(planningService).listOutlineTree("novel-001");
        verify(planningService).createOutlineNode(
                eq("novel-001"),
                argThat(value -> value.nodeCode() == null
                        && value.parentNodeCode().equals("book")
                        && value.nodeKind() == null
                        && value.sequenceNo() == null
                        && value.startChapter() == 1
                        && value.endChapter() == 100
                        && value.status() == null)
        );
        verify(planningService).updateOutlineNode(
                eq("novel-001"),
                argThat(value -> value.nodeCode().equals("volume")
                        && value.title().equals("第一卷（修订）"))
        );
        verify(planningService).deleteOutlineNode("novel-001", "volume");
        verify(planningService).reorderOutlineNode("novel-001", "volume", 1);
        System.out.println("ManualOutlineCrudHttpTest verified GET/POST outline routes, including POST delete and reorder actions");
    }

    private OutlineNodeVO node(
            String code,
            String parentCode,
            OutlineNodeKindEnum kind,
            int sequence,
            String title,
            String summary
    ) {
        return new OutlineNodeVO(
                code, parentCode, kind, sequence, title, summary,
                1, 100, "PLANNED"
        );
    }
}
