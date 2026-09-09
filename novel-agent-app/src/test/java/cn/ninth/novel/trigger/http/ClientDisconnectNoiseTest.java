package cn.ninth.novel.trigger.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClientDisconnectNoiseTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldClassifyClientDisconnectAtWarnWithoutStacktrace() {
        handler.handleException(new IOException("ServletOutputStream failed to write"));
        ILoggingEvent genericEvent = onlyEvent();

        assertThat(genericEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(genericEvent.getFormattedMessage())
                .contains("CLIENT_DISCONNECTED", "exceptionType=IOException");
        assertThat(genericEvent.getThrowableProxy()).isNull();

        appender.list.clear();
        handler.handleAppException(AppException.internal(
                ResponseCode.E0001.getCode(),
                "response write failed",
                new ClientAbortException()
        ));
        ILoggingEvent appEvent = onlyEvent();

        assertThat(appEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(appEvent.getFormattedMessage())
                .contains("CLIENT_DISCONNECTED", "rootCauseType=ClientAbortException");
        assertThat(appEvent.getThrowableProxy()).isNull();
        System.out.println("客户端主动断开已归类为 CLIENT_DISCONNECTED：WARN，无完整堆栈");
    }

    @Test
    void shouldRecognizeAsyncRequestNotUsableExceptionInCauseChain() {
        Throwable wrapped = new IllegalStateException(
                "response write failed",
                new AsyncRequestNotUsableException()
        );

        assertThat(ClientDisconnectDetector.isClientDisconnected(wrapped)).isTrue();
        System.out.println("AsyncRequestNotUsableException cause chain 已识别为 CLIENT_DISCONNECTED");
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    private static final class ClientAbortException extends IOException {
        private ClientAbortException() {
            super("client closed connection");
        }
    }

    private static final class AsyncRequestNotUsableException extends RuntimeException {
        private AsyncRequestNotUsableException() {
            super("request is no longer usable");
        }
    }
}
