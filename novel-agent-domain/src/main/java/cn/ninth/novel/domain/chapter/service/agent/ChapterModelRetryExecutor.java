package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.types.exception.AppException;
import org.springframework.stereotype.Component;

import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 对章节模型边界的瞬态失败执行有界重试。 */
@Component
public class ChapterModelRetryExecutor {

    static final int MAX_RETRIES = 3;
    private static final int MAX_STRUCTURED_RESPONSE_RETRIES = 1;
    private static final long FIRST_RETRY_BACKOFF_MILLIS = 300L;
    private static final long SECOND_RETRY_BACKOFF_MILLIS = 1_000L;
    private static final long THIRD_RETRY_BACKOFF_MILLIS = 2_000L;

    public <T> RetryResult<T> execute(Supplier<T> operation) {
        return execute(0, operation);
    }

    public <T> RetryResult<T> execute(int usedRetryCount, Supplier<T> operation) {
        return execute(usedRetryCount, operation, exception -> false);
    }

    /**
     * 在默认瞬态错误策略之外，为特定节点增加可重试的业务校验错误。
     *
     * <p>默认调用保持原有策略；REVIEW 节点使用该入口把模型报告校验放入
     * 同一个重试闭包，避免一个非法 evidence 直接终止整条工作流。</p>
     */
    public <T> RetryResult<T> execute(
            int usedRetryCount,
            Supplier<T> operation,
            Predicate<AppException> additionalRetryable
    ) {
        return execute(
                usedRetryCount,
                attempt -> operation.get(),
                additionalRetryable
        );
    }

    /**
     * 执行模型边界调用，并将本次 0-based 尝试序号交给调用方生成 Prompt Trace。
     * 首次调用为 0，第一次重试为 1。
     */
    public <T> RetryResult<T> execute(
            int usedRetryCount,
            IntFunction<T> operation,
            Predicate<AppException> additionalRetryable
    ) {
        int retriesRemaining = Math.max(0, MAX_RETRIES - usedRetryCount);
        for (int attempt = 0; ; attempt++) {
            try {
                return new RetryResult<>(operation.apply(attempt), attempt);
            } catch (AppException exception) {
                int retryLimit = retryLimit(
                        exception,
                        retriesRemaining,
                        additionalRetryable
                );
                if (attempt >= retryLimit) {
                    throw exception;
                }
                waitBeforeRetry(attempt + 1);
            }
        }
    }

    private int retryLimit(
            AppException exception,
            int retriesRemaining,
            Predicate<AppException> additionalRetryable
    ) {
        if (exception instanceof ChapterModelResponseException response
                && response.parseRetryExhausted()) {
            return 0;
        }
        if (additionalRetryable.test(exception)) {
            return retriesRemaining;
        }
        if (ResponseCode.E0001.getCode().equals(exception.getCode())) {
            return retriesRemaining;
        }
        if (ResponseCode.E0007.getCode().equals(exception.getCode())) {
            return Math.min(MAX_STRUCTURED_RESPONSE_RETRIES, retriesRemaining);
        }
        return 0;
    }

    private void waitBeforeRetry(int failedAttempt) {
        long backoffMillis = switch (failedAttempt) {
            case 1 -> FIRST_RETRY_BACKOFF_MILLIS;
            case 2 -> SECOND_RETRY_BACKOFF_MILLIS;
            default -> THIRD_RETRY_BACKOFF_MILLIS;
        };
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("章节模型重试退避被中断", exception);
        }
    }

    public record RetryResult<T>(T value, int retryCount) {
    }
}
