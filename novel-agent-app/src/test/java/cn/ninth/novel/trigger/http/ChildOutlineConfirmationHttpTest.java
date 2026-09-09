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
class ChildOutlineConfirmationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldConfirmOneNextOutlineWithEditableContentOnly() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/VOL_001/children/confirm"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "draftId":"draft-next",
                                  "title":"用户标题",
                                  "summary":"用户概要"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        verify(planningService).confirmNextOutline(
                eq("novel-001"), eq("VOL_001"), eq("draft-next"),
                eq("用户标题"), eq("用户概要")
        );
        System.out.println("HTTP 下一步大纲确认契约已验证：只提交 draftId、标题和概要");
    }

    @Test
    void shouldConfirmCurrentVolumeOutlineThroughDedicatedEndpoint() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/volumes/VOL_001/confirm"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "draftId":"draft-volume",
                                  "title":"用户卷名",
                                  "summary":"用户卷纲"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        verify(planningService).confirmVolumeOutline(
                eq("novel-001"), eq("VOL_001"), eq("draft-volume"),
                eq("用户卷名"), eq("用户卷纲")
        );
        System.out.println("HTTP 当前卷卷纲确认契约已验证：确认后由服务推进 PLANNED");
    }
}
