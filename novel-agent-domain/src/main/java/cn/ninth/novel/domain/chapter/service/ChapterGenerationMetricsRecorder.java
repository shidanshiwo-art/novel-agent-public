package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.adapter.repository.IGenerationMetricsRepository;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 章节生成指标采集器。
 *
 * <p>采集器是工作流的旁路能力。每个公开方法都隔离指标仓储异常，
 * 这样数据库或查询异常不会改变正文生成、审核和路由结果。</p>
 */
@Slf4j
@Component
public class ChapterGenerationMetricsRecorder {

    private final IGenerationMetricsRepository metricsRepository;
    private final INovelProjectRepository projectRepository;
    private final ConcurrentMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionIdentity> sessionIdentities = new ConcurrentHashMap<>();

    public ChapterGenerationMetricsRecorder() {
        this(null, null);
    }

    @Autowired
    public ChapterGenerationMetricsRecorder(
            IGenerationMetricsRepository metricsRepository,
            INovelProjectRepository projectRepository
    ) {
        this.metricsRepository = metricsRepository;
        this.projectRepository = projectRepository;
    }

    /** 记录一次 generation session 的开始时间。重复恢复同一会话时保留原开始时间。 */
    public void startSession(String workflowId, String projectCode, int chapterNumber) {
        if (!enabled() || blank(workflowId) || blank(projectCode)) {
            return;
        }
        sessionIdentities.putIfAbsent(
                workflowId,
                new SessionIdentity(projectCode, chapterNumber)
        );
        safely(workflowId, () -> withSessionLock(workflowId, () -> {
            Long projectId = projectRepository.findProjectId(projectCode);
            if (projectId == null) {
                return;
            }
            GenerationMetricsDO metrics = findOrCreate(
                    projectId, chapterNumber, workflowId);
            if (metrics.getStartedAt() == null) {
                metrics.setStartedAt(LocalDateTime.now());
            }
            metricsRepository.save(metrics);
        }));
    }

    /** 记录节点实际模型调用次数、Usage 增量及本次 retryCount 增量。 */
    public void recordStageResult(
            ChapterGraphState state,
            String stage,
            Map<String, Object> update
    ) {
        if (!enabled() || state == null || update == null || update.isEmpty()) {
            return;
        }
        String workflowId = state.workflowId().orElse(null);
        String projectCode = state.projectCode().orElse(null);
        Integer chapterNumber = state.chapterNumber().orElse(null);
        if (blank(workflowId) || blank(projectCode) || chapterNumber == null) {
            return;
        }
        safely(workflowId, () -> withSessionLock(workflowId, () -> {
            Long projectId = projectRepository.findProjectId(projectCode);
            if (projectId == null) {
                return;
            }
            GenerationMetricsDO metrics = findOrCreate(
                    projectId, chapterNumber, workflowId);
            int retryDelta = Math.max(
                    0,
                    integerValue(update.get(ChapterGraphKeys.RETRY_COUNT))
                            - state.retryCount()
            );
            metrics.setRetryCount(nonNegative(metrics.getRetryCount()) + retryDelta);
            GenerationMetricsDelta delta = update.get(ChapterGraphKeys.GENERATION_METRICS_DELTA)
                    instanceof GenerationMetricsDelta value
                    ? value
                    : GenerationMetricsDelta.forCall(stage, null);
            switch (stage) {
                case "DRAFT" -> metrics.setDraftCalls(
                        nonNegative(metrics.getDraftCalls()) + delta.draftCalls());
                case "REVIEW" -> metrics.setReviewCalls(
                        nonNegative(metrics.getReviewCalls()) + delta.reviewCalls());
                case "REVISE" -> {
                    metrics.setReviseCalls(
                            nonNegative(metrics.getReviseCalls()) + delta.reviseCalls());
                    metrics.setReviseRounds(
                            nonNegative(metrics.getReviseRounds()) + 1);
                }
                case "COMPRESSION" -> metrics.setCompressionCalls(
                        nonNegative(metrics.getCompressionCalls()) + delta.compressionCalls());
                default -> {
                    return;
                }
            }
            metrics.setInputTokens(addNullable(metrics.getInputTokens(), delta.inputTokens()));
            metrics.setOutputTokens(addNullable(metrics.getOutputTokens(), delta.outputTokens()));
            metrics.setTotalTokens(addNullable(metrics.getTotalTokens(), delta.totalTokens()));
            metricsRepository.save(metrics);
        }));
    }

    /** 标记工作流首次进入 HUMAN 节点。 */
    public void markHumanIntervened(ChapterGraphState state) {
        if (!enabled() || state == null) {
            return;
        }
        String workflowId = state.workflowId().orElse(null);
        String projectCode = state.projectCode().orElse(null);
        Integer chapterNumber = state.chapterNumber().orElse(null);
        if (blank(workflowId) || blank(projectCode) || chapterNumber == null) {
            return;
        }
        safely(workflowId, () -> withSessionLock(workflowId, () -> {
            Long projectId = projectRepository.findProjectId(projectCode);
            if (projectId == null) {
                return;
            }
            GenerationMetricsDO metrics = findOrCreate(
                    projectId, chapterNumber, workflowId);
            metrics.setHumanIntervened(true);
            metricsRepository.save(metrics);
        }));
    }

    /** 记录终态结束时间、耗时和最终正文词数。 */
    public void finishSession(
            String workflowId,
            String projectCode,
            int chapterNumber,
            String finalContent
    ) {
        if (!enabled() || blank(workflowId) || blank(projectCode)) {
            return;
        }
        safely(workflowId, () -> withSessionLock(workflowId, () -> {
            Long projectId = projectRepository.findProjectId(projectCode);
            if (projectId == null) {
                return;
            }
            GenerationMetricsDO metrics = findOrCreate(
                    projectId, chapterNumber, workflowId);
            LocalDateTime endedAt = metrics.getEndedAt();
            if (endedAt == null) {
                endedAt = LocalDateTime.now();
                metrics.setEndedAt(endedAt);
            }
            if (metrics.getStartedAt() == null) {
                metrics.setStartedAt(endedAt);
            }
            if (metrics.getGenerationDurationMs() == null) {
                metrics.setGenerationDurationMs(durationMillis(
                        metrics.getStartedAt(), endedAt));
            }
            if (finalContent != null) {
                metrics.setFinalWordCount(countNonWhitespaceCodePoints(finalContent));
            }
            metricsRepository.save(metrics);
        }));
    }

    /** 使用采集器记录的会话身份结束一次恢复流程。 */
    public void finishSession(String workflowId, String finalContent) {
        SessionIdentity identity = sessionIdentities.get(workflowId);
        if (identity != null) {
            finishSession(
                    workflowId,
                    identity.projectCode(),
                    identity.chapterNumber(),
                    finalContent
            );
        }
    }

    /**
     * 释放 generation session 在 JVM 内维护的身份和互斥对象。
     *
     * <p>终态指标已经写入后，这些对象不再承担任何业务语义；释放操作幂等，
     * 由章节工作流的终态 finally 统一调用。</p>
     */
    public void releaseSession(String workflowId) {
        if (blank(workflowId)) {
            return;
        }
        sessionIdentities.remove(workflowId);
        sessionLocks.remove(workflowId);
    }

    public GenerationMetricsDO find(
            String projectCode,
            int chapterNumber,
            String generationSessionId
    ) {
        Long projectId = requireProjectId(projectCode);
        return metricsRepository.find(projectId, chapterNumber, generationSessionId);
    }

    public List<GenerationMetricsDO> findByChapter(
            String projectCode,
            int chapterNumber
    ) {
        Long projectId = requireProjectId(projectCode);
        return metricsRepository.findByChapter(projectId, chapterNumber);
    }

    public GenerationMetricsSummaryVO summarizeByProject(String projectCode) {
        Long projectId = requireProjectId(projectCode);
        return metricsRepository.summarizeByProject(projectId);
    }

    private GenerationMetricsDO findOrCreate(
            Long projectId,
            int chapterNumber,
            String workflowId
    ) {
        GenerationMetricsDO existing = metricsRepository.find(
                projectId, chapterNumber, workflowId);
        if (existing != null) {
            return existing;
        }
        return GenerationMetricsDO.builder()
                .projectId(projectId)
                .chapterNumber(chapterNumber)
                .generationSessionId(workflowId)
                .draftCalls(0)
                .reviewCalls(0)
                .reviseCalls(0)
                .compressionCalls(0)
                .reviseRounds(0)
                .retryCount(0)
                .humanIntervened(false)
                .finalWordCount(0)
                .build();
    }

    private Long requireProjectId(String projectCode) {
        if (blank(projectCode) || projectRepository == null) {
            throw AppException.user(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "项目标识不能为空"
            );
        }
        Long projectId = projectRepository.findProjectId(projectCode);
        if (projectId == null) {
            throw AppException.user(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "项目不存在"
            );
        }
        return projectId;
    }

    private void safely(String workflowId, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            log.warn("章节生成指标采集失败，workflowId={}，指标将被忽略", workflowId, exception);
        }
    }

    private void withSessionLock(String workflowId, Runnable operation) {
        Object lock = sessionLocks.computeIfAbsent(workflowId, ignored -> new Object());
        synchronized (lock) {
            operation.run();
        }
    }

    private boolean enabled() {
        return metricsRepository != null && projectRepository != null;
    }

    private int integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Long addNullable(Long current, Long increment) {
        if (current == null) {
            return increment;
        }
        if (increment == null) {
            return current;
        }
        return current + increment;
    }

    private long durationMillis(LocalDateTime startedAt, LocalDateTime endedAt) {
        return Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
    }

    private int countNonWhitespaceCodePoints(String content) {
        return (int) content.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record SessionIdentity(String projectCode, int chapterNumber) {
    }
}
