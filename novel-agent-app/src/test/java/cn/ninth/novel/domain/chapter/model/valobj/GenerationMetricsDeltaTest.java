package cn.ninth.novel.domain.chapter.model.valobj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证模型调用指标的阶段计数和可空 Token 聚合规则。 */
class GenerationMetricsDeltaTest {

    @Test
    void shouldAggregateAllModelStagesWithoutEstimatingMissingUsage() {
        GenerationMetricsDelta first = GenerationMetricsDelta.forCall(
                "DRAFT", ChapterTokenUsage.of(100L, 40L, 140L));
        GenerationMetricsDelta review = GenerationMetricsDelta.forCall(
                "REVIEW", ChapterTokenUsage.of(null, null, null));
        GenerationMetricsDelta revise = GenerationMetricsDelta.forCall(
                "REVISE", ChapterTokenUsage.of(30L, 20L, 50L));
        GenerationMetricsDelta compression = GenerationMetricsDelta.forCall(
                "COMPRESSION", ChapterTokenUsage.of(10L, 5L, 15L));

        GenerationMetricsDelta actual = first.plus(review).plus(revise).plus(compression);

        System.out.printf(
                "GenerationMetricsDelta 聚合：draft=%d, review=%d, revise=%d, compression=%d, "
                        + "input=%s, output=%s, total=%s%n",
                actual.draftCalls(),
                actual.reviewCalls(),
                actual.reviseCalls(),
                actual.compressionCalls(),
                actual.inputTokens(),
                actual.outputTokens(),
                actual.totalTokens()
        );
        assertEquals(1, actual.draftCalls());
        assertEquals(1, actual.reviewCalls());
        assertEquals(1, actual.reviseCalls());
        assertEquals(1, actual.compressionCalls());
        assertEquals(140L, actual.inputTokens());
        assertEquals(65L, actual.outputTokens());
        assertEquals(205L, actual.totalTokens());
    }

    @Test
    void shouldKeepTokenFieldsNullWhenEveryProviderResponseOmitsUsage() {
        GenerationMetricsDelta actual = GenerationMetricsDelta.forCall("REVIEW", null)
                .plus(GenerationMetricsDelta.tokens(null));

        System.out.printf(
                "缺少 Usage 时指标：review=%d, input=%s, output=%s, total=%s%n",
                actual.reviewCalls(),
                actual.inputTokens(),
                actual.outputTokens(),
                actual.totalTokens()
        );
        assertEquals(1, actual.reviewCalls());
        assertNull(actual.inputTokens());
        assertNull(actual.outputTokens());
        assertNull(actual.totalTokens());
    }
}
