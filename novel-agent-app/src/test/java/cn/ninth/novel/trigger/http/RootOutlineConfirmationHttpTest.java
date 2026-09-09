package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.planning.service.IPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class RootOutlineConfirmationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldConfirmRootOutlineWithSummaryOnly() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/root/confirm"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "draftId":"draft-root",
                                  "summary":"修订后的概要"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        verify(planningService).confirmRootOutline(
                eq("novel-001"),
                eq("draft-root"),
                eq("修订后的概要")
        );
        System.out.println("root outline confirmation HTTP route verified: POST /outlines/root/confirm");
    }
}
