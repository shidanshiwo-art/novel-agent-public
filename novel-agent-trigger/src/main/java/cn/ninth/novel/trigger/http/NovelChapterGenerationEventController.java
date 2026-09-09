package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionRegistry;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_ABORTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_CANCELLED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_FAILED;

/**
 * 章节生成 Session 的 SSE 事件通道。
 */
@RestController
@RequestMapping("/api/v1/novels/generation-sessions")
@Slf4j
public class NovelChapterGenerationEventController {

    private final ChapterGenerationSessionRegistry sessionRegistry;

    public NovelChapterGenerationEventController(
            ChapterGenerationSessionRegistry sessionRegistry
    ) {
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping(value = "/{workflowId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String workflowId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean connectedLogged = new AtomicBoolean();
        AtomicBoolean terminalLogged = new AtomicBoolean();
        AtomicBoolean listenerRegistered = new AtomicBoolean();
        Runnable logConnected = () -> logOnce(
                connectedLogged,
                () -> log.info("[SSE] connected workflowId={}", workflowId)
        );
        Consumer<ChapterGenerationSessionEvent> listener = event ->
                sendEvent(workflowId, emitter, event, logConnected);
        Runnable unsubscribe = () -> {
            if (listenerRegistered.compareAndSet(true, false)) {
                sessionRegistry.unsubscribe(workflowId, listener);
            }
        };
        emitter.onCompletion(() -> {
            logOnce(
                    terminalLogged,
                    () -> log.info("[SSE] completed workflowId={}", workflowId)
            );
            unsubscribe.run();
        });
        emitter.onTimeout(() -> {
            logOnce(
                    terminalLogged,
                    () -> log.info("[SSE] disconnected workflowId={} reason=timeout", workflowId)
            );
            unsubscribe.run();
        });
        emitter.onError(error -> {
            logOnce(terminalLogged, () -> {
                if (ClientDisconnectDetector.isClientDisconnected(error)) {
                    log.warn(
                            "CLIENT_DISCONNECTED workflowId={} exceptionType={} rootCauseType={}",
                            workflowId,
                            error == null ? null : error.getClass().getSimpleName(),
                            rootCauseType(error)
                    );
                    return;
                }
                log.warn(
                        "[SSE] error workflowId={} errorType={}",
                        workflowId,
                        error == null ? null : error.getClass().getSimpleName()
                );
            });
            unsubscribe.run();
        });

        // subscribe 会同步回放历史事件；回放期间断开也必须能取消刚登记的 listener。
        listenerRegistered.set(true);
        ChapterGenerationSessionRegistry.SubscribeResult result =
                sessionRegistry.subscribe(workflowId, listener);
        if (!result.found()) {
            unsubscribe.run();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (result.terminal()) {
            unsubscribe.run();
        }
        logConnected.run();
        if (result.terminal()) {
            emitter.complete();
        }
        return emitter;
    }

    private void sendEvent(
            String workflowId,
            SseEmitter emitter,
            ChapterGenerationSessionEvent event,
            Runnable logConnected
    ) {
        String eventType = event.type().name();
        logConnected.run();
        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(event));
            if (GENERATION_COMPLETED.name().equals(eventType)
                    || GENERATION_ABORTED.name().equals(eventType)
                    || GENERATION_CANCELLED.name().equals(eventType)
                    || GENERATION_FAILED.name().equals(eventType)) {
                emitter.complete();
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void logOnce(AtomicBoolean logged, Runnable action) {
        if (logged.compareAndSet(false, true)) {
            action.run();
        }
    }

    private String rootCauseType(Throwable exception) {
        if (exception == null) {
            return null;
        }
        Throwable root = exception;
        for (int depth = 0; depth < 32 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
