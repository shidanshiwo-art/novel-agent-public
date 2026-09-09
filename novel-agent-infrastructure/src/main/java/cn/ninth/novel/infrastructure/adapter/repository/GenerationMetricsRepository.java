package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.adapter.repository.IGenerationMetricsRepository;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.infrastructure.dao.IGenerationMetricsDao;
import cn.ninth.novel.infrastructure.dao.po.GenerationMetricsPO;
import cn.ninth.novel.infrastructure.dao.po.GenerationMetricsSummaryPO;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis 章节生成指标仓储实现。 */
@Repository
public class GenerationMetricsRepository implements IGenerationMetricsRepository {

    private final IGenerationMetricsDao generationMetricsDao;

    public GenerationMetricsRepository(IGenerationMetricsDao generationMetricsDao) {
        this.generationMetricsDao = generationMetricsDao;
    }

    @Override
    public void save(GenerationMetricsDO metrics) {
        if (metrics == null) {
            return;
        }
        generationMetricsDao.insertOrUpdate(toPO(metrics));
    }

    @Override
    public GenerationMetricsDO find(
            Long projectId,
            Integer chapterNumber,
            String generationSessionId
    ) {
        GenerationMetricsPO metrics = generationMetricsDao.queryByIdentity(
                projectId, chapterNumber, generationSessionId);
        return metrics == null ? null : toDO(metrics);
    }

    @Override
    public List<GenerationMetricsDO> findByChapter(Long projectId, Integer chapterNumber) {
        List<GenerationMetricsPO> metrics = generationMetricsDao.queryByChapter(
                projectId, chapterNumber);
        return metrics == null ? List.of() : metrics.stream().map(this::toDO).toList();
    }

    @Override
    public GenerationMetricsSummaryVO summarizeByProject(Long projectId) {
        GenerationMetricsSummaryPO summary = generationMetricsDao.queryProjectSummary(projectId);
        if (summary == null || summary.getSessionCount() == null || summary.getSessionCount() == 0L) {
            return GenerationMetricsSummaryVO.empty();
        }
        Long totalTokens = summary.getTotalTokens();
        if (totalTokens == null
                && (summary.getInputTokens() != null || summary.getOutputTokens() != null)) {
            totalTokens = (summary.getInputTokens() == null ? 0L : summary.getInputTokens())
                    + (summary.getOutputTokens() == null ? 0L : summary.getOutputTokens());
        }
        return new GenerationMetricsSummaryVO(
                value(summary.getSessionCount()),
                value(summary.getDraftCalls()),
                value(summary.getReviewCalls()),
                value(summary.getReviseCalls()),
                value(summary.getCompressionCalls()),
                value(summary.getReviseRounds()),
                value(summary.getRetryCount()),
                value(summary.getHumanIntervenedCount()),
                value(summary.getGenerationDurationMs()),
                summary.getInputTokens(),
                summary.getOutputTokens(),
                totalTokens,
                value(summary.getFinalWordCount()),
                summary.getStartedAt(),
                summary.getEndedAt()
        );
    }

    private GenerationMetricsPO toPO(GenerationMetricsDO metrics) {
        GenerationMetricsPO po = new GenerationMetricsPO();
        po.setProjectId(metrics.getProjectId());
        po.setChapterNumber(metrics.getChapterNumber());
        po.setGenerationSessionId(metrics.getGenerationSessionId());
        po.setDraftCalls(metrics.getDraftCalls());
        po.setReviewCalls(metrics.getReviewCalls());
        po.setReviseCalls(metrics.getReviseCalls());
        po.setCompressionCalls(metrics.getCompressionCalls());
        po.setReviseRounds(metrics.getReviseRounds());
        po.setRetryCount(metrics.getRetryCount());
        po.setHumanIntervened(metrics.getHumanIntervened());
        po.setGenerationDurationMs(metrics.getGenerationDurationMs());
        po.setStartedAt(metrics.getStartedAt());
        po.setEndedAt(metrics.getEndedAt());
        po.setInputTokens(metrics.getInputTokens());
        po.setOutputTokens(metrics.getOutputTokens());
        po.setTotalTokens(metrics.getTotalTokens());
        po.setFinalWordCount(metrics.getFinalWordCount());
        po.setCreatedAt(metrics.getCreatedAt());
        po.setUpdatedAt(metrics.getUpdatedAt());
        return po;
    }

    private GenerationMetricsDO toDO(GenerationMetricsPO metrics) {
        return GenerationMetricsDO.builder()
                .projectId(metrics.getProjectId())
                .chapterNumber(metrics.getChapterNumber())
                .generationSessionId(metrics.getGenerationSessionId())
                .draftCalls(metrics.getDraftCalls())
                .reviewCalls(metrics.getReviewCalls())
                .reviseCalls(metrics.getReviseCalls())
                .compressionCalls(metrics.getCompressionCalls())
                .reviseRounds(metrics.getReviseRounds())
                .retryCount(metrics.getRetryCount())
                .humanIntervened(metrics.getHumanIntervened())
                .generationDurationMs(metrics.getGenerationDurationMs())
                .startedAt(metrics.getStartedAt())
                .endedAt(metrics.getEndedAt())
                .inputTokens(metrics.getInputTokens())
                .outputTokens(metrics.getOutputTokens())
                .totalTokens(metrics.getTotalTokens())
                .finalWordCount(metrics.getFinalWordCount())
                .createdAt(metrics.getCreatedAt())
                .updatedAt(metrics.getUpdatedAt())
                .build();
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
