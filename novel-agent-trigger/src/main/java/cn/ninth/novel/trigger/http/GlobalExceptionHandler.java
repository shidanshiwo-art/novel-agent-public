package cn.ninth.novel.trigger.http;

import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.types.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/** 统一将后端异常转换为安全的 API 用户提示。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> EXPECTED_BUSINESS_ERROR_CODES = Set.of(
            ResponseCode.ILLEGAL_PARAMETER.getCode(),
            ResponseCode.HUMAN_REVISE_LIMIT_EXCEEDED.getCode()
    );

    private static final Set<String> TECHNICAL_ERROR_CODES = Set.of(
            ResponseCode.UN_ERROR.getCode(),
            ResponseCode.E0001.getCode(),
            ResponseCode.E0002.getCode(),
            ResponseCode.E0003.getCode(),
            ResponseCode.E0004.getCode(),
            ResponseCode.E0005.getCode(),
            ResponseCode.E0006.getCode(),
            ResponseCode.E0007.getCode()
    );

    @ExceptionHandler(AppException.class)
    public Response<Void> handleAppException(AppException exception) {
        if (ClientDisconnectDetector.isClientDisconnected(exception)) {
            log.warn(
                    "CLIENT_DISCONNECTED exceptionType={} rootCauseType={}",
                    exception.getClass().getSimpleName(),
                    rootCauseType(exception)
            );
            return Response.failure(exception);
        }
        if (shouldLogError(exception)) {
            log.error(
                    "API 技术异常，code={}，internalDetail={}",
                    exception.getCode(),
                    exception.getInternalDetail(),
                    exception
            );
        } else {
            log.warn(
                    "API 业务校验失败，code={}，message={}",
                    exception.getCode(),
                    ResponseCode.messageFor(
                            exception.getCode(),
                            exception.getUserMessage()
                    )
            );
        }
        return Response.failure(exception);
    }

    private boolean shouldLogError(AppException exception) {
        return exception.getInternalDetail() != null
                || TECHNICAL_ERROR_CODES.contains(exception.getCode())
                || !EXPECTED_BUSINESS_ERROR_CODES.contains(exception.getCode());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Response<Void>> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "API 请求状态异常，status={}，path={}",
                exception.getStatusCode().value(),
                request == null ? null : request.getRequestURI()
        );
        return ResponseEntity.status(exception.getStatusCode())
                .body(Response.failure(ResponseCode.UN_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception exception) {
        if (ClientDisconnectDetector.isClientDisconnected(exception)) {
            log.warn(
                    "CLIENT_DISCONNECTED exceptionType={} rootCauseType={}",
                    exception.getClass().getSimpleName(),
                    rootCauseType(exception)
            );
            return Response.failure(ResponseCode.UN_ERROR);
        }
        log.error("API 未处理异常", exception);
        return Response.failure(ResponseCode.UN_ERROR);
    }

    private String rootCauseType(Throwable exception) {
        Throwable root = exception;
        for (int depth = 0; depth < 32 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
