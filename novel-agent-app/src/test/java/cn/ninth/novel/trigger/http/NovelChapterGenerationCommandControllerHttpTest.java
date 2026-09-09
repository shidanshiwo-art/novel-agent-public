package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.service.IChapterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelChapterGenerationCommandController.class)
@ActiveProfiles("test")
class NovelChapterGenerationCommandControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IChapterService chapterService;

    @Test
    void shouldStopGenerationSessionThroughHttp() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/generation-sessions/workflow-stop/commands"
                )
                        .contentType("application/json")
                        .content("{\"command\":\"STOP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(chapterService).stopGenerationSession("workflow-stop");
        System.out.println(
                "HTTP STOP command 路径和请求体契约通过：POST /generation-sessions/{workflowId}/commands"
        );
    }

    @Test
    void shouldAcceptGenerationSessionThroughHttp() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/generation-sessions/workflow-accept/commands"
                )
                        .contentType("application/json")
                        .content("{\"command\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(chapterService).acceptGenerationSession("workflow-accept");
        System.out.println("HTTP ACCEPT command 路径和请求体契约通过");
    }
}
