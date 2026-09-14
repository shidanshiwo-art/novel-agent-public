package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter.MAX_REVISE_ROUND;

/**
 * 只补跑一个已清理失败 run 的 V1-improved Chapter 16。
 * 默认关闭；不对真实模型文本做 Assert，只打印最终结果和观测指标。
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class V1ImprovedChapter16RealModelIT {

    private static final String PROJECT_CODE = "novel-001-v1-improved-exp";
    private static final int CHAPTER_NUMBER = 16;

    @Autowired
    private IChapterService chapterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rerunOnlyImprovedChapter16() {
        if (!"true".equalsIgnoreCase(System.getProperty("runV1ImprovedChapter16"))) {
            System.out.println(
                    "V1-improved Chapter 16 real-model rerun is disabled; "
                            + "use -DrunV1ImprovedChapter16=true explicitly.");
            return;
        }

        long beforeEvents = count(
                "SELECT COUNT(*) FROM memory_canonical_event WHERE project_code = ?",
                PROJECT_CODE);
        long beforeFacts = count(
                "SELECT COUNT(*) FROM memory_canonical_fact WHERE project_code = ?",
                PROJECT_CODE);
        long beforeProjections = count(
                "SELECT COUNT(*) FROM memory_canonical_projection WHERE project_code = ?",
                PROJECT_CODE);

        ChapterGenerationResultVO result = chapterService.generateChapter(
                PROJECT_CODE,
                CHAPTER_NUMBER,
                MemoryMode.V1,
                ChapterGenerationVariant.V1_IMPROVED);
        GenerationMetricsDO metrics = chapterService.findGenerationMetrics(
                PROJECT_CODE,
                CHAPTER_NUMBER,
                result.workflowId());

        if (isHumanCheckpoint(result.status())
                && metrics != null
                && metrics.getReviseRounds() >= MAX_REVISE_ROUND) {
            result = chapterService.resumeChapter(result.workflowId(), HumanDecisionEnum.PASS);
            metrics = chapterService.findGenerationMetrics(
                    PROJECT_CODE,
                    CHAPTER_NUMBER,
                    result.workflowId());
        }

        printResult(result, metrics, beforeEvents, beforeFacts, beforeProjections);
    }

    private void printResult(
            ChapterGenerationResultVO result,
            GenerationMetricsDO metrics,
            long beforeEvents,
            long beforeFacts,
            long beforeProjections
    ) {
        long events = count(
                "SELECT COUNT(*) FROM memory_canonical_event WHERE project_code = ?",
                PROJECT_CODE);
        long facts = count(
                "SELECT COUNT(*) FROM memory_canonical_fact WHERE project_code = ?",
                PROJECT_CODE);
        long projections = count(
                "SELECT COUNT(*) FROM memory_canonical_projection WHERE project_code = ?",
                PROJECT_CODE);
        long commits = count(
                "SELECT COUNT(*) FROM memory_commit WHERE project_code = ?",
                PROJECT_CODE);
        long accepted = count(
                "SELECT COUNT(*) FROM memory_accepted_chapter_version WHERE project_code = ?",
                PROJECT_CODE);

        Map<String, Object> retrieval = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS request_count,
                       COALESCE(SUM(selected_count), 0) AS selected_items,
                       COALESCE(SUM(estimated_tokens), 0) AS estimated_tokens,
                       COALESCE(AVG(selected_count), 0) AS average_selected_items,
                       COALESCE(MAX(selected_count), 0) AS max_selected_items,
                       COALESCE(AVG(estimated_tokens), 0) AS average_estimated_tokens,
                       COALESCE(MAX(estimated_tokens), 0) AS max_estimated_tokens,
                       MAX(canonical_hit) AS canonical_hit,
                       MAX(legacy_fallback_requested) AS fallback_requested,
                       MAX(legacy_fallback_hit) AS fallback_hit
                FROM memory_retrieval_metrics
                WHERE project_code = ? AND chapter_number = ? AND generation_id = ?
                """, PROJECT_CODE, CHAPTER_NUMBER, result.workflowId());

        List<Map<String, Object>> traces = jdbcTemplate.queryForList("""
                SELECT node, success, error_code, COUNT(*) AS call_count
                FROM chapter_model_trace
                WHERE project_code = ? AND chapter_number = ? AND workflow_id = ?
                GROUP BY node, success, error_code
                ORDER BY node, success, error_code
                """, PROJECT_CODE, CHAPTER_NUMBER, result.workflowId());

        System.out.println("========== V1-IMPROVED CH16 RESULT ==========");
        System.out.println("status=" + result.status());
        System.out.println("workflowId=" + result.workflowId());
        System.out.println("currentChapterNumber=" + currentChapterNumber());
        System.out.println("commit=" + commits + ", accepted=" + accepted);
        System.out.println(
                "eventDelta=" + (events - beforeEvents)
                        + ", factDelta=" + (facts - beforeFacts)
                        + ", projectionDelta=" + (projections - beforeProjections));
        System.out.println("metrics=" + metrics);
        System.out.println("retrieval=" + retrieval);
        System.out.println("traces=" + traces);
        System.out.println("========== END V1-IMPROVED CH16 RESULT ==========");
    }

    private boolean isHumanCheckpoint(ChapterWorkflowStatusEnum status) {
        return status == ChapterWorkflowStatusEnum.WAITING_HUMAN
                || status == ChapterWorkflowStatusEnum.REVIEW_FAILED;
    }

    private long count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private long currentChapterNumber() {
        Number value = jdbcTemplate.queryForObject(
                "SELECT current_chapter_number FROM novel_project WHERE project_code = ?",
                Number.class,
                PROJECT_CODE);
        return value == null ? 0L : value.longValue();
    }
}
