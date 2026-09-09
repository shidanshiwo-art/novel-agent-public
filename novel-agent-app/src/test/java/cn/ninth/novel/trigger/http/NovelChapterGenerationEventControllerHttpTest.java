package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionEvent;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionRegistry;
import cn.ninth.novel.types.enums.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelChapterGenerationEventController.class)
@Import(NovelChapterGenerationEventControllerHttpTest.ControllerTestConfiguration.class)
@ActiveProfiles("test")
class NovelChapterGenerationEventControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChapterGenerationSessionRegistry sessionRegistry;

    @BeforeEach
    void setUpSession() {
        sessionRegistry.register("workflow-events");
        sessionRegistry.publish(
                "workflow-events",
                ChapterGenerationEventType.GENERATION_STARTED
        );
        sessionRegistry.publish(
                "workflow-events",
                ChapterGenerationEventType.GENERATION_COMPLETED
        );
    }

    @Test
    void shouldStreamNamedEventsOverHttp() throws Exception {
        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/novels/generation-sessions/workflow-events/events"
                ).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event:GENERATION_STARTED"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event:GENERATION_COMPLETED"
                )));
        System.out.println("HTTP SSE 已输出 GENERATION_STARTED 和 GENERATION_COMPLETED 命名事件");
    }

    @Test
    void shouldCloseHttpStreamAfterGenerationCancellation() throws Exception {
        sessionRegistry.register("workflow-cancelled");
        sessionRegistry.publish(
                "workflow-cancelled",
                ChapterGenerationEventType.GENERATION_STARTED
        );
        sessionRegistry.publish(
                "workflow-cancelled",
                ChapterGenerationEventType.DRAFT_STARTED
        );
        assertThat(sessionRegistry.requestStop("workflow-cancelled").accepted())
                .isTrue();

        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/novels/generation-sessions/workflow-cancelled/events"
                ).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event:GENERATION_CANCELLED"
                )));
        System.out.println("HTTP SSE 在 GENERATION_CANCELLED 后正确结束");
    }

    @Test
    void shouldStreamDraftChunkContentAsJsonPayload() throws Exception {
        sessionRegistry.register("workflow-chunk-content");
        sessionRegistry.publish(
                "workflow-chunk-content",
                ChapterGenerationEventType.GENERATION_STARTED
        );
        sessionRegistry.publish(
                "workflow-chunk-content",
                ChapterGenerationEventType.DRAFT_CHUNK,
                "draft-chunk-content"
        );
        sessionRegistry.publish(
                "workflow-chunk-content",
                ChapterGenerationEventType.GENERATION_COMPLETED
        );

        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/novels/generation-sessions/workflow-chunk-content/events"
                ).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event:DRAFT_CHUNK"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"content\":\"draft-chunk-content\""
                )));
        System.out.println("HTTP SSE 的 DRAFT_CHUNK 已输出包含 content 的 JSON 事件数据");
    }

    @Test
    void shouldStreamReviewIssuesOnReviewCompleted() throws Exception {
        sessionRegistry.register("workflow-review-issues");
        sessionRegistry.publish(
                "workflow-review-issues",
                ChapterGenerationEventType.GENERATION_STARTED
        );
        sessionRegistry.publish(
                "workflow-review-issues",
                ChapterGenerationEventType.REVIEW_COMPLETED,
                null,
                java.util.List.of("结尾冲突偏弱")
        );
        sessionRegistry.publish(
                "workflow-review-issues",
                ChapterGenerationEventType.GENERATION_COMPLETED
        );

        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/novels/generation-sessions/workflow-review-issues/events"
                ).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event:REVIEW_COMPLETED")
                .contains("\"reviewIssues\":[\"结尾冲突偏弱\"]");
        System.out.println("HTTP SSE Review 结果检查：REVIEW_COMPLETED 已携带当前 reviewIssues");
    }

    @Test
    void shouldStreamFailureReasonAsEventContent() throws Exception {
        sessionRegistry.register("workflow-failed-content");
        sessionRegistry.publish(
                "workflow-failed-content",
                ChapterGenerationEventType.GENERATION_STARTED
        );
        sessionRegistry.publish(
                "workflow-failed-content",
                ChapterGenerationEventType.GENERATION_FAILED,
                ChapterGenerationSessionEvent.DRAFT_FAILURE_MESSAGE
        );

        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/novels/generation-sessions/workflow-failed-content/events"
                ).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event:GENERATION_FAILED")
                .contains(ChapterGenerationSessionEvent.DRAFT_FAILURE_MESSAGE)
                .doesNotContain("chapter context load failed");
        System.out.println("HTTP SSE 失败事件已输出阶段用户文案，未输出底层异常");
    }

    @Test
    void shouldHideTechnicalFailureDetailsFromSseContent() throws Exception {
        sessionRegistry.register("workflow-technical-failure");
        sessionRegistry.publish(
                "workflow-technical-failure",
                ChapterGenerationEventType.GENERATION_STARTED
        );
        sessionRegistry.publish(
                "workflow-technical-failure",
                ChapterGenerationEventType.GENERATION_FAILED,
                "MismatchedInputException: JSON parse error, model timeout"
        );

        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/novels/generation-sessions/workflow-technical-failure/events"
                ).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event:GENERATION_FAILED")
                .contains(ResponseCode.UN_ERROR.getMessage())
                .doesNotContain("MismatchedInputException", "JSON", "timeout");
        System.out.println("HTTP SSE 已将模型技术异常收口为系统暂时异常提示，原始异常未进入 content");
    }

    @TestConfiguration
    static class ControllerTestConfiguration {

        @Bean
        ChapterGenerationSessionRegistry sessionRegistry() {
            return new ChapterGenerationSessionRegistry();
        }
    }
}
