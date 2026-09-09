package cn.ninth.novel.trigger.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.types.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerLogLevelTest {

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
    void shouldLogExpectedBusinessFailureAtWarnWithoutStacktrace() {
        Response<Void> response = handler.handleAppException(
                AppException.user(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "当前状态不允许操作"
                )
        );

        ILoggingEvent event = onlyEvent();
        assertThat(response.info()).isEqualTo("当前状态不允许操作");
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("code=0002", "message=当前状态不允许操作");
        assertThat(event.getThrowableProxy()).isNull();
        System.out.println("用户可预期业务异常使用 WARN，且不携带 stacktrace");
    }

    @Test
    void shouldLogInternalAndTechnicalFailureAtErrorWithStacktrace() {
        IllegalStateException cause = new IllegalStateException("Jackson detail");

        handler.handleAppException(AppException.internal(
                ResponseCode.E0004.getCode(),
                "MismatchedInputException: JSON parse error",
                cause
        ));

        ILoggingEvent internalEvent = onlyEvent();
        assertThat(internalEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(internalEvent.getFormattedMessage())
                .contains("code=E0004", "MismatchedInputException: JSON parse error");
        assertThat(internalEvent.getThrowableProxy()).isNotNull();

        appender.list.clear();
        handler.handleAppException(new AppException(ResponseCode.E0003.getCode()));

        ILoggingEvent technicalEvent = onlyEvent();
        assertThat(technicalEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(technicalEvent.getFormattedMessage()).contains("code=E0003");
        assertThat(technicalEvent.getThrowableProxy()).isNotNull();
        System.out.println("internalDetail 和 E0003 技术失败使用 ERROR，并保留 stacktrace");
    }

    @Test
    void shouldKeepUnknownExceptionAtErrorWithSafeResponse() {
        Response<Void> response = handler.handleException(
                new RuntimeException("SQLIntegrityConstraintViolationException")
        );

        ILoggingEvent event = onlyEvent();
        assertThat(response.code()).isEqualTo(ResponseCode.UN_ERROR.getCode());
        assertThat(response.info()).isEqualTo(ResponseCode.UN_ERROR.getMessage());
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        System.out.println("未知 RuntimeException 使用 ERROR，响应保持 0001 安全提示");
    }

    @Test
    void shouldHideResponseStatusReasonAndLogOnlyRequestMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/novels/generation-sessions/missing/events");

        ResponseEntity<Response<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "secret reason"),
                request
        );

        ILoggingEvent event = onlyEvent();
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().info()).isEqualTo(ResponseCode.UN_ERROR.getMessage());
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("status=404", request.getRequestURI())
                .doesNotContain("secret reason");
        assertThat(event.getThrowableProxy()).isNull();
        System.out.println("ResponseStatusException 仅记录 status/path，reason 未进入响应或日志");
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }
}
