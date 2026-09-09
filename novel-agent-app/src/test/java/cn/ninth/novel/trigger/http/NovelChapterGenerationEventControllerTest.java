package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NovelChapterGenerationEventControllerTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeWorkflowEventStreamEndpoint() throws Exception {
        Method method = NovelChapterGenerationEventController.class
                .getDeclaredMethod("events", String.class);

        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/{workflowId}/events");
        assertThat(method.getAnnotation(GetMapping.class).produces())
                .containsExactly("text/event-stream");
        assertThat(ChapterGenerationEventType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "GENERATION_STARTED",
                        "DRAFT_STARTED",
                        "DRAFT_CHUNK",
                        "DRAFT_COMPLETED",
                        "REVIEW_STARTED",
                        "REVIEW_COMPLETED",
                        "REVISION_STARTED",
                        "REVISION_COMPLETED",
                        "REVIEW_FAILED",
                        "HUMAN_REVIEW_REQUIRED",
                        "COMPRESSION_STARTED",
                        "COMPRESSION_COMPLETED",
                        "PERSIST_STARTED",
                        "PERSIST_COMPLETED",
                        "GENERATION_COMPLETED",
                        "GENERATION_ABORTED",
                        "GENERATION_FAILED",
                        "GENERATION_CANCELLED"
                );
        assertThat(NovelChapterGenerationEventController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value())
                .containsExactly("/api/v1/novels/generation-sessions");
        System.out.println("SSE 事件通道路径和 18 种业务事件类型契约通过");
    }

    @Test
    void shouldReturnNotFoundForUnknownWorkflow() {
        NovelChapterGenerationEventController controller =
                new NovelChapterGenerationEventController(
                        new ChapterGenerationSessionRegistry()
                );

        org.springframework.web.server.ResponseStatusException exception =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> controller.events("workflow-missing"),
                        org.springframework.web.server.ResponseStatusException.class
                );

        assertThat(exception.getStatusCode().value()).isEqualTo(404);
        System.out.println("未知 workflowId 的 SSE 订阅返回 404");
    }

    @Test
    void shouldLogSseLifecycleOnlyAndNeverLogDraftChunkContent() throws IOException {
        String source = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/"
                        + "NovelChapterGenerationEventController.java"
        ));

        assertThat(source)
                .contains(
                        "log.info(\"[SSE] connected workflowId={}\", workflowId)",
                        "log.info(\"[SSE] completed workflowId={}\", workflowId)",
                        "log.info(\"[SSE] disconnected workflowId={} reason=timeout\", workflowId)",
                        "log.warn(",
                        "CLIENT_DISCONNECTED",
                        "ClientDisconnectDetector.isClientDisconnected(error)",
                        "[SSE] error workflowId={} errorType={}",
                        "logOnce("
                )
                .doesNotContain(
                        "log.debug(\"SSE",
                        "log.info(\"[SSE] event",
                        "event.content()",
                        "eventType={} chunk",
                        "DRAFT_CHUNK 的正文",
                        "event.content().length()",
                        "chunkLength"
                );
        System.out.println("后端 SSE 日志契约通过：只记录 connected/completed/disconnected/error 生命周期，未记录事件正文");
    }
}
