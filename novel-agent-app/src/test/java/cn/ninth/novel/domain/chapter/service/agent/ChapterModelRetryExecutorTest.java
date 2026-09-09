package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterModelRetryExecutorTest {

    private final ChapterModelRetryExecutor executor = new ChapterModelRetryExecutor();

    @Test
    void shouldRetryTransientModelFailureUntilInvocationSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        long startedAt = System.nanoTime();

        ChapterModelRetryExecutor.RetryResult<String> result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw AppException.internal(ResponseCode.E0001.getCode(), "temporary");
            }
            return "draft";
        });
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(result.value()).isEqualTo("draft");
        assertThat(result.retryCount()).isEqualTo(2);
        assertThat(calls).hasValue(3);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(1_200L);
        System.out.printf(
                "瞬态模型失败退避检查：calls=%d, retryCount=%d, elapsedMillis=%d%n",
                calls.get(), result.retryCount(), elapsedMillis
        );
    }

    @Test
    void shouldStopAfterThreeRetriesForTransientModelFailure() {
        AtomicInteger calls = new AtomicInteger();
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw AppException.internal(ResponseCode.E0001.getCode(), "temporary");
        })).isInstanceOf(AppException.class);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(calls).hasValue(4);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(3_000L);
        System.out.printf(
                "三次重试上限退避检查：calls=%d, elapsedMillis=%d%n",
                calls.get(), elapsedMillis
        );
    }

    @Test
    void shouldUseOnlyRemainingWorkflowRetryBudget() {
        AtomicInteger calls = new AtomicInteger();

        ChapterModelRetryExecutor.RetryResult<String> result = executor.execute(2, () -> {
            if (calls.incrementAndGet() == 1) {
                throw AppException.internal(ResponseCode.E0001.getCode(), "temporary");
            }
            return "draft";
        });

        assertThat(result.value()).isEqualTo("draft");
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldNotRetryWhenWorkflowRetryBudgetIsExhausted() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(3, () -> {
            calls.incrementAndGet();
            throw AppException.internal(ResponseCode.E0001.getCode(), "temporary");
        })).isInstanceOf(AppException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldExposeZeroBasedAttemptNumberToEachModelCall() {
        List<Integer> attempts = new ArrayList<>();

        ChapterModelRetryExecutor.RetryResult<String> result = executor.execute(
                2,
                attempt -> {
                    attempts.add(attempt);
                if (attempt == 0) {
                        throw AppException.internal(ResponseCode.E0001.getCode(), "temporary");
                    }
                    return "done";
                },
                exception -> false
        );

        assertThat(result.value()).isEqualTo("done");
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(attempts).containsExactly(0, 1);
        System.out.printf(
                "模型调用 attempt 序号检查：attempts=%s, retryCount=%d%n",
                attempts,
                result.retryCount()
        );
    }

    @Test
    void shouldNotRetryNonTransientFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw AppException.internal(ResponseCode.ILLEGAL_PARAMETER.getCode(), "invalid state");
        })).isInstanceOf(AppException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldRetryStructuredResponseFailureAtMostOnce() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw AppException.internal(ResponseCode.E0007.getCode(), "structured response invalid");
        }))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getCode())
                .isEqualTo(ResponseCode.E0007.getCode());

        assertThat(calls).hasValue(2);
        System.out.printf("结构化响应错误重试上限检查：calls=%d, retryLimit=1%n", calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"E0004", "E0006"})
    void shouldNotRetryReviewOrCompressionBusinessValidationFailure(String code) {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw AppException.internal(code, "business validation failed");
        }))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getCode())
                .isEqualTo(code);

        assertThat(calls).hasValue(1);
        System.out.printf("业务校验错误不进入模型重试：code=%s, calls=%d%n", code, calls.get());
    }
}
