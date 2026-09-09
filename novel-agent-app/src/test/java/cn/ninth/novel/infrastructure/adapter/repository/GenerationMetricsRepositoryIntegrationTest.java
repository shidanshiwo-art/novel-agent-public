package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.adapter.repository.IGenerationMetricsRepository;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 MySQL 验证章节生成指标的 Repository、DAO 和 MyBatis 映射。 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class GenerationMetricsRepositoryIntegrationTest {

    private final String projectCode = "generation-metrics-test-" + UUID.randomUUID();

    @Autowired
    private IGenerationMetricsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long projectId;

    @BeforeEach
    void setUpProject() {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count,
                     words_per_chapter, current_chapter_number, status)
                VALUES (?, 'Generation metrics test', '玄幻', 10, 2000, 0, 'DRAFT')
                """, projectCode);
        projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?", Long.class, projectCode);
        System.out.println("[GenerationMetrics] 已创建测试项目，projectId=" + projectId);
    }

    @AfterEach
    void cleanUpProject() {
        TestProjectDataCleanup.deleteProject(jdbcTemplate, projectId);
    }

    @Test
    void shouldPersistAndUpsertAllGenerationMetrics() {
        GenerationMetricsDO first = metrics(
                2, 1, 3, 1, 2, 3, false, 12_345L, 6_789L, 2_345L, 9_134L, 2_100);
        repository.save(first);
        System.out.println("[GenerationMetrics] 已保存首版指标，session=" + first.getGenerationSessionId());

        GenerationMetricsDO stored = repository.find(
                projectId, 1, first.getGenerationSessionId());
        assertNotNull(stored);
        assertMetrics(first, stored);
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());

        GenerationMetricsDO updated = metrics(
                3, 2, 4, 2, 3, 4, true, 22_345L, 16_789L, 12_345L, 29_134L, 2_500);
        updated.setGenerationSessionId(first.getGenerationSessionId());
        repository.save(updated);
        System.out.println("[GenerationMetrics] 已按复合键更新指标，humanIntervened="
                + updated.getHumanIntervened());

        GenerationMetricsDO reloaded = repository.find(
                projectId, 1, first.getGenerationSessionId());
        assertNotNull(reloaded);
        assertMetrics(updated, reloaded);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM generation_metrics WHERE project_id = ?", Integer.class, projectId));
        assertTrue(reloaded.getUpdatedAt().compareTo(reloaded.getCreatedAt()) >= 0);
        System.out.println("[GenerationMetrics] 验证通过：复合键幂等更新后仍只有一条记录。");
    }

    @Test
    void shouldQueryChapterSessionsAndProjectSummary() {
        GenerationMetricsDO first = metrics(
                2, 1, 1, 1, 1, 2, false, 1_000L, 100L, 50L, 150L, 100);
        first.setGenerationSessionId("session-summary-1");
        first.setStartedAt(LocalDateTime.of(2026, 9, 7, 10, 0));
        first.setEndedAt(LocalDateTime.of(2026, 9, 7, 10, 0, 1));
        repository.save(first);

        GenerationMetricsDO second = metrics(
                1, 2, 2, 2, 3, 1, true, 2_000L, 200L, 100L, 300L, 200);
        second.setGenerationSessionId("session-summary-2");
        second.setStartedAt(LocalDateTime.of(2026, 9, 7, 10, 1));
        second.setEndedAt(LocalDateTime.of(2026, 9, 7, 10, 1, 2));
        repository.save(second);

        var chapterMetrics = repository.findByChapter(projectId, 1);
        var summary = repository.summarizeByProject(projectId);
        assertEquals(2, chapterMetrics.size());
        assertEquals(2L, summary.sessionCount());
        assertEquals(3L, summary.draftCalls());
        assertEquals(3L, summary.reviewCalls());
        assertEquals(3L, summary.reviseCalls());
        assertEquals(3L, summary.compressionCalls());
        assertEquals(4L, summary.reviseRounds());
        assertEquals(3L, summary.retryCount());
        assertEquals(1L, summary.hitlCount());
        assertEquals(3_000L, summary.generationDurationMs());
        assertEquals(300L, summary.inputTokens());
        assertEquals(150L, summary.outputTokens());
        assertEquals(450L, summary.totalTokens());
        assertEquals(300L, summary.finalWordCount());
        System.out.println("[GenerationMetrics] 查询聚合通过：chapter sessions=2, totalTokens=450, hitl=1");
    }

    @Test
    void shouldKeepUnavailableTokenUsageAsNull() {
        GenerationMetricsDO metrics = metrics(
                1, 1, 0, 0, 0, 0, false, 500L, 0L, 0L, 0L, 50);
        metrics.setGenerationSessionId("session-token-null");
        metrics.setInputTokens(null);
        metrics.setOutputTokens(null);
        metrics.setTotalTokens(null);
        repository.save(metrics);

        GenerationMetricsDO stored = repository.find(
                projectId, 1, "session-token-null");
        var summary = repository.summarizeByProject(projectId);
        assertNotNull(stored);
        assertNull(stored.getInputTokens());
        assertNull(stored.getOutputTokens());
        assertNull(stored.getTotalTokens());
        assertNull(summary.inputTokens());
        assertNull(summary.outputTokens());
        assertNull(summary.totalTokens());
        System.out.println("[GenerationMetrics] Token 不可得语义通过：明细和项目汇总均返回 null");
    }

    private GenerationMetricsDO metrics(
            int draftCalls,
            int reviewCalls,
            int reviseCalls,
            int compressionCalls,
            int reviseRounds,
            int retryCount,
            boolean humanIntervened,
            long durationMs,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            int finalWordCount
    ) {
        return GenerationMetricsDO.builder()
                .projectId(projectId)
                .chapterNumber(1)
                .generationSessionId(UUID.randomUUID().toString())
                .draftCalls(draftCalls)
                .reviewCalls(reviewCalls)
                .reviseCalls(reviseCalls)
                .compressionCalls(compressionCalls)
                .reviseRounds(reviseRounds)
                .retryCount(retryCount)
                .humanIntervened(humanIntervened)
                .generationDurationMs(durationMs)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .finalWordCount(finalWordCount)
                .build();
    }

    private void assertMetrics(GenerationMetricsDO expected, GenerationMetricsDO actual) {
        assertEquals(expected.getProjectId(), actual.getProjectId());
        assertEquals(expected.getChapterNumber(), actual.getChapterNumber());
        assertEquals(expected.getGenerationSessionId(), actual.getGenerationSessionId());
        assertEquals(expected.getDraftCalls(), actual.getDraftCalls());
        assertEquals(expected.getReviewCalls(), actual.getReviewCalls());
        assertEquals(expected.getReviseCalls(), actual.getReviseCalls());
        assertEquals(expected.getCompressionCalls(), actual.getCompressionCalls());
        assertEquals(expected.getReviseRounds(), actual.getReviseRounds());
        assertEquals(expected.getRetryCount(), actual.getRetryCount());
        assertEquals(expected.getHumanIntervened(), actual.getHumanIntervened());
        assertEquals(expected.getGenerationDurationMs(), actual.getGenerationDurationMs());
        assertEquals(expected.getInputTokens(), actual.getInputTokens());
        assertEquals(expected.getOutputTokens(), actual.getOutputTokens());
        assertEquals(expected.getTotalTokens(), actual.getTotalTokens());
        assertEquals(expected.getFinalWordCount(), actual.getFinalWordCount());
    }
}
