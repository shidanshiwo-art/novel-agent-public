package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalContextItem;
import cn.ninth.novel.domain.memory.service.MemoryReconcileMetricsCollector;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import cn.ninth.novel.domain.chapter.service.agent.SystemPrompt;
import cn.ninth.novel.infrastructure.config.ChapterModelProperties;
import cn.ninth.novel.infrastructure.config.ModelStageConfig;
import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可复现 Memory A/B 实验编排器。
 *
 * <p>该类只负责实验清单、数据库隔离复制、现有章节工作流调用和指标汇总，
 * 不参与 MemoryContextProvider、Canonical Gate 或 LegacyFallbackRouter 的决策。</p>
 */
@Service
public class MemoryAbExperimentHarness {

    public static final String WAITING_HUMAN_POLICY =
            "ACCEPT_CURRENT_AFTER_AUTOMATIC_REVISION_LIMIT";
    public static final String LEGACY_GROUP = "A";
    public static final String V1_GROUP = "B";
    public static final String V1_CURRENT_GROUP = "V1-current";
    public static final String V1_IMPROVED_GROUP = "V1-improved";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final IChapterService chapterService;
    private final ChapterModelProperties chapterModelProperties;
    private final PlanningModelProperties planningModelProperties;
    private final ModelTimeoutProperties modelTimeoutProperties;
    private final ObjectMapper objectMapper;
    private final MemoryReconcileMetricsCollector reconcileMetricsCollector;

    public MemoryAbExperimentHarness(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            IChapterService chapterService,
            ChapterModelProperties chapterModelProperties,
            PlanningModelProperties planningModelProperties,
            ModelTimeoutProperties modelTimeoutProperties,
            ObjectMapper objectMapper
    ) {
        this(
                dataSource,
                transactionManager,
                chapterService,
                chapterModelProperties,
                planningModelProperties,
                modelTimeoutProperties,
                objectMapper,
                new MemoryReconcileMetricsCollector()
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MemoryAbExperimentHarness(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            IChapterService chapterService,
            ChapterModelProperties chapterModelProperties,
            PlanningModelProperties planningModelProperties,
            ModelTimeoutProperties modelTimeoutProperties,
            ObjectMapper objectMapper,
            MemoryReconcileMetricsCollector reconcileMetricsCollector
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.chapterService = Objects.requireNonNull(chapterService, "chapterService 不能为空");
        this.chapterModelProperties = Objects.requireNonNull(
                chapterModelProperties, "chapterModelProperties 不能为空");
        this.planningModelProperties = Objects.requireNonNull(
                planningModelProperties, "planningModelProperties 不能为空");
        this.modelTimeoutProperties = Objects.requireNonNull(
                modelTimeoutProperties, "modelTimeoutProperties 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.reconcileMetricsCollector = Objects.requireNonNull(
                reconcileMetricsCollector, "reconcileMetricsCollector 不能为空");
    }

    /** 使用约定项目名冻结 novel-001 的基线。 */
    public MemoryAbExperimentManifest freezeBaseline(String baselineProjectCode) {
        String base = required(baselineProjectCode, "baselineProjectCode");
        return freezeBaseline(
                base,
                base + "-legacy-exp",
                base + "-v1-exp",
                1,
                10,
                11,
                16
        );
    }

    /**
     * 只读冻结：要求第 11～16 章章纲/计划已经由既有流程生成并确认。
     * 本方法不会调用模型，也不会创建任何实验项目。
     */
    public MemoryAbExperimentManifest freezeBaseline(
            String baselineProjectCode,
            String legacyProjectCode,
            String v1ProjectCode,
            int baselineFromChapter,
            int baselineToChapter,
            int experimentFromChapter,
            int experimentToChapter
    ) {
        String base = required(baselineProjectCode, "baselineProjectCode");
        String legacy = required(legacyProjectCode, "legacyProjectCode");
        String v1 = required(v1ProjectCode, "v1ProjectCode");
        validateRange(baselineFromChapter, baselineToChapter, "baseline");
        validateRange(experimentFromChapter, experimentToChapter, "experiment");

        Map<String, Object> project = requireProject(base);
        requireFinalizedBaseline(base, baselineFromChapter, baselineToChapter);
        if (intValue(project.get("current_chapter_number")) != baselineToChapter) {
            throw new IllegalStateException(
                    "实验基线当前进度必须停在 Chapter " + baselineToChapter);
        }
        requireConfirmedBible(base);
        requireCharacters(base);
        requireFrozenExperimentOutline(
                base, experimentFromChapter, experimentToChapter);
        requireNoExperimentBodies(base, experimentFromChapter, experimentToChapter);

        String baselineFingerprint = projectFingerprint(
                base, baselineFromChapter, baselineToChapter);
        String outlineFingerprint = outlineFingerprint(
                base, experimentFromChapter, experimentToChapter);

        return new MemoryAbExperimentManifest(
                "memory-ab-" + UUID.randomUUID(),
                base,
                legacy,
                v1,
                baselineFromChapter,
                baselineToChapter,
                experimentFromChapter,
                experimentToChapter,
                baselineFingerprint,
                outlineFingerprint,
                configurationFingerprint(),
                WAITING_HUMAN_POLICY,
                ReviewRouter.MAX_REVISE_ROUND,
                HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND,
                Instant.now()
        );
    }

    /** 使用同一 V1 baseline 冻结 V1-current/V1-improved 对照实验。 */
    public V1ComparisonManifest freezeV1Comparison(String baselineProjectCode) {
        String base = required(baselineProjectCode, "baselineProjectCode");
        return freezeV1Comparison(
                base,
                base + "-v1-current-exp",
                base + "-v1-improved-exp",
                1,
                10,
                11,
                16
        );
    }

    /**
     * 只读冻结 V1 对照的共同输入；两组都保留 Chapter 1～10 Canonical Memory。
     */
    public V1ComparisonManifest freezeV1Comparison(
            String baselineProjectCode,
            String currentProjectCode,
            String improvedProjectCode,
            int baselineFromChapter,
            int baselineToChapter,
            int experimentFromChapter,
            int experimentToChapter
    ) {
        String base = required(baselineProjectCode, "baselineProjectCode");
        String current = required(currentProjectCode, "currentProjectCode");
        String improved = required(improvedProjectCode, "improvedProjectCode");
        validateRange(baselineFromChapter, baselineToChapter, "baseline");
        validateRange(experimentFromChapter, experimentToChapter, "experiment");

        Map<String, Object> project = requireProject(base);
        requireFinalizedBaseline(base, baselineFromChapter, baselineToChapter);
        if (intValue(project.get("current_chapter_number")) != baselineToChapter) {
            throw new IllegalStateException(
                    "实验基线当前进度必须停在 Chapter " + baselineToChapter);
        }
        requireConfirmedBible(base);
        requireCharacters(base);
        requireFrozenExperimentOutline(base, experimentFromChapter, experimentToChapter);
        requireNoExperimentBodies(base, experimentFromChapter, experimentToChapter);

        return new V1ComparisonManifest(
                "v1-comparison-" + UUID.randomUUID(),
                base,
                current,
                improved,
                baselineFromChapter,
                baselineToChapter,
                experimentFromChapter,
                experimentToChapter,
                projectFingerprint(base, baselineFromChapter, baselineToChapter),
                outlineFingerprint(base, experimentFromChapter, experimentToChapter),
                configurationFingerprint(),
                WAITING_HUMAN_POLICY,
                ReviewRouter.MAX_REVISE_ROUND,
                HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND,
                Instant.now()
        );
    }

    /**
     * 在一个事务内创建 A/B 两份项目。目标项目必须不存在；不会覆盖已有实验数据。
     * A 只复制 Legacy 可读业务数据，B 额外复制隔离后的 V1 Canonical 数据。
     */
    public MemoryAbIsolationReport cloneEnvironments(
            MemoryAbExperimentManifest manifest
    ) {
        Objects.requireNonNull(manifest, "实验清单不能为空");
        verifyBaselineStillFrozen(manifest);
        ensureProjectAbsent(manifest.legacyProjectCode());
        ensureProjectAbsent(manifest.v1ProjectCode());

        transactionTemplate.executeWithoutResult(status -> {
            cloneProject(manifest.baselineProjectCode(), manifest.legacyProjectCode(), manifest);
            cloneProject(manifest.baselineProjectCode(), manifest.v1ProjectCode(), manifest);
            cloneCanonicalMemory(
                    manifest.baselineProjectCode(), manifest.v1ProjectCode(), manifest);
            verifyEnvironmentRows(manifest);
        });

        return isolationReport(manifest);
    }

    /** 创建两份完全相同的 V1 baseline 环境，并分别复制隔离的 Canonical 数据。 */
    public V1ComparisonIsolationReport cloneV1ComparisonEnvironments(
            V1ComparisonManifest manifest
    ) {
        Objects.requireNonNull(manifest, "实验清单不能为空");
        MemoryAbExperimentManifest compatibility = compatibilityManifest(manifest);
        verifyBaselineStillFrozen(compatibility);
        ensureProjectAbsent(manifest.currentProjectCode());
        ensureProjectAbsent(manifest.improvedProjectCode());

        transactionTemplate.executeWithoutResult(status -> {
            cloneProject(manifest.baselineProjectCode(), manifest.currentProjectCode(), compatibility);
            cloneProject(manifest.baselineProjectCode(), manifest.improvedProjectCode(), compatibility);
            cloneCanonicalMemory(
                    manifest.baselineProjectCode(), manifest.currentProjectCode(), compatibility);
            cloneCanonicalMemory(
                    manifest.baselineProjectCode(), manifest.improvedProjectCode(), compatibility);
            reconcileMetricsCollector.clear(manifest.currentProjectCode());
            reconcileMetricsCollector.clear(manifest.improvedProjectCode());
            verifyV1ComparisonEnvironmentRows(manifest);
        });

        return v1ComparisonIsolationReport(manifest);
    }

    /**
     * 从一个已完成的 V1 项目复制到指定章节为止的只读快照。
     *
     * <p>该入口只用于 REVIEW reasoning 回放：目标项目不包含待审章节正文，
     * 但保留相同的前置正文、章纲、摘要和 Canonical Memory。它不会覆盖已有项目。</p>
     */
    public void cloneProjectSnapshot(
            String sourceProjectCode,
            String targetProjectCode,
            int copyThroughChapter,
            String experimentId
    ) {
        String source = required(sourceProjectCode, "sourceProjectCode");
        String target = required(targetProjectCode, "targetProjectCode");
        if (copyThroughChapter < 1) {
            throw new IllegalArgumentException("快照章节范围无效");
        }
        String scope = required(experimentId, "experimentId") + ":" + target;
        requireProject(source);
        ensureProjectAbsent(target);
        MemoryAbExperimentManifest snapshot = new MemoryAbExperimentManifest(
                scope,
                source,
                target,
                target + "-unused",
                1,
                copyThroughChapter,
                copyThroughChapter + 1,
                copyThroughChapter + 1,
                "snapshot",
                "snapshot",
                configurationFingerprint(),
                WAITING_HUMAN_POLICY,
                ReviewRouter.MAX_REVISE_ROUND,
                HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND,
                Instant.now()
        );
        transactionTemplate.executeWithoutResult(status -> {
            cloneProject(source, target, snapshot);
            cloneCanonicalMemory(source, target, snapshot);
        });
    }

    /**
     * 按章节交错运行 A/B，确保两组使用同一份已冻结 ChapterPlan。
     * 具体生成、检索和持久化仍全部走现有章节工作流。
     */
    public MemoryAbExperimentReport run(MemoryAbExperimentManifest manifest) {
        Objects.requireNonNull(manifest, "实验清单不能为空");
        verifyEnvironmentReady(manifest);

        List<MemoryAbExperimentReport.ChapterResult> results = new ArrayList<>();
        boolean legacyActive = true;
        boolean v1Active = true;
        for (int chapter = manifest.experimentFromChapter();
             chapter <= manifest.experimentToChapter(); chapter++) {
            if (legacyActive) {
                RunOutcome outcome = runChapter(
                        LEGACY_GROUP,
                        manifest.legacyProjectCode(),
                        MemoryMode.LEGACY,
                        chapter,
                        manifest);
                results.add(outcome.result());
                legacyActive = outcome.completed();
            }
            if (v1Active) {
                RunOutcome outcome = runChapter(
                        V1_GROUP,
                        manifest.v1ProjectCode(),
                        MemoryMode.V1,
                        chapter,
                        manifest);
                results.add(outcome.result());
                v1Active = outcome.completed();
            }
        }
        return new MemoryAbExperimentReport(manifest, results);
    }

    /**
     * 交错运行 V1-current/V1-improved，避免一组连续占用模型服务导致时间因素偏差。
     * 两组均固定为 MemoryMode.V1，差异只来自 ChapterGenerationVariant。
     */
    public V1ComparisonReport runV1Comparison(V1ComparisonManifest manifest) {
        Objects.requireNonNull(manifest, "实验清单不能为空");
        verifyV1ComparisonEnvironmentReady(manifest);

        List<V1ComparisonReport.ChapterResult> results = new ArrayList<>();
        boolean currentActive = true;
        boolean improvedActive = true;
        for (int chapter = manifest.experimentFromChapter();
             chapter <= manifest.experimentToChapter(); chapter++) {
            if (currentActive) {
                V1ComparisonRunOutcome outcome = runV1Chapter(
                        V1_CURRENT_GROUP,
                        manifest.currentProjectCode(),
                        ChapterGenerationVariant.V1_CURRENT,
                        chapter,
                        manifest);
                results.add(outcome.result());
                currentActive = outcome.completed();
            }
            if (improvedActive) {
                V1ComparisonRunOutcome outcome = runV1Chapter(
                        V1_IMPROVED_GROUP,
                        manifest.improvedProjectCode(),
                        ChapterGenerationVariant.V1_IMPROVED,
                        chapter,
                        manifest);
                results.add(outcome.result());
                improvedActive = outcome.completed();
            }
        }
        return new V1ComparisonReport(manifest, results);
    }

    /** 供 CLI、集成测试或外部报告器保存 JSON，不包含 Prompt 正文。 */
    public String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    /** 从保存的清单 JSON 恢复实验身份；恢复后仍会重新校验所有指纹。 */
    public MemoryAbExperimentManifest readManifest(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("实验清单 JSON 不能为空");
        }
        return objectMapper.readValue(json, MemoryAbExperimentManifest.class);
    }

    /** 返回当前冻结配置的指纹，便于实验开始前打印并人工核对。 */
    public String configurationFingerprint() {
        StringBuilder value = new StringBuilder(16384);
        appendPromptConstants(value, SystemPrompt.class);
        appendPromptConstants(value, PlanningPrompts.class);
        appendStageConfig(value, "chapter.DRAFT", chapterModelProperties.forStage("DRAFT"));
        appendStageConfig(value, "chapter.REVIEW", chapterModelProperties.forStage("REVIEW"));
        appendStageConfig(value, "chapter.REVISION", chapterModelProperties.forStage("REVISION"));
        appendStageConfig(
                value, "chapter.COMPRESSION", chapterModelProperties.forStage("COMPRESSION"));
        appendStageConfig(
                value, "planning.NEXT_CHAPTER_OUTLINE",
                planningModelProperties.forStage("NEXT_CHAPTER_OUTLINE"));
        appendStageConfig(
                value, "planning.CHAPTER_PLAN",
                planningModelProperties.forStage("CHAPTER_PLAN"));
        modelTimeoutProperties.getStructuredTimeouts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> append(
                        value,
                        "model.structuredTimeout." + entry.getKey(),
                        entry.getValue()));
        append(value, "model.httpConnectTimeout", modelTimeoutProperties.httpConnectTimeout());
        append(value, "model.httpReadAndRequestTimeout",
                modelTimeoutProperties.httpReadAndRequestTimeout());
        append(value, "workflow.waitingHumanPolicy", WAITING_HUMAN_POLICY);
        append(value, "workflow.automaticRevisionLimit", ReviewRouter.MAX_REVISE_ROUND);
        append(value, "workflow.humanRevisionLimit", HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND);
        return sha256(value.toString());
    }

    private RunOutcome runChapter(
            String group,
            String projectCode,
            MemoryMode memoryMode,
            int chapter,
            MemoryAbExperimentManifest manifest
    ) {
        ChapterGenerationResultVO initial = chapterService.generateChapter(
                projectCode, chapter, memoryMode);
        GenerationMetricsDO initialMetrics = findMetrics(
                projectCode, chapter, initial.workflowId());
        ChapterGenerationResultVO finalResult = initial;

        // 只有自动修订已达到固定上限才采用当前正文；其它 HUMAN 状态按失败样本记录。
        if (isHumanCheckpoint(initial.status())
                && automaticRevisionLimitReached(initialMetrics, manifest)) {
            finalResult = chapterService.resumeChapter(
                    initial.workflowId(), HumanDecisionEnum.PASS);
        }

        GenerationMetricsDO metrics = findMetrics(
                projectCode, chapter, finalResult.workflowId());
        MemoryAbExperimentReport.MemoryContextMetrics context = retrievalMetrics(
                projectCode, chapter, finalResult.workflowId());
        boolean completed = finalResult.status() == ChapterWorkflowStatusEnum.COMPLETED;
        return new RunOutcome(
                new MemoryAbExperimentReport.ChapterResult(
                        chapter,
                        group,
                        memoryMode,
                        finalResult.workflowId(),
                        nonNegative(metrics == null ? null : metrics.getDraftCalls()),
                        nonNegative(metrics == null ? null : metrics.getReviewCalls()),
                        nonNegative(metrics == null ? null : metrics.getReviseCalls()),
                        metrics == null ? null : metrics.getInputTokens(),
                        metrics == null ? null : metrics.getOutputTokens(),
                        metrics == null ? null : metrics.getTotalTokens(),
                        metrics == null ? null : metrics.getGenerationDurationMs(),
                        context,
                        context.fallbackRequested() || context.fallbackHit(),
                        finalResult.status().name()
                ),
                completed
        );
    }

    private V1ComparisonRunOutcome runV1Chapter(
            String variant,
            String projectCode,
            ChapterGenerationVariant generationVariant,
            int chapter,
            V1ComparisonManifest manifest
    ) {
        ChapterGenerationResultVO initial = chapterService.generateChapter(
                projectCode, chapter, MemoryMode.V1, generationVariant);
        GenerationMetricsDO initialMetrics = findMetrics(
                projectCode, chapter, initial.workflowId());
        ChapterGenerationResultVO finalResult = initial;

        if (isHumanCheckpoint(initial.status())
                && automaticRevisionLimitReached(initialMetrics, manifest.automaticRevisionLimit())) {
            finalResult = chapterService.resumeChapter(
                    initial.workflowId(), HumanDecisionEnum.PASS);
        }

        GenerationMetricsDO metrics = findMetrics(
                projectCode, chapter, finalResult.workflowId());
        MemoryAbExperimentReport.MemoryContextMetrics context = retrievalMetrics(
                projectCode, chapter, finalResult.workflowId());
        V1ComparisonReport.ChapterResult result = new V1ComparisonReport.ChapterResult(
                chapter,
                variant,
                projectCode,
                finalResult.workflowId(),
                nonNegative(metrics == null ? null : metrics.getDraftCalls()),
                nonNegative(metrics == null ? null : metrics.getReviewCalls()),
                nonNegative(metrics == null ? null : metrics.getReviseCalls()),
                metrics == null ? null : metrics.getInputTokens(),
                metrics == null ? null : metrics.getOutputTokens(),
                metrics == null ? null : metrics.getTotalTokens(),
                metrics == null ? null : metrics.getGenerationDurationMs(),
                context,
                canonicalGrowth(projectCode),
                reconcileMetrics(projectCode, chapter),
                previousCanonicalRecall(projectCode, chapter - 1, finalResult.workflowId()),
                finalResult.status().name()
        );
        return new V1ComparisonRunOutcome(
                result, finalResult.status() == ChapterWorkflowStatusEnum.COMPLETED);
    }

    private boolean automaticRevisionLimitReached(
            GenerationMetricsDO metrics,
            MemoryAbExperimentManifest manifest
    ) {
        return automaticRevisionLimitReached(
                metrics, manifest.automaticRevisionLimit());
    }

    private boolean automaticRevisionLimitReached(
            GenerationMetricsDO metrics,
            int automaticRevisionLimit
    ) {
        return metrics != null
                && nonNegative(metrics.getReviseRounds())
                >= automaticRevisionLimit;
    }

    private V1ComparisonReport.CanonicalGrowth canonicalGrowth(String projectCode) {
        return new V1ComparisonReport.CanonicalGrowth(
                count("SELECT COUNT(*) FROM memory_commit WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_accepted_chapter_version WHERE project_code = ?",
                        projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_event WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_fact WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_projection WHERE project_code = ?",
                        projectCode),
                count("SELECT COUNT(*) FROM memory_outbox WHERE commit_key IN "
                        + "(SELECT commit_key FROM memory_commit WHERE project_code = ?)", projectCode)
        );
    }

    private V1ComparisonReport.ReconcileMetrics reconcileMetrics(
            String projectCode,
            int chapter
    ) {
        MemoryReconcileMetricsCollector.Snapshot snapshot =
                reconcileMetricsCollector.snapshot(projectCode, chapter);
        return new V1ComparisonReport.ReconcileMetrics(
                snapshot.addCount(),
                snapshot.reinforceCount(),
                snapshot.supersedeCount(),
                snapshot.invalidateCount(),
                snapshot.noopCount());
    }

    private V1ComparisonReport.PreviousCanonicalRecall previousCanonicalRecall(
            String projectCode,
            int sourceChapter,
            String generationId
    ) {
        if (sourceChapter < 1 || generationId == null || generationId.isBlank()) {
            return V1ComparisonReport.PreviousCanonicalRecall.none();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT context_items_json, estimated_tokens
                FROM memory_retrieval_metrics
                WHERE project_code = ? AND chapter_number = ? AND generation_id = ?
                ORDER BY id
                """, projectCode, sourceChapter + 1, generationId);
        Set<String> selectedIds = new HashSet<>();
        long contextTokens = 0L;
        for (Map<String, Object> row : rows) {
            String json = text(row.get("context_items_json"));
            if (json == null || json.isBlank()) {
                continue;
            }
            MemoryRetrievalContextItem[] items;
            try {
                items = objectMapper.readValue(json, MemoryRetrievalContextItem[].class);
            } catch (RuntimeException exception) {
                continue;
            }
            boolean rowHasPrevious = false;
            for (MemoryRetrievalContextItem item : items == null
                    ? new MemoryRetrievalContextItem[0] : items) {
                if (item.selected()
                        && item.sourceChapter() == sourceChapter
                        && "CANONICAL".equals(item.canonicalOrLegacy())
                        && selectedIds.add(item.itemId())) {
                    rowHasPrevious = true;
                }
            }
            if (rowHasPrevious) {
                contextTokens += longValue(row.get("estimated_tokens"));
            }
        }
        return new V1ComparisonReport.PreviousCanonicalRecall(
                sourceChapter,
                !selectedIds.isEmpty(),
                selectedIds.size(),
                contextTokens);
    }

    private boolean isHumanCheckpoint(ChapterWorkflowStatusEnum status) {
        return status == ChapterWorkflowStatusEnum.WAITING_HUMAN
                || status == ChapterWorkflowStatusEnum.REVIEW_FAILED;
    }

    private GenerationMetricsDO findMetrics(
            String projectCode,
            int chapter,
            String workflowId
    ) {
        if (workflowId == null || workflowId.isBlank()) {
            return null;
        }
        return chapterService.findGenerationMetrics(projectCode, chapter, workflowId);
    }

    private MemoryAbExperimentReport.MemoryContextMetrics retrievalMetrics(
            String projectCode,
            int chapter,
            String generationId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT profile,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(candidate_count), 0) AS candidate_count,
                       COALESCE(SUM(filtered_count), 0) AS filtered_count,
                       COALESCE(SUM(selected_count), 0) AS selected_count,
                       COALESCE(SUM(trimmed_count), 0) AS trimmed_count,
                       COALESCE(SUM(estimated_tokens), 0) AS estimated_tokens,
                       COALESCE(SUM(retrieval_latency_ms), 0) AS retrieval_latency_ms,
                       MAX(canonical_requested) AS canonical_requested,
                       MAX(canonical_hit) AS canonical_hit,
                       MAX(legacy_fallback_requested) AS fallback_requested,
                       MAX(legacy_fallback_hit) AS fallback_hit
                FROM memory_retrieval_metrics
                WHERE project_code = ? AND chapter_number = ? AND generation_id = ?
                GROUP BY profile
                ORDER BY FIELD(profile, 'PLAN', 'DRAFT', 'REVIEW'), profile
                """, projectCode, chapter, generationId);

        List<MemoryAbExperimentReport.ProfileMetrics> profiles = rows.stream()
                .map(this::toProfileMetrics)
                .toList();
        return new MemoryAbExperimentReport.MemoryContextMetrics(
                profiles,
                profiles.stream().mapToInt(MemoryAbExperimentReport.ProfileMetrics::requestCount).sum(),
                profiles.stream().mapToLong(MemoryAbExperimentReport.ProfileMetrics::candidateCount).sum(),
                profiles.stream().mapToLong(MemoryAbExperimentReport.ProfileMetrics::filteredCount).sum(),
                profiles.stream().mapToLong(MemoryAbExperimentReport.ProfileMetrics::selectedCount).sum(),
                profiles.stream().mapToLong(MemoryAbExperimentReport.ProfileMetrics::trimmedCount).sum(),
                profiles.stream().mapToLong(MemoryAbExperimentReport.ProfileMetrics::estimatedTokens).sum(),
                profiles.stream().mapToLong(MemoryAbExperimentReport.ProfileMetrics::retrievalLatencyMs).sum(),
                profiles.stream().anyMatch(MemoryAbExperimentReport.ProfileMetrics::canonicalRequested),
                profiles.stream().anyMatch(MemoryAbExperimentReport.ProfileMetrics::canonicalHit),
                profiles.stream().anyMatch(MemoryAbExperimentReport.ProfileMetrics::fallbackRequested),
                profiles.stream().anyMatch(MemoryAbExperimentReport.ProfileMetrics::fallbackHit)
        );
    }

    private MemoryAbExperimentReport.ProfileMetrics toProfileMetrics(Map<String, Object> row) {
        return new MemoryAbExperimentReport.ProfileMetrics(
                text(row.get("profile")),
                intValue(row.get("request_count")),
                longValue(row.get("candidate_count")),
                longValue(row.get("filtered_count")),
                longValue(row.get("selected_count")),
                longValue(row.get("trimmed_count")),
                longValue(row.get("estimated_tokens")),
                longValue(row.get("retrieval_latency_ms")),
                booleanValue(row.get("canonical_requested")),
                booleanValue(row.get("canonical_hit")),
                booleanValue(row.get("fallback_requested")),
                booleanValue(row.get("fallback_hit"))
        );
    }

    private void cloneProject(
            String sourceProjectCode,
            String targetProjectCode,
            MemoryAbExperimentManifest manifest
    ) {
        Map<String, Object> project = requireProject(sourceProjectCode);
        Long targetProjectId = insertProject(project, targetProjectCode,
                manifest.baselineToChapter());
        Long sourceProjectId = longValue(project.get("id"));

        Map<String, Object> bible = queryOne(
                "SELECT * FROM story_bible WHERE project_id = ?", sourceProjectId);
        if (bible == null) {
            throw new IllegalStateException("Story Bible 不存在，projectCode=" + sourceProjectCode);
        }
        insertBible(bible, targetProjectId);

        List<Map<String, Object>> characters = jdbcTemplate.queryForList(
                "SELECT * FROM story_character WHERE project_id = ? ORDER BY character_code, id",
                sourceProjectId);
        for (Map<String, Object> character : characters) {
            insertCharacter(character, targetProjectId);
        }

        Map<Long, Long> outlineIds = new HashMap<>();
        List<Map<String, Object>> outlines = jdbcTemplate.queryForList("""
                SELECT id, parent_id, node_code, node_kind, sequence_no,
                       start_chapter, end_chapter, title, summary, status
                FROM outline_node
                WHERE project_id = ?
                ORDER BY CASE node_kind WHEN 'BOOK' THEN 0 WHEN 'VOLUME' THEN 1 ELSE 2 END,
                         sequence_no, id
                """, sourceProjectId);
        for (Map<String, Object> outline : outlines) {
            Long sourceId = longValue(outline.get("id"));
            Long sourceParentId = nullableLong(outline.get("parent_id"));
            Long targetParentId = sourceParentId == null
                    ? null : outlineIds.get(sourceParentId);
            if (sourceParentId != null && targetParentId == null) {
                throw new IllegalStateException("大纲父节点复制顺序异常");
            }
            jdbcTemplate.update("""
                    INSERT INTO outline_node
                        (project_id, parent_id, node_code, node_kind, sequence_no,
                         start_chapter, end_chapter, title, summary, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    targetProjectId, targetParentId, text(outline.get("node_code")),
                    text(outline.get("node_kind")), intValue(outline.get("sequence_no")),
                    nullableInt(outline.get("start_chapter")),
                    nullableInt(outline.get("end_chapter")), text(outline.get("title")),
                    text(outline.get("summary")), text(outline.get("status")));
            Long targetId = jdbcTemplate.queryForObject(
                    "SELECT id FROM outline_node WHERE project_id = ? AND node_code = ?",
                    Long.class, targetProjectId, text(outline.get("node_code")));
            outlineIds.put(sourceId, targetId);
        }

        List<Map<String, Object>> plans = jdbcTemplate.queryForList("""
                SELECT p.chapter_number, p.title, p.summary, p.status, n.node_code
                FROM chapter_plan p
                JOIN outline_node n ON n.project_id = p.project_id AND n.id = p.outline_node_id
                WHERE p.project_id = ?
                ORDER BY p.chapter_number
                """, sourceProjectId);
        for (Map<String, Object> plan : plans) {
            Long targetOutlineId = jdbcTemplate.queryForObject(
                    "SELECT id FROM outline_node WHERE project_id = ? AND node_code = ?",
                    Long.class, targetProjectId, text(plan.get("node_code")));
            jdbcTemplate.update("""
                    INSERT INTO chapter_plan
                        (project_id, outline_node_id, chapter_number, title, summary, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    targetProjectId, targetOutlineId, intValue(plan.get("chapter_number")),
                    text(plan.get("title")), text(plan.get("summary")),
                    text(plan.get("status")));
        }

        List<Map<String, Object>> chapters = jdbcTemplate.queryForList("""
                SELECT c.chapter_number, c.title, c.content, c.word_count, c.status
                FROM story_chapter c
                WHERE c.project_id = ? AND c.chapter_number BETWEEN ? AND ?
                ORDER BY c.chapter_number
                """, sourceProjectId, manifest.baselineFromChapter(), manifest.baselineToChapter());
        for (Map<String, Object> chapter : chapters) {
            Long targetPlanId = jdbcTemplate.queryForObject(
                    "SELECT id FROM chapter_plan WHERE project_id = ? AND chapter_number = ?",
                    Long.class, targetProjectId, intValue(chapter.get("chapter_number")));
            jdbcTemplate.update("""
                    INSERT INTO story_chapter
                        (project_id, chapter_plan_id, chapter_number, title, content, word_count, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    targetProjectId, targetPlanId, intValue(chapter.get("chapter_number")),
                    text(chapter.get("title")), text(chapter.get("content")),
                    intValue(chapter.get("word_count")), text(chapter.get("status")));
        }

        List<Map<String, Object>> summaries = jdbcTemplate.queryForList("""
                SELECT chapter_number, short_summary, key_events_json,
                       unresolved_questions_json, ending_hook, story_state_snapshot_json, status
                FROM story_summary
                WHERE project_id = ? AND chapter_number BETWEEN ? AND ?
                ORDER BY chapter_number
                """, sourceProjectId, manifest.baselineFromChapter(), manifest.baselineToChapter());
        for (Map<String, Object> summary : summaries) {
            Long targetChapterId = jdbcTemplate.queryForObject(
                    "SELECT id FROM story_chapter WHERE project_id = ? AND chapter_number = ?",
                    Long.class, targetProjectId, intValue(summary.get("chapter_number")));
            jdbcTemplate.update("""
                    INSERT INTO story_summary
                        (project_id, chapter_id, chapter_number, short_summary, key_events_json,
                         unresolved_questions_json, ending_hook, story_state_snapshot_json, status)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, CAST(? AS JSON), ?)
                    """,
                    targetProjectId, targetChapterId, intValue(summary.get("chapter_number")),
                    text(summary.get("short_summary")), jsonOrNull(summary.get("key_events_json")),
                    jsonOrNull(summary.get("unresolved_questions_json")),
                    text(summary.get("ending_hook")),
                    jsonOrNull(summary.get("story_state_snapshot_json")),
                    text(summary.get("status")));
        }
    }

    private void cloneCanonicalMemory(
            String sourceProjectCode,
            String targetProjectCode,
            MemoryAbExperimentManifest manifest
    ) {
        String scope = manifest.experimentId() + ":" + targetProjectCode;
        List<Map<String, Object>> commits = jdbcTemplate.queryForList("""
                SELECT commit_key, chapter_number, chapter_version, content_hash, final_content
                FROM memory_commit
                WHERE project_code = ? AND chapter_number BETWEEN ? AND ?
                ORDER BY chapter_number, commit_key
                """, sourceProjectCode, manifest.baselineFromChapter(), manifest.baselineToChapter());
        Map<String, String> commitIds = new HashMap<>();
        for (Map<String, Object> commit : commits) {
            String oldKey = text(commit.get("commit_key"));
            String newKey = cloneId("commit", scope, oldKey, 255);
            commitIds.put(oldKey, newKey);
            jdbcTemplate.update("""
                    INSERT INTO memory_commit
                        (commit_key, project_code, chapter_number, chapter_version,
                         content_hash, final_content)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    newKey, targetProjectCode, intValue(commit.get("chapter_number")),
                    rewrite(text(commit.get("chapter_version")), sourceProjectCode, targetProjectCode),
                    text(commit.get("content_hash")), text(commit.get("final_content")));
        }

        for (Map<String, Object> accepted : jdbcTemplate.queryForList("""
                SELECT commit_key, chapter_number, chapter_version, content_hash, final_content
                FROM memory_accepted_chapter_version
                WHERE project_code = ? AND chapter_number BETWEEN ? AND ?
                ORDER BY chapter_number, id
                """, sourceProjectCode, manifest.baselineFromChapter(), manifest.baselineToChapter())) {
            String oldCommitKey = text(accepted.get("commit_key"));
            String newCommitKey = requireMapped(commitIds, oldCommitKey, "accepted commit");
            jdbcTemplate.update("""
                    INSERT INTO memory_accepted_chapter_version
                        (commit_key, project_code, chapter_number, chapter_version,
                         content_hash, final_content)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    newCommitKey, targetProjectCode, intValue(accepted.get("chapter_number")),
                    rewrite(text(accepted.get("chapter_version")), sourceProjectCode, targetProjectCode),
                    text(accepted.get("content_hash")), text(accepted.get("final_content")));
        }

        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT event_id, chapter_number, description, chapter_version, content_hash,
                       evidence_start_offset, evidence_end_offset, evidence_excerpt,
                       story_time, candidate_id
                FROM memory_canonical_event
                WHERE project_code = ? AND chapter_number BETWEEN ? AND ?
                ORDER BY chapter_number, event_id
                """, sourceProjectCode, manifest.baselineFromChapter(), manifest.baselineToChapter());
        Map<String, String> eventIds = new HashMap<>();
        for (Map<String, Object> event : events) {
            String oldId = text(event.get("event_id"));
            String newId = cloneId("event", scope, oldId, 128);
            eventIds.put(oldId, newId);
        }

        List<Map<String, Object>> facts = jdbcTemplate.queryForList("""
                SELECT fact_id, proposition, source_ids_json, status, candidate_id
                FROM memory_canonical_fact
                WHERE project_code = ?
                ORDER BY fact_id
                """, sourceProjectCode).stream()
                .filter(fact -> sourceIdsWithinChapter(
                        text(fact.get("source_ids_json")),
                        sourceProjectCode,
                        manifest.baselineToChapter()))
                .toList();
        Map<String, String> factIds = new HashMap<>();
        for (Map<String, Object> fact : facts) {
            String oldId = text(fact.get("fact_id"));
            factIds.put(oldId, cloneId("fact", scope, oldId, 128));
        }

        List<Map<String, Object>> projections = jdbcTemplate.queryForList("""
                SELECT projection_id, content, source_ids_json, status
                FROM memory_canonical_projection
                WHERE project_code = ?
                ORDER BY projection_id
                """, sourceProjectCode).stream()
                .filter(projection -> sourceIdsWithinChapter(
                        text(projection.get("source_ids_json")),
                        sourceProjectCode,
                        manifest.baselineToChapter()))
                .toList();
        Map<String, String> projectionIds = new HashMap<>();
        for (Map<String, Object> projection : projections) {
            String oldId = text(projection.get("projection_id"));
            projectionIds.put(oldId, cloneId("projection", scope, oldId, 128));
        }

        Map<String, String> candidateIds = new HashMap<>();
        for (Map<String, Object> event : events) {
            candidateIds.put(text(event.get("candidate_id")),
                    cloneId("candidate", scope, text(event.get("candidate_id")), 128));
        }
        for (Map<String, Object> fact : facts) {
            candidateIds.put(text(fact.get("candidate_id")),
                    cloneId("candidate", scope, text(fact.get("candidate_id")), 128));
        }

        for (Map<String, Object> event : events) {
            jdbcTemplate.update("""
                    INSERT INTO memory_canonical_event
                        (event_id, project_code, chapter_number, description, chapter_version,
                         content_hash, evidence_start_offset, evidence_end_offset,
                         evidence_excerpt, story_time, candidate_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    eventIds.get(text(event.get("event_id"))), targetProjectCode,
                    intValue(event.get("chapter_number")), text(event.get("description")),
                    rewrite(text(event.get("chapter_version")), sourceProjectCode, targetProjectCode),
                    text(event.get("content_hash")), intValue(event.get("evidence_start_offset")),
                    intValue(event.get("evidence_end_offset")), text(event.get("evidence_excerpt")),
                    text(event.get("story_time")),
                    candidateIds.get(text(event.get("candidate_id"))));
        }

        for (Map<String, Object> fact : facts) {
            jdbcTemplate.update("""
                    INSERT INTO memory_canonical_fact
                        (fact_id, project_code, proposition, source_ids_json, status, candidate_id)
                    VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                    """,
                    factIds.get(text(fact.get("fact_id"))), targetProjectCode,
                    text(fact.get("proposition")),
                    rewriteSourceIds(text(fact.get("source_ids_json")), sourceProjectCode,
                            targetProjectCode, commitIds, eventIds, factIds, projectionIds, candidateIds),
                    text(fact.get("status")), candidateIds.get(text(fact.get("candidate_id"))));
        }

        for (Map<String, Object> projection : projections) {
            jdbcTemplate.update("""
                    INSERT INTO memory_canonical_projection
                        (projection_id, project_code, content, source_ids_json, status)
                    VALUES (?, ?, ?, CAST(? AS JSON), ?)
                    """,
                    projectionIds.get(text(projection.get("projection_id"))), targetProjectCode,
                    text(projection.get("content")),
                    rewriteSourceIds(text(projection.get("source_ids_json")), sourceProjectCode,
                            targetProjectCode, commitIds, eventIds, factIds, projectionIds, candidateIds),
                    text(projection.get("status")));
        }

        List<Map<String, Object>> outbox = jdbcTemplate.queryForList("""
                SELECT o.outbox_id, o.commit_key, o.aggregate_type, o.aggregate_id,
                       o.payload, o.status
                FROM memory_outbox o
                JOIN memory_commit c ON c.commit_key = o.commit_key
                WHERE c.project_code = ? AND c.chapter_number BETWEEN ? AND ?
                ORDER BY o.outbox_id
                """, sourceProjectCode, manifest.baselineFromChapter(), manifest.baselineToChapter());
        for (Map<String, Object> entry : outbox) {
            String oldAggregateType = text(entry.get("aggregate_type"));
            String oldAggregateId = text(entry.get("aggregate_id"));
            String newAggregateId = switch (oldAggregateType.toUpperCase(Locale.ROOT)) {
                case "EVENT" -> eventIds.getOrDefault(oldAggregateId,
                        cloneId("event", scope, oldAggregateId, 128));
                case "FACT" -> factIds.getOrDefault(oldAggregateId,
                        cloneId("fact", scope, oldAggregateId, 128));
                case "PROJECTION" -> projectionIds.getOrDefault(oldAggregateId,
                        cloneId("projection", scope, oldAggregateId, 128));
                default -> throw new IllegalStateException(
                        "不支持的 Canonical outbox 类型=" + oldAggregateType);
            };
            String newCommitKey = requireMapped(
                    commitIds, text(entry.get("commit_key")), "outbox commit");
            String payload = rewriteSourceIds(
                    text(entry.get("payload")), sourceProjectCode, targetProjectCode,
                    commitIds, eventIds, factIds, projectionIds, candidateIds);
            jdbcTemplate.update("""
                    INSERT INTO memory_outbox
                        (outbox_id, commit_key, aggregate_type, aggregate_id, payload, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    cloneId("outbox", scope, text(entry.get("outbox_id")), 64),
                    newCommitKey, oldAggregateType, newAggregateId, payload,
                    text(entry.get("status")));
        }
    }

    private String rewriteSourceIds(
            String json,
            String sourceProjectCode,
            String targetProjectCode,
            Map<String, String> commitIds,
            Map<String, String> eventIds,
            Map<String, String> factIds,
            Map<String, String> projectionIds,
            Map<String, String> candidateIds
    ) {
        String value = rewrite(json, sourceProjectCode, targetProjectCode);
        List<Map<String, String>> maps = List.of(
                commitIds, eventIds, factIds, projectionIds, candidateIds);
        for (Map<String, String> mappings : maps) {
            List<Map.Entry<String, String>> entries = mappings.entrySet().stream()
                    .sorted(Map.Entry.<String, String>comparingByKey(
                            Comparator.nullsFirst(Comparator.comparingInt(String::length))).reversed())
                    .toList();
            for (Map.Entry<String, String> entry : entries) {
                if (entry.getKey() != null && !entry.getKey().isBlank()) {
                    value = value.replace(entry.getKey(), entry.getValue());
                }
            }
        }
        return value == null || value.isBlank() ? "{}" : value;
    }

    private boolean sourceIdsWithinChapter(
            String sourceIdsJson,
            String sourceProjectCode,
            int maxChapter
    ) {
        if (sourceIdsJson == null || sourceIdsJson.isBlank()) {
            return false;
        }
        // 兼容历史数据中只保存 candidate/event 短 ID、没有项目/章节编码的记录。
        // 这类记录无法按章节裁剪，只能沿用旧复制行为。
        if (!sourceIdsJson.contains(sourceProjectCode)) {
            return true;
        }
        Matcher matcher = Pattern.compile(
                Pattern.quote(sourceProjectCode) + ":(\\d+)(?=[:#])"
        ).matcher(sourceIdsJson);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (Integer.parseInt(matcher.group(1)) > maxChapter) {
                return false;
            }
        }
        return found;
    }

    private MemoryAbIsolationReport isolationReport(MemoryAbExperimentManifest manifest) {
        return new MemoryAbIsolationReport(
                manifest.experimentId(),
                List.of(
                        environmentReport(LEGACY_GROUP, manifest.legacyProjectCode(), MemoryMode.LEGACY),
                        environmentReport(V1_GROUP, manifest.v1ProjectCode(), MemoryMode.V1)
                )
        );
    }

    private V1ComparisonIsolationReport v1ComparisonIsolationReport(
            V1ComparisonManifest manifest
    ) {
        return new V1ComparisonIsolationReport(
                manifest.experimentId(),
                List.of(
                        v1EnvironmentReport(
                                V1_CURRENT_GROUP, manifest.currentProjectCode()),
                        v1EnvironmentReport(
                                V1_IMPROVED_GROUP, manifest.improvedProjectCode())
                )
        );
    }

    private V1ComparisonIsolationReport.Environment v1EnvironmentReport(
            String variant,
            String projectCode
    ) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        return new V1ComparisonIsolationReport.Environment(
                variant,
                projectCode,
                MemoryMode.V1,
                count("SELECT COUNT(*) FROM story_chapter WHERE project_id = ? "
                        + "AND status = 'FINALIZED'", projectId),
                count("SELECT COUNT(*) FROM outline_node WHERE project_id = ?", projectId),
                count("SELECT COUNT(*) FROM chapter_plan WHERE project_id = ?", projectId),
                count("SELECT COUNT(*) FROM memory_commit WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_accepted_chapter_version WHERE project_code = ?",
                        projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_event WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_fact WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_projection WHERE project_code = ?",
                        projectCode),
                count("SELECT COUNT(*) FROM generation_metrics WHERE project_id = ?", projectId),
                count("SELECT COUNT(*) FROM memory_retrieval_metrics WHERE project_code = ?",
                        projectCode),
                count("SELECT COUNT(*) FROM chapter_model_trace WHERE project_code = ?", projectCode)
        );
    }

    private MemoryAbIsolationReport.Environment environmentReport(
            String group,
            String projectCode,
            MemoryMode mode
    ) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        return new MemoryAbIsolationReport.Environment(
                group,
                projectCode,
                mode,
                count("SELECT COUNT(*) FROM story_chapter WHERE project_id = ? AND status = 'FINALIZED'", projectId),
                count("SELECT COUNT(*) FROM outline_node WHERE project_id = ?", projectId),
                count("SELECT COUNT(*) FROM chapter_plan WHERE project_id = ?", projectId),
                count("SELECT COUNT(*) FROM memory_commit WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_accepted_chapter_version WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_event WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_fact WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM memory_canonical_projection WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM generation_metrics WHERE project_id = ?", projectId),
                count("SELECT COUNT(*) FROM memory_retrieval_metrics WHERE project_code = ?", projectCode),
                count("SELECT COUNT(*) FROM chapter_model_trace WHERE project_code = ?", projectCode)
        );
    }

    private void verifyEnvironmentReady(MemoryAbExperimentManifest manifest) {
        verifyProjectFingerprint(manifest.legacyProjectCode(), manifest);
        verifyProjectFingerprint(manifest.v1ProjectCode(), manifest);
        verifyOutlineFingerprint(manifest.legacyProjectCode(), manifest);
        verifyOutlineFingerprint(manifest.v1ProjectCode(), manifest);
        if (count("SELECT COUNT(*) FROM story_chapter WHERE project_id = "
                + "(SELECT id FROM novel_project WHERE project_code = ?) "
                + "AND chapter_number BETWEEN ? AND ?", manifest.legacyProjectCode(),
                manifest.experimentFromChapter(), manifest.experimentToChapter()) != 0L
                || count("SELECT COUNT(*) FROM story_chapter WHERE project_id = "
                + "(SELECT id FROM novel_project WHERE project_code = ?) "
                + "AND chapter_number BETWEEN ? AND ?", manifest.v1ProjectCode(),
                manifest.experimentFromChapter(), manifest.experimentToChapter()) != 0L) {
            throw new IllegalStateException("实验项目已经存在 11～16 章正文，不能重复运行");
        }
    }

    private void verifyBaselineStillFrozen(MemoryAbExperimentManifest manifest) {
        if (!manifest.configurationFingerprint().equals(configurationFingerprint())) {
            throw new IllegalStateException("实验配置指纹已变化，请重新冻结基线");
        }
        if (!manifest.baselineFingerprint().equals(projectFingerprint(
                manifest.baselineProjectCode(),
                manifest.baselineFromChapter(), manifest.baselineToChapter()))) {
            throw new IllegalStateException("Chapter 1～10 基线已变化，请重新冻结基线");
        }
        if (!manifest.frozenOutlineFingerprint().equals(outlineFingerprint(
                manifest.baselineProjectCode(),
                manifest.experimentFromChapter(), manifest.experimentToChapter()))) {
            throw new IllegalStateException("Chapter 11～16 冻结章纲已变化，请重新冻结基线");
        }
    }

    private void verifyProjectFingerprint(
            String projectCode,
            MemoryAbExperimentManifest manifest
    ) {
        if (!manifest.baselineFingerprint().equals(projectFingerprint(
                projectCode, manifest.baselineFromChapter(), manifest.baselineToChapter()))) {
            throw new IllegalStateException("实验项目基线不一致，projectCode=" + projectCode);
        }
    }

    private void verifyOutlineFingerprint(
            String projectCode,
            MemoryAbExperimentManifest manifest
    ) {
        if (!manifest.frozenOutlineFingerprint().equals(outlineFingerprint(
                projectCode, manifest.experimentFromChapter(), manifest.experimentToChapter()))) {
            throw new IllegalStateException("实验项目章纲不一致，projectCode=" + projectCode);
        }
    }

    private void verifyEnvironmentRows(MemoryAbExperimentManifest manifest) {
        MemoryAbIsolationReport.Environment legacy = environmentReport(
                LEGACY_GROUP, manifest.legacyProjectCode(), MemoryMode.LEGACY);
        MemoryAbIsolationReport.Environment v1 = environmentReport(
                V1_GROUP, manifest.v1ProjectCode(), MemoryMode.V1);
        if (legacy.generationMetricCount() != 0 || legacy.retrievalMetricCount() != 0
                || legacy.promptTraceCount() != 0 || v1.generationMetricCount() != 0
                || v1.retrievalMetricCount() != 0 || v1.promptTraceCount() != 0) {
            throw new IllegalStateException("实验指标或 Prompt Trace 未保持空白隔离");
        }
        if (legacy.canonicalCommitCount() != 0 || legacy.acceptedVersionCount() != 0
                || legacy.eventCount() != 0 || legacy.factCount() != 0
                || legacy.projectionCount() != 0) {
            throw new IllegalStateException("LEGACY 环境不应复制 Canonical Memory");
        }
        if (legacy.finalizedChapterCount() != v1.finalizedChapterCount()
                || legacy.outlineCount() != v1.outlineCount()
                || legacy.chapterPlanCount() != v1.chapterPlanCount()) {
            throw new IllegalStateException("A/B 业务基线行数不一致");
        }
    }

    private void verifyV1ComparisonEnvironmentRows(V1ComparisonManifest manifest) {
        V1ComparisonIsolationReport.Environment current = v1EnvironmentReport(
                V1_CURRENT_GROUP, manifest.currentProjectCode());
        V1ComparisonIsolationReport.Environment improved = v1EnvironmentReport(
                V1_IMPROVED_GROUP, manifest.improvedProjectCode());
        if (current.generationMetricCount() != 0 || current.retrievalMetricCount() != 0
                || current.promptTraceCount() != 0 || improved.generationMetricCount() != 0
                || improved.retrievalMetricCount() != 0 || improved.promptTraceCount() != 0) {
            throw new IllegalStateException("V1 对照环境指标或 Prompt Trace 未保持空白隔离");
        }
        if (current.finalizedChapterCount() != improved.finalizedChapterCount()
                || current.outlineCount() != improved.outlineCount()
                || current.chapterPlanCount() != improved.chapterPlanCount()
                || current.canonicalCommitCount() != improved.canonicalCommitCount()
                || current.acceptedVersionCount() != improved.acceptedVersionCount()
                || current.eventCount() != improved.eventCount()
                || current.factCount() != improved.factCount()
                || current.projectionCount() != improved.projectionCount()) {
            throw new IllegalStateException("V1-current/V1-improved 初始数据不一致");
        }
    }

    private void verifyV1ComparisonEnvironmentReady(V1ComparisonManifest manifest) {
        MemoryAbExperimentManifest compatibility = compatibilityManifest(manifest);
        verifyBaselineStillFrozen(compatibility);
        verifyProjectFingerprint(manifest.currentProjectCode(), compatibility);
        verifyProjectFingerprint(manifest.improvedProjectCode(), compatibility);
        verifyOutlineFingerprint(manifest.currentProjectCode(), compatibility);
        verifyOutlineFingerprint(manifest.improvedProjectCode(), compatibility);
        for (String projectCode : List.of(
                manifest.currentProjectCode(), manifest.improvedProjectCode())) {
            if (count("SELECT COUNT(*) FROM story_chapter WHERE project_id = "
                    + "(SELECT id FROM novel_project WHERE project_code = ?) "
                    + "AND chapter_number BETWEEN ? AND ?", projectCode,
                    manifest.experimentFromChapter(), manifest.experimentToChapter()) != 0L) {
                throw new IllegalStateException(
                        "V1 对照项目已经存在实验章节正文，不能重复运行：" + projectCode);
            }
        }
    }

    private MemoryAbExperimentManifest compatibilityManifest(V1ComparisonManifest manifest) {
        return new MemoryAbExperimentManifest(
                manifest.experimentId(),
                manifest.baselineProjectCode(),
                manifest.currentProjectCode(),
                manifest.improvedProjectCode(),
                manifest.baselineFromChapter(),
                manifest.baselineToChapter(),
                manifest.experimentFromChapter(),
                manifest.experimentToChapter(),
                manifest.baselineFingerprint(),
                manifest.frozenOutlineFingerprint(),
                manifest.configurationFingerprint(),
                manifest.waitingHumanPolicy(),
                manifest.automaticRevisionLimit(),
                manifest.humanRevisionLimit(),
                manifest.frozenAt()
        );
    }

    private String projectFingerprint(
            String projectCode,
            int baselineFromChapter,
            int baselineToChapter
    ) {
        Map<String, Object> project = requireProject(projectCode);
        Long projectId = longValue(project.get("id"));
        StringBuilder value = new StringBuilder(65536);
        appendRow(value, "project", project, List.of(
                "title", "genre", "target_chapter_count", "words_per_chapter",
                "current_chapter_number", "status"));
        appendRows(value, "bible", jdbcTemplate.queryForList("""
                SELECT one_sentence_premise, core_theme, main_conflict, ending_direction,
                       world_background, power_system_json, hard_rules_json, style_guide, status
                FROM story_bible WHERE project_id = ?
                """, projectId));
        appendRows(value, "character", jdbcTemplate.queryForList("""
                SELECT character_code, name, role_type, gender, age_description, appearance,
                       personality, background_story, note, current_state_json, life_status, status
                FROM story_character WHERE project_id = ? ORDER BY character_code
                """, projectId));
        appendRows(value, "outline", jdbcTemplate.queryForList("""
                SELECT n.node_code, p.node_code AS parent_code, n.node_kind, n.sequence_no,
                       n.start_chapter, n.end_chapter, n.title, n.summary, n.status
                FROM outline_node n
                LEFT JOIN outline_node p ON p.project_id = n.project_id AND p.id = n.parent_id
                WHERE n.project_id = ?
                ORDER BY n.node_code
                """, projectId));
        appendRows(value, "plan", jdbcTemplate.queryForList("""
                SELECT p.chapter_number, n.node_code, p.title, p.summary, p.status
                FROM chapter_plan p
                JOIN outline_node n ON n.project_id = p.project_id AND n.id = p.outline_node_id
                WHERE p.project_id = ? ORDER BY p.chapter_number
                """, projectId));
        appendRows(value, "chapter", jdbcTemplate.queryForList("""
                SELECT chapter_number, title, content, word_count, status
                FROM story_chapter
                WHERE project_id = ? AND chapter_number BETWEEN ? AND ?
                ORDER BY chapter_number
                """, projectId, baselineFromChapter, baselineToChapter));
        appendRows(value, "summary", jdbcTemplate.queryForList("""
                SELECT chapter_number, short_summary, key_events_json,
                       unresolved_questions_json, ending_hook, story_state_snapshot_json, status
                FROM story_summary
                WHERE project_id = ? AND chapter_number BETWEEN ? AND ?
                ORDER BY chapter_number
                """, projectId, baselineFromChapter, baselineToChapter));
        return sha256(value.toString());
    }

    private String outlineFingerprint(
            String projectCode,
            int experimentFromChapter,
            int experimentToChapter
    ) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        StringBuilder value = new StringBuilder(32768);
        appendRows(value, "outline", jdbcTemplate.queryForList("""
                SELECT n.node_code, p.node_code AS parent_code, n.node_kind, n.sequence_no,
                       n.start_chapter, n.end_chapter, n.title, n.summary, n.status
                FROM outline_node n
                LEFT JOIN outline_node p ON p.project_id = n.project_id AND p.id = n.parent_id
                WHERE n.project_id = ? ORDER BY n.node_code
                """, projectId));
        appendRows(value, "plan", jdbcTemplate.queryForList("""
                SELECT p.chapter_number, n.node_code, p.title, p.summary, p.status
                FROM chapter_plan p
                JOIN outline_node n ON n.project_id = p.project_id AND n.id = p.outline_node_id
                WHERE p.project_id = ? AND p.chapter_number BETWEEN ? AND ?
                ORDER BY p.chapter_number
                """, projectId, experimentFromChapter, experimentToChapter));
        return sha256(value.toString());
    }

    private void requireFinalizedBaseline(
            String projectCode,
            int fromChapter,
            int toChapter
    ) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        long total = count("SELECT COUNT(*) FROM story_chapter WHERE project_id = ? "
                + "AND chapter_number BETWEEN ? AND ?", projectId, fromChapter, toChapter);
        long finalized = count("SELECT COUNT(*) FROM story_chapter WHERE project_id = ? "
                + "AND chapter_number BETWEEN ? AND ? AND status = 'FINALIZED'",
                projectId, fromChapter, toChapter);
        if (total != toChapter - fromChapter + 1 || finalized != total) {
            throw new IllegalStateException(
                    "基线正文必须全部 FINALIZED，范围=" + fromChapter + "～" + toChapter);
        }
    }

    private void requireConfirmedBible(String projectCode) {
        Map<String, Object> row = queryOne("""
                SELECT status FROM story_bible
                WHERE project_id = (SELECT id FROM novel_project WHERE project_code = ?)
                """, projectCode);
        if (row == null || !"CONFIRMED".equalsIgnoreCase(text(row.get("status")))) {
            throw new IllegalStateException("实验基线 Story Bible 必须已确认");
        }
    }

    private void requireCharacters(String projectCode) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        if (count("SELECT COUNT(*) FROM story_character WHERE project_id = ?", projectId) == 0) {
            throw new IllegalStateException("实验基线必须存在已确认人物");
        }
    }

    private void requireFrozenExperimentOutline(
            String projectCode,
            int fromChapter,
            int toChapter
    ) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.chapter_number, p.status AS plan_status,
                       n.node_kind, n.start_chapter, n.end_chapter, n.status AS outline_status
                FROM chapter_plan p
                JOIN outline_node n ON n.project_id = p.project_id AND n.id = p.outline_node_id
                WHERE p.project_id = ? AND p.chapter_number BETWEEN ? AND ?
                ORDER BY p.chapter_number
                """, projectId, fromChapter, toChapter);
        if (rows.size() != toChapter - fromChapter + 1) {
            throw new IllegalStateException(
                    "第" + fromChapter + "～" + toChapter
                            + "章章纲未全部生成；请先通过既有流程生成并确认一份共同章纲");
        }
        for (Map<String, Object> row : rows) {
            if (!"ARC".equalsIgnoreCase(text(row.get("node_kind")))
                    || !Objects.equals(nullableInt(row.get("start_chapter")),
                    nullableInt(row.get("end_chapter")))
                    || !isConfirmedPlanStatus(text(row.get("plan_status")))
                    || !isConfirmedPlanStatus(text(row.get("outline_status")))) {
                throw new IllegalStateException(
                        "第" + intValue(row.get("chapter_number"))
                                + "章章纲尚未确认，不能进入 A/B 实验");
            }
        }
    }

    private void requireNoExperimentBodies(
            String projectCode,
            int fromChapter,
            int toChapter
    ) {
        Long projectId = longValue(requireProject(projectCode).get("id"));
        long count = count("SELECT COUNT(*) FROM story_chapter "
                + "WHERE project_id = ? AND chapter_number BETWEEN ? AND ?",
                projectId, fromChapter, toChapter);
        if (count != 0) {
            throw new IllegalStateException(
                    "实验基线已存在第" + fromChapter + "～" + toChapter
                            + "章正文，不能把已有结果混入 A/B 实验");
        }
    }

    private boolean isConfirmedPlanStatus(String status) {
        return "READY".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status);
    }

    private Long insertProject(
            Map<String, Object> source,
            String targetProjectCode,
            int currentChapterNumber
    ) {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count, words_per_chapter,
                     current_chapter_number, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                targetProjectCode, text(source.get("title")), text(source.get("genre")),
                nullableInt(source.get("target_chapter_count")),
                intValue(source.get("words_per_chapter")), currentChapterNumber,
                text(source.get("status")));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?",
                Long.class, targetProjectCode);
    }

    private void insertBible(Map<String, Object> bible, Long projectId) {
        jdbcTemplate.update("""
                INSERT INTO story_bible
                    (project_id, one_sentence_premise, core_theme, main_conflict,
                     ending_direction, world_background, power_system_json, hard_rules_json,
                     style_guide, status)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                """,
                projectId, text(bible.get("one_sentence_premise")), text(bible.get("core_theme")),
                text(bible.get("main_conflict")), text(bible.get("ending_direction")),
                text(bible.get("world_background")), jsonOrNull(bible.get("power_system_json")),
                jsonOrNull(bible.get("hard_rules_json")), text(bible.get("style_guide")),
                text(bible.get("status")));
    }

    private void insertCharacter(Map<String, Object> character, Long projectId) {
        jdbcTemplate.update("""
                INSERT INTO story_character
                    (project_id, character_code, name, role_type, gender, age_description,
                     appearance, personality, background_story, note, current_state_json,
                     life_status, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                """,
                projectId, text(character.get("character_code")), text(character.get("name")),
                text(character.get("role_type")), text(character.get("gender")),
                text(character.get("age_description")), text(character.get("appearance")),
                text(character.get("personality")), text(character.get("background_story")),
                text(character.get("note")), jsonOrNull(character.get("current_state_json")),
                text(character.get("life_status")), text(character.get("status")));
    }

    private Map<String, Object> requireProject(String projectCode) {
        Map<String, Object> project = queryOne(
                "SELECT * FROM novel_project WHERE project_code = ?", projectCode);
        if (project == null) {
            throw new IllegalStateException("项目不存在，projectCode=" + projectCode);
        }
        return project;
    }

    private void ensureProjectAbsent(String projectCode) {
        if (queryOne("SELECT id FROM novel_project WHERE project_code = ?", projectCode) != null) {
            throw new IllegalStateException(
                    "实验目标项目已存在，为避免覆盖数据请更换实验项目名：" + projectCode);
        }
    }

    private void appendPromptConstants(StringBuilder target, Class<?> type) {
        List<Field> fields = List.of(type.getFields()).stream()
                .filter(field -> Modifier.isStatic(field.getModifiers())
                        && field.getType() == String.class)
                .sorted(Comparator.comparing(Field::getName))
                .toList();
        for (Field field : fields) {
            try {
                append(target, type.getName() + "." + field.getName(), field.get(null));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("读取 Prompt 指纹失败", exception);
            }
        }
    }

    private void appendStageConfig(StringBuilder target, String name, ModelStageConfig config) {
        append(target, name + ".model", config == null ? null : config.model());
        append(target, name + ".temperature", config == null ? null : config.temperature());
        append(target, name + ".reasoning", config == null ? null : config.reasoning());
    }

    private void appendRows(
            StringBuilder target,
            String prefix,
            List<Map<String, Object>> rows
    ) {
        for (Map<String, Object> row : rows) {
            appendRow(target, prefix, row, row.keySet().stream().sorted().toList());
        }
    }

    private void appendRow(
            StringBuilder target,
            String prefix,
            Map<String, Object> row,
            List<String> columns
    ) {
        append(target, prefix, "row");
        for (String column : columns) {
            append(target, prefix + "." + column, row.get(column));
        }
    }

    private void append(StringBuilder target, String key, Object value) {
        target.append(key).append('=').append(value == null ? "<null>" : value)
                .append('\n');
    }

    private String cloneId(String kind, String scope, String sourceId, int maxLength) {
        String raw = kind + ":" + scope + ":" + sourceId;
        String prefix = "ab:" + kind + ":" + sha256(scope).substring(0, 12) + ":";
        String id = prefix + sha256(raw);
        return id.substring(0, Math.min(maxLength, id.length()));
    }

    private String requireMapped(Map<String, String> mappings, String key, String name) {
        String value = mappings.get(key);
        if (value == null) {
            throw new IllegalStateException("Canonical 复制缺少映射：" + name + "=" + key);
        }
        return value;
    }

    private String rewrite(String value, String source, String target) {
        return value == null ? null : value.replace(source, target);
    }

    private String jsonOrNull(Object value) {
        String text = text(value);
        return text == null || text.isBlank() ? null : text;
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : longValue(value);
    }

    private Integer nullableInt(Object value) {
        return value == null ? null : intValue(value);
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(text(value));
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "true".equalsIgnoreCase(text(value)) || "1".equals(text(value));
    }

    private void validateRange(int from, int to, String name) {
        if (from < 1 || from > to) {
            throw new IllegalArgumentException(name + " 章节范围无效");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private record RunOutcome(
            MemoryAbExperimentReport.ChapterResult result,
            boolean completed
    ) {
    }

    private record V1ComparisonRunOutcome(
            V1ComparisonReport.ChapterResult result,
            boolean completed
    ) {
    }
}
