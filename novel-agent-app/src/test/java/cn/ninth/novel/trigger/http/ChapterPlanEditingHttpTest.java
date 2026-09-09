package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.service.IPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
class ChapterPlanEditingHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldListChapterPlansWithGet() throws Exception {
        when(planningService.listChapterPlans("novel-001"))
                .thenReturn(List.of(new ChapterOutlineVO(
                        3, "ARC_001", "第三章", "第三章摘要", "READY"
                )));

        mockMvc.perform(get("/api/v1/novels/projects/novel-001/chapter-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].chapterNumber").value(3))
                .andExpect(jsonPath("$.data[0].outlineNodeCode").value("ARC_001"))
                .andExpect(jsonPath("$.data[0].title").value("第三章"))
                .andExpect(jsonPath("$.data[0].status").value("READY"));

        verify(planningService).listChapterPlans("novel-001");
        System.out.println("chapter plan list HTTP route verified: GET /chapter-plans");
    }

    @Test
    void shouldUpdateChapterPlanWithPostAndUsePathChapterNumber() throws Exception {
        when(planningService.updateChapterPlan(
                eq("novel-001"),
                eq(3),
                argThat((ChapterOutlineVO plan) -> plan != null
                        && plan.chapterNumber() == 3
                        && "人工修订标题".equals(plan.title())
                        && "人工修订摘要".equals(plan.summary())
                        && plan.status() == null)
        )).thenReturn(new ChapterOutlineVO(
                3, "ARC_001", "人工修订标题", "人工修订摘要", "READY"
        ));

        mockMvc.perform(post("/api/v1/novels/projects/novel-001/chapter-plans/3")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"人工修订标题",
                                  "summary":"人工修订摘要"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.chapterNumber").value(3))
                .andExpect(jsonPath("$.data.outlineNodeCode").value("ARC_001"))
                .andExpect(jsonPath("$.data.title").value("人工修订标题"))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(planningService).updateChapterPlan(
                eq("novel-001"),
                eq(3),
                argThat((ChapterOutlineVO plan) -> plan != null && plan.chapterNumber() == 3)
        );
        System.out.println("chapter plan update HTTP route verified: POST /chapter-plans/{chapterNumber}");
    }
}
