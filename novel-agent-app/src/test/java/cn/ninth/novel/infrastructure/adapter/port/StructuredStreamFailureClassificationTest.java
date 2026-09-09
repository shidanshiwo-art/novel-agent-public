package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import cn.ninth.novel.types.exception.AppException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class StructuredStreamFailureClassificationTest {

    @Test
    void classifiesStructuredStreamLifecycleFailuresInBothPorts() {
        List<FailureCase> cases = List.of(
                new FailureCase(
                        "connect-timeout",
                        "CONNECT_TIMEOUT",
                        Flux.error(new SocketTimeoutException("connect timed out"))
                ),
                new FailureCase(
                        "stream-start-timeout",
                        "STREAM_START_TIMEOUT",
                        Flux.error(new SocketTimeoutException("read timed out"))
                ),
                new FailureCase(
                        "stream-read-timeout",
                        "STREAM_READ_TIMEOUT",
                        Flux.concat(
                                Flux.just(response("{\"summary\":\"partial")),
                                Flux.error(new SocketTimeoutException("read timed out"))
                        )
                ),
                new FailureCase(
                        "stream-failed",
                        "STREAM_FAILED",
                        Flux.error(new IllegalStateException("upstream closed"))
                )
        );

        for (boolean planning : List.of(true, false)) {
            for (FailureCase failureCase : cases) {
                Logger logger = (Logger) LoggerFactory.getLogger(
                        planning ? PlanningModelPort.class : ChapterModelPort.class
                );
                ListAppender<ILoggingEvent> appender = new ListAppender<>();
                appender.start();
                logger.addAppender(appender);
                try {
                    ChatClient client = ChatClient.builder(new FailingStreamChatModel(failureCase.responses()))
                            .build();
                    RuntimeException failure = catchThrowableOfType(
                            () -> {
                                if (planning) {
                                    new PlanningModelPort(client).call(
                                            "system", "user", RootOutlineDraftVO.class, "FAILURE_TEST", 1
                                    );
                                } else {
                                    new ChapterModelPort(client).call(
                                            "system", "user", RootOutlineDraftVO.class, "FAILURE_TEST", 1
                                    );
                                }
                            },
                            RuntimeException.class
                    );
                    assertThat(failure).isNotNull();

                    String failureLog = appender.list.stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .filter(message -> message.contains("failureType=" + failureCase.expectedType()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(failureLog)
                            .contains("stage=FAILURE_TEST", "exceptionType=", "rootCauseType=")
                            .doesNotContain("upstream closed", "connect timed out", "read timed out");
                    assertThat(failureLog).contains(
                            "rootCauseType=" + (failureCase.expectedType().contains("TIMEOUT")
                                    ? "SocketTimeoutException" : "IllegalStateException")
                    );
                    System.out.printf(
                            "结构化流生命周期分类通过：planning=%s, case=%s, %s%n",
                            planning,
                            failureCase.name(),
                            failureLog
                    );
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }
        }
    }

    @Test
    void timeoutCancelsStructuredStreamAndReturnsUnifiedTimeoutFailure() {
        List<TimeoutCase> cases = List.of(
                new TimeoutCase(
                        "stream-start-timeout",
                        "STREAM_START_TIMEOUT",
                        cancellation -> Flux.<ChatResponse>never()
                                .doOnCancel(cancellation::incrementAndGet)
                ),
                new TimeoutCase(
                        "stream-read-timeout",
                        "STREAM_READ_TIMEOUT",
                        cancellation -> Flux.concat(
                                        Flux.just(response("{\"summary\":\"partial")),
                                        Flux.<ChatResponse>never()
                                )
                                .doOnCancel(cancellation::incrementAndGet)
                )
        );

        for (boolean planning : List.of(true, false)) {
            for (TimeoutCase timeoutCase : cases) {
                AtomicInteger cancellationCount = new AtomicInteger();
                ChatClient client = ChatClient.builder(
                        new FailingStreamChatModel(timeoutCase.responses().apply(cancellationCount))
                ).build();
                ModelTimeoutProperties modelTimeoutProperties = new ModelTimeoutProperties();
                modelTimeoutProperties.getStructuredTimeouts().put(
                        "TIMEOUT_TEST",
                        Duration.ofMillis(20)
                );
                Logger logger = (Logger) LoggerFactory.getLogger(
                        planning ? PlanningModelPort.class : ChapterModelPort.class
                );
                ListAppender<ILoggingEvent> appender = new ListAppender<>();
                appender.start();
                logger.addAppender(appender);
                try {
                    AppException failure = catchThrowableOfType(
                            () -> {
                                if (planning) {
                                    new PlanningModelPort(
                                            client,
                                            new PlanningModelProperties(),
                                            modelTimeoutProperties
                                    ).call(
                                            "system", "user", RootOutlineDraftVO.class, "TIMEOUT_TEST", 1
                                    );
                                } else {
                                    new ChapterModelPort(client, modelTimeoutProperties).call(
                                            "system", "user", RootOutlineDraftVO.class, "TIMEOUT_TEST", 1
                                    );
                                }
                            },
                            AppException.class
                    );
                    assertThat(failure).isNotNull();
                    assertThat(failure.getUserMessage()).isEqualTo("模型响应超时，请重试");
                    assertThat(cancellationCount).hasValue(1);
                    assertThat(appender.list)
                            .anySatisfy(event -> assertThat(event.getFormattedMessage())
                                    .contains(
                                            "stage=TIMEOUT_TEST",
                                            "failureType=" + timeoutCase.expectedType(),
                                            "exceptionType=",
                                            "rootCauseType="
                                    ));
                    System.out.printf(
                            "结构化流超时取消通过：planning=%s, case=%s, cancellations=%d%n",
                            planning,
                            timeoutCase.name(),
                            cancellationCount.get()
                    );
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }
        }
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private record FailureCase(String name, String expectedType, Flux<ChatResponse> responses) {
    }

    private record TimeoutCase(
            String name,
            String expectedType,
            java.util.function.Function<AtomicInteger, Flux<ChatResponse>> responses
    ) {
    }

    private static final class FailingStreamChatModel implements ChatModel {
        private final Flux<ChatResponse> responses;

        private FailingStreamChatModel(Flux<ChatResponse> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("结构化响应必须使用 stream()，不能回退到 call()");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return responses;
        }
    }
}
