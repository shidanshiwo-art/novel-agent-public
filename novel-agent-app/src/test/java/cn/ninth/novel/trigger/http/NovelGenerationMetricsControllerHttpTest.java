package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证指标查询接口的章节明细和项目汇总响应。 */
@WebMvcTest(NovelGenerationMetricsController.class)
@Import(NovelGenerationMetricsControllerHttpTest.ControllerTestConfiguration.class)
@ActiveProfiles("test")
class NovelGenerationMetricsControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldQueryChapterMetricsByProjectAndChapter() throws Exception {
        System.out.println("查询章节指标：projectCode=novel-001，chapterNumber=3");

        mockMvc.perform(get(
                        "/api/v1/novels/projects/novel-001/chapters/3/metrics"
                ))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].projectId").value("novel-001"))
                .andExpect(jsonPath("$.data[0].chapterNumber").value(3))
                .andExpect(jsonPath("$.data[0].generationSessionId")
                        .value("session-002"))
                .andExpect(jsonPath("$.data[0].reviewCalls").value(2))
                .andExpect(jsonPath("$.data[0].totalTokens").value(600L));

        System.out.println("章节指标查询通过：返回同一章节的 2 个 generation session");
    }

    @Test
    void shouldQueryProjectMetricSummary() throws Exception {
        System.out.println("查询项目指标汇总：projectCode=novel-001");

        mockMvc.perform(get("/api/v1/novels/projects/novel-001/metrics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.totalGeneratedChapters").value(2))
                .andExpect(jsonPath("$.data.averageGenerationDurationMs")
                        .value(1500.0))
                .andExpect(jsonPath("$.data.averageReviewCalls").value(1.5))
                .andExpect(jsonPath("$.data.averageReviseRounds").value(0.5))
                .andExpect(jsonPath("$.data.hitlCount").value(1))
                .andExpect(jsonPath("$.data.hitlRate").value(0.5))
                .andExpect(jsonPath("$.data.totalTokenUsage").value(1500))
                .andExpect(jsonPath("$.data.averageFinalWordCount").value(750.0));

        System.out.println("项目汇总查询通过：耗时、Review、Revise、HITL、Token 和字数均按 session 聚合");
    }

    @TestConfiguration
    static class ControllerTestConfiguration {

        @Bean
        IChapterService chapterService() {
            return new StubChapterService();
        }
    }

    private static final class StubChapterService implements IChapterService {

        @Override
        public ChapterGenerationResultVO generateChapter(
                String projectCode,
                int chapterNumber
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChapterGenerationResultVO resumeChapter(
                String workflowId,
                HumanDecisionEnum decision
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GenerationMetricsDO> findGenerationMetrics(
                String projectCode,
                int chapterNumber
        ) {
            LocalDateTime startedAt = LocalDateTime.of(2026, 9, 7, 10, 0);
            return List.of(
                    metrics("session-002", 1, 2, 1, 1, false, 2_000L, 600L, 1_000),
                    metrics("session-001", 1, 1, 0, 0, true, 1_000L, 300L, 500)
            ).stream()
                    .map(metrics -> {
                        metrics.setStartedAt(startedAt);
                        metrics.setEndedAt(startedAt.plusNanos(
                                metrics.getGenerationDurationMs() * 1_000_000L));
                        return metrics;
                    })
                    .toList();
        }

        @Override
        public GenerationMetricsSummaryVO summarizeGenerationMetrics(
                String projectCode
        ) {
            return new GenerationMetricsSummaryVO(
                    2L,
                    2L,
                    3L,
                    1L,
                    0L,
                    1L,
                    2L,
                    1L,
                    3_000L,
                    1_000L,
                    500L,
                    1_500L,
                    1_500L,
                    LocalDateTime.of(2026, 9, 7, 10, 0),
                    LocalDateTime.of(2026, 9, 7, 10, 0, 3)
            );
        }

        private GenerationMetricsDO metrics(
                String sessionId,
                int draftCalls,
                int reviewCalls,
                int reviseCalls,
                int reviseRounds,
                boolean humanIntervened,
                long durationMs,
                long totalTokens,
                int finalWordCount
        ) {
            return GenerationMetricsDO.builder()
                    .projectId(1L)
                    .chapterNumber(3)
                    .generationSessionId(sessionId)
                    .draftCalls(draftCalls)
                    .reviewCalls(reviewCalls)
                    .reviseCalls(reviseCalls)
                    .compressionCalls(1)
                    .reviseRounds(reviseRounds)
                    .retryCount(0)
                    .humanIntervened(humanIntervened)
                    .generationDurationMs(durationMs)
                    .inputTokens(totalTokens)
                    .outputTokens(0L)
                    .totalTokens(totalTokens)
                    .finalWordCount(finalWordCount)
                    .build();
        }
    }
}
