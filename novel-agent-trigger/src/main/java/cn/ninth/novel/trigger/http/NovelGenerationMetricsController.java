package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerationMetricsResponseDTO;
import cn.ninth.novel.api.dto.GenerationMetricsSummaryResponseDTO;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 章节生成运行指标查询接口。 */
@RestController
@RequestMapping("/api/v1/novels/projects")
public class NovelGenerationMetricsController {

    private final IChapterService chapterService;

    public NovelGenerationMetricsController(IChapterService chapterService) {
        this.chapterService = chapterService;
    }

    /** 查询一个章节下各 generation session 的指标。 */
    @GetMapping("/{projectCode}/chapters/{chapterNumber}/metrics")
    public Response<List<GenerationMetricsResponseDTO>> findChapterMetrics(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber
    ) {
        return Response.success(chapterService.findGenerationMetrics(projectCode, chapterNumber)
                .stream()
                .map(metrics -> toResponse(projectCode, metrics))
                .toList());
    }

    /** 查询一个 generation session 的指标。 */
    @GetMapping("/{projectCode}/chapters/{chapterNumber}/metrics/{generationSessionId}")
    public Response<GenerationMetricsResponseDTO> findSessionMetrics(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber,
            @PathVariable String generationSessionId
    ) {
        GenerationMetricsDO metrics = chapterService.findGenerationMetrics(
                projectCode, chapterNumber, generationSessionId);
        return Response.success(metrics == null ? null : toResponse(projectCode, metrics));
    }

    /** 查询项目范围的 generation session 汇总指标。 */
    @GetMapping("/{projectCode}/metrics")
    public Response<GenerationMetricsSummaryResponseDTO> summarizeProjectMetrics(
            @PathVariable String projectCode
    ) {
        return Response.success(toSummaryResponse(
                projectCode,
                chapterService.summarizeGenerationMetrics(projectCode)
        ));
    }

    private GenerationMetricsResponseDTO toResponse(
            String projectCode,
            GenerationMetricsDO metrics
    ) {
        return new GenerationMetricsResponseDTO(
                projectCode,
                metrics.getChapterNumber(),
                metrics.getGenerationSessionId(),
                metrics.getDraftCalls(),
                metrics.getReviewCalls(),
                metrics.getReviseCalls(),
                metrics.getCompressionCalls(),
                metrics.getReviseRounds(),
                metrics.getRetryCount(),
                metrics.getHumanIntervened(),
                metrics.getGenerationDurationMs(),
                metrics.getInputTokens(),
                metrics.getOutputTokens(),
                metrics.getTotalTokens(),
                metrics.getFinalWordCount(),
                metrics.getStartedAt(),
                metrics.getEndedAt(),
                metrics.getCreatedAt(),
                metrics.getUpdatedAt()
        );
    }

    private GenerationMetricsSummaryResponseDTO toSummaryResponse(
            String projectCode,
            GenerationMetricsSummaryVO summary
    ) {
        long sessions = summary.sessionCount();
        long hitlCount = summary.humanIntervenedCount();
        return new GenerationMetricsSummaryResponseDTO(
                projectCode,
                sessions,
                average(summary.generationDurationMs(), sessions),
                average(summary.reviewCalls(), sessions),
                average(summary.reviseRounds(), sessions),
                hitlCount,
                sessions == 0L ? 0D : (double) hitlCount / sessions,
                summary.totalTokens(),
                average(summary.finalWordCount(), sessions),
                sessions,
                summary.draftCalls(),
                summary.reviewCalls(),
                summary.reviseCalls(),
                summary.compressionCalls(),
                summary.reviseRounds(),
                summary.retryCount(),
                summary.humanIntervenedCount(),
                summary.generationDurationMs(),
                summary.inputTokens(),
                summary.outputTokens(),
                summary.totalTokens(),
                summary.finalWordCount(),
                summary.startedAt(),
                summary.endedAt()
        );
    }

    private double average(long total, long count) {
        return count == 0L ? 0D : (double) total / count;
    }
}
