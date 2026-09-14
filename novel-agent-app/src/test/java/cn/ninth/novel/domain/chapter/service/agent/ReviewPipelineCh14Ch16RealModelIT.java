package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterTokenUsage;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RegressionStatus;
import cn.ninth.novel.domain.chapter.service.data.IDataService;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewPipelineRouter;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Task 4.6 的真实模型验证入口。
 *
 * <p>默认关闭；显式传入 {@code -DrunTask46=true} 后，只读取已经 FINALIZED 的
 * Ch14/Ch16 正文并运行新的 Review Pipeline 节点，不调用 Draft/Compression，
 * 也不生成或改写 Chapter 11～16 的持久化内容。</p>
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ReviewPipelineCh14Ch16RealModelIT {

    private static final String SOURCE_PROJECT = "novel-001-v1-improved-exp";
    private static final String REVIEW_PROJECT_PREFIX = "task46-review";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IDataService dataService;
    @Autowired
    private IChapterModelPort chapterModelPort;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runNewReviewPipelineForChapter14And16Only() throws Exception {
        if (!"true".equalsIgnoreCase(System.getProperty("runTask46"))) {
            System.out.println(
                    "Task 4.6 real-model validation is disabled; "
                            + "use -DrunTask46=true explicitly.");
            return;
        }

        List<ChapterResult> results = new ArrayList<>();
        for (int chapter : List.of(14, 16)) {
            results.add(runOne(chapter));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("task", "4.6");
        report.put("sourceProject", SOURCE_PROJECT);
        report.put("chaptersRun", List.of(14, 16));
        report.put("chaptersRegenerated", List.of());
        report.put("oldBaseline", Map.of(
                "ch14", Map.of(
                        "hardTP", 0, "hardFN", 1, "hardFP", 0,
                        "qualityFindings", 14, "revisionRounds", 3,
                        "regressionCalls", 0, "modelCalls", 8,
                        "tokens", ">=136839"),
                "ch16", Map.of(
                        "hardTP", 0, "hardFN", 0, "hardFP", 2,
                        "qualityFindings", 17, "revisionRounds", 3,
                        "regressionCalls", 0, "modelCalls", 7,
                        "tokens", ">=89361")));
        report.put("newResults", results.stream().map(ChapterResult::asMap).toList());
        report.put("acceptance", acceptance(results));

        System.out.println("========== TASK 4.6 REAL MODEL REPORT ==========");
        System.out.println(objectMapper.writeValueAsString(report));
        System.out.println("========== END TASK 4.6 REAL MODEL REPORT ==========");
    }

    private ChapterResult runOne(int chapter) {
        String workflowId = REVIEW_PROJECT_PREFIX + "-ch" + chapter + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        CountingModelPort countingPort = new CountingModelPort(chapterModelPort);
        try {
            String originalDraft = sourceDraft(chapter);
            ChapterContextAggregate context = dataService.loadContext(
                    SOURCE_PROJECT, chapter, MemoryMode.V1, workflowId);
            ChapterGraphState state = state(Map.of(
                    ChapterGraphKeys.PROJECT_CODE, SOURCE_PROJECT,
                    ChapterGraphKeys.CHAPTER_NUMBER, chapter,
                    ChapterGraphKeys.WORKFLOW_ID, workflowId,
                    ChapterGraphKeys.CONTEXT, context,
                    ChapterGraphKeys.DRAFT, originalDraft,
                    ChapterGraphKeys.REVISION_ROUND, 0));

            ReviewPreparationNode preparation = new ReviewPreparationNode();
            ContinuityValidationNode continuity = new ContinuityValidationNode(
                    new ConflictCandidateGenerator(),
                    new ModelContinuitySemanticVerifier(countingPort));
            QualityReviewNode quality = new QualityReviewNode(
                    new ModelQualityReviewer(countingPort));
            RepairPlanNode repairPlan = new RepairPlanNode();
            TargetedRevisionNode revision = new TargetedRevisionNode(countingPort);
            ModelRegressionVerifier modelRegression = new ModelRegressionVerifier(countingPort);
            RegressionCheckNode regression = new RegressionCheckNode(modelRegression);
            FinalRegressionNode finalRegression = new FinalRegressionNode(modelRegression);

            state = with(state, preparation.apply(state));
            int phaseACandidateCount = new ConflictCandidateGenerator()
                    .generate(state.reviewContext().orElseThrow()).size();
            System.out.printf("Task 4.6 Chapter %d Phase A candidates=%d%n",
                    chapter, phaseACandidateCount);
            state = with(state, continuity.apply(state));
            state = with(state, quality.apply(state));
            state = with(state, repairPlan.apply(state));

            int initialRequired = state.repairPlan().orElse(RepairPlan.builder().build())
                    .requiredItems().size();
            List<RegressionCheckResult> regressionResults = new ArrayList<>();
            int revisionRounds = 0;
            int remainingAfterRevisionOne = initialRequired;
            int resolvedAfterRevisionOne = 0;
            int revisionTwoCount = 0;
            int humanCount = 0;

            if (initialRequired == 0) {
                state = with(state, Map.of(
                        ChapterGraphKeys.FINAL_DECISION, ReviewPipelineRouter.PASS));
            } else {
                while (true) {
                    int nextRound = revisionRounds + 1;
                    state = with(state, Map.of(ChapterGraphKeys.REVISION_ROUND, nextRound));
                    state = with(state, revision.apply(state));
                    revisionRounds++;
                    if (nextRound == 2) {
                        revisionTwoCount++;
                    }

                    state = with(state, regression.apply(state));
                    RegressionCheckResult check = state.regressionResult().orElseThrow();
                    regressionResults.add(check);
                    if (nextRound == 1) {
                        remainingAfterRevisionOne = state.remainingRepairPlan()
                                .orElse(RepairPlan.builder().build()).requiredItems().size();
                        resolvedAfterRevisionOne = Math.max(
                                0, initialRequired - remainingAfterRevisionOne);
                    }

                    if (check.isResolved() && !check.isNewHardRegression()) {
                        state = with(state, Map.of(
                                ChapterGraphKeys.FINAL_DECISION, ReviewPipelineRouter.PASS));
                        break;
                    }
                    if (nextRound < ChapterGraphState.MAX_REVISION_ROUND) {
                        continue;
                    }

                    state = with(state, finalRegression.apply(state));
                    RegressionCheckResult finalCheck = state.regressionResult().orElseThrow();
                    regressionResults.add(finalCheck);
                    String decision = finalCheck.isResolved()
                            && !finalCheck.isNewHardRegression()
                            ? ReviewPipelineRouter.PASS : ReviewPipelineRouter.HUMAN;
                    if (ReviewPipelineRouter.HUMAN.equals(decision)) {
                        humanCount++;
                    }
                    state = with(state, Map.of(ChapterGraphKeys.FINAL_DECISION, decision));
                    break;
                }
            }

            int newHardRegressions = (int) regressionResults.stream()
                    .filter(RegressionCheckResult::isNewHardRegression).count();
            int hardTp = chapter == 14 ? hardPositiveCount(state.continuityFindings()) : 0;
            int hardFp = chapter == 16 ? hardNegativeCount(state.continuityFindings()) : 0;
            int hardFn = chapter == 14 && hardTp == 0 ? 1 : 0;
            return new ChapterResult(
                    chapter,
                    "OK",
                    state.continuityFindings().size(),
                    state.qualityFindings().size(),
                    initialRequired,
                    resolvedAfterRevisionOne,
                    remainingAfterRevisionOne,
                    newHardRegressions,
                    revisionTwoCount,
                    humanCount,
                    revisionRounds,
                    countingPort.calls("CONTINUITY_VALIDATION"),
                    countingPort.calls("QUALITY_REVIEW"),
                    countingPort.calls("REVISION"),
                    countingPort.calls("REGRESSION_CHECK"),
                    countingPort.tokensByNode(),
                    countingPort.totalTokens(),
                    changedCharacterCount(originalDraft, state.draft().orElse("")),
                    changedParagraphCount(originalDraft, state.draft().orElse("")),
                    hardTp,
                    hardFp,
                    hardFn,
                    state.finalDecision().orElse(ReviewPipelineRouter.HUMAN));
        } catch (RuntimeException exception) {
            System.out.printf("Task 4.6 Chapter %d failed: %s%n", chapter, exception);
            return new ChapterResult(
                    chapter, "ERROR: " + exception.getClass().getSimpleName(),
                    0, 0, 0, 0, 0, 0, 0, 1, 0,
                    countingPort.calls("CONTINUITY_VALIDATION"),
                    countingPort.calls("QUALITY_REVIEW"),
                    countingPort.calls("REVISION"),
                    countingPort.calls("REGRESSION_CHECK"),
                    countingPort.tokensByNode(), countingPort.totalTokens(),
                    0, 0, 0, 0, chapter == 14 ? 1 : 0,
                    ReviewPipelineRouter.HUMAN);
        }
    }

    private ChapterGraphState with(ChapterGraphState state, Map<String, Object> update) {
        Map<String, Object> values = new LinkedHashMap<>(state.data());
        values.putAll(update);
        return state(values);
    }

    private ChapterGraphState state(Map<String, Object> values) {
        return new ChapterGraphState(values);
    }

    private String sourceDraft(int chapter) {
        String content = jdbcTemplate.queryForObject("""
                SELECT content
                FROM story_chapter
                WHERE project_id = (SELECT id FROM novel_project WHERE project_code = ?)
                  AND chapter_number = ? AND status = 'FINALIZED'
                """, String.class, SOURCE_PROJECT, chapter);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("源项目缺少 Chapter " + chapter + " 正文");
        }
        return content;
    }

    private int hardPositiveCount(List<cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding> findings) {
        return (int) findings.stream()
                .filter(finding -> finding != null
                        && finding.getSeverity() == cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity.HARD)
                .filter(finding -> "郝乐".equals(finding.getEntity()))
                .filter(finding -> containsAny(finding.getCurrentEvidence(), "宿舍")
                        && containsAny(finding.getHistoricalEvidence(), "没有", "未", "不在"))
                .count();
    }

    private int hardNegativeCount(List<cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding> findings) {
        return (int) findings.stream()
                .filter(finding -> finding != null
                        && finding.getSeverity() == cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity.HARD)
                .map(finding -> finding.getType() == null ? "UNKNOWN" : finding.getType())
                .distinct()
                .count();
    }

    private boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int changedCharacterCount(String before, String after) {
        String left = before == null ? "" : before;
        String right = after == null ? "" : after;
        int changed = Math.abs(left.length() - right.length());
        int common = Math.min(left.length(), right.length());
        for (int i = 0; i < common; i++) {
            if (left.charAt(i) != right.charAt(i)) {
                changed++;
            }
        }
        return changed;
    }

    private int changedParagraphCount(String before, String after) {
        String[] left = paragraphs(before);
        String[] right = paragraphs(after);
        int changed = Math.abs(left.length - right.length);
        int common = Math.min(left.length, right.length);
        for (int i = 0; i < common; i++) {
            if (!Objects.equals(left[i], right[i])) {
                changed++;
            }
        }
        return changed;
    }

    private String[] paragraphs(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        return text.trim().split("(?:\\r?\\n){2,}");
    }

    private String acceptance(List<ChapterResult> results) {
        if (results.stream().anyMatch(result -> !"OK".equals(result.status()))) {
            return "READY_WITH_FIXES";
        }
        ChapterResult ch14 = results.stream().filter(result -> result.chapter() == 14)
                .findFirst().orElseThrow();
        ChapterResult ch16 = results.stream().filter(result -> result.chapter() == 16)
                .findFirst().orElseThrow();
        if (ch14.hardTp() == 1 && ch14.hardFn() == 0 && ch14.revisionRounds() <= 2
                && ch16.hardFp() <= 1 && ch16.revisionRounds() <= 1) {
            return "NEW_REVIEW_PIPELINE_READY";
        }
        return "READY_WITH_FIXES";
    }

    private record ChapterResult(
            int chapter,
            String status,
            int continuityFindings,
            int qualityFindings,
            int initialRequiredFindings,
            int resolvedAfterRevisionOne,
            int remainingAfterRevisionOne,
            int newHardRegressions,
            int revisionTwoCount,
            int humanCount,
            int revisionRounds,
            int continuityValidatorCalls,
            int qualityReviewerCalls,
            int revisionCalls,
            int regressionCalls,
            Map<String, Long> tokensByNode,
            long totalTokens,
            int changedCharacterCount,
            int changedParagraphCount,
            int hardTp,
            int hardFp,
            int hardFn,
            String finalDecision
    ) {

        private Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("chapter", chapter);
            map.put("status", status);
            map.put("continuityFindings", continuityFindings);
            map.put("qualityFindings", qualityFindings);
            map.put("initialRequiredFindings", initialRequiredFindings);
            map.put("resolvedAfterRevision1", resolvedAfterRevisionOne);
            map.put("remainingAfterRevision1", remainingAfterRevisionOne);
            map.put("newHardRegressions", newHardRegressions);
            map.put("revision2Count", revisionTwoCount);
            map.put("humanCount", humanCount);
            map.put("revisionRounds", revisionRounds);
            map.put("modelCalls", Map.of(
                    "continuityValidator", continuityValidatorCalls,
                    "qualityReviewer", qualityReviewerCalls,
                    "revision", revisionCalls,
                    "regression", regressionCalls));
            map.put("tokensByNode", tokensByNode);
            map.put("totalTokens", totalTokens);
            map.put("changedCharacterCount", changedCharacterCount);
            map.put("changedParagraphCount", changedParagraphCount);
            map.put("hardTP", hardTp);
            map.put("hardFP", hardFp);
            map.put("hardFN", hardFn);
            map.put("finalDecision", finalDecision);
            return map;
        }
    }

    private static final class CountingModelPort implements IChapterModelPort {

        private final IChapterModelPort delegate;
        private final Map<String, Integer> calls = new LinkedHashMap<>();
        private final Map<String, Long> tokensByNode = new LinkedHashMap<>();

        private CountingModelPort(IChapterModelPort delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public Flux<String> stream(String systemPrompt, String userPrompt) {
            return delegate.stream(systemPrompt, userPrompt);
        }

        @Override
        public Flux<String> stream(
                String systemPrompt,
                String userPrompt,
                String stage,
                int attempt
        ) {
            recordCall(stage);
            return delegate.stream(systemPrompt, userPrompt, stage, attempt);
        }

        @Override
        public String call(String systemPrompt, String userPrompt) {
            return delegate.call(systemPrompt, userPrompt);
        }

        @Override
        public String call(
                String systemPrompt,
                String userPrompt,
                String stage,
                int attempt
        ) {
            recordCall(stage);
            return delegate.call(systemPrompt, userPrompt, stage, attempt);
        }

        @Override
        public ChapterModelResponse<String> callWithUsage(
                String systemPrompt,
                String userPrompt,
                String stage,
                int attempt
        ) {
            recordCall(stage);
            ChapterModelResponse<String> response = delegate.callWithUsage(
                    systemPrompt, userPrompt, stage, attempt);
            if (response != null) {
                recordTokens(stage, response.usage());
            }
            return response;
        }

        @Override
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            return delegate.call(systemPrompt, userPrompt, responseType);
        }

        @Override
        public <T> ChapterModelResponse<T> callWithRawResponse(
                String systemPrompt,
                String userPrompt,
                Class<T> responseType,
                String stage,
                int attempt
        ) {
            recordCall(stage);
            ChapterModelResponse<T> response = delegate.callWithRawResponse(
                    systemPrompt, userPrompt, responseType, stage, attempt);
            if (response != null) {
                recordTokens(stage, response.usage());
            }
            return response;
        }

        private void recordCall(String stage) {
            String key = stage == null || stage.isBlank() ? "UNKNOWN" : stage;
            calls.merge(key, 1, Integer::sum);
        }

        private void recordTokens(String stage, ChapterTokenUsage usage) {
            String key = stage == null || stage.isBlank() ? "UNKNOWN" : stage;
            if (usage != null && usage.totalTokens() != null) {
                tokensByNode.merge(key, usage.totalTokens(), Long::sum);
            }
        }

        private int calls(String stage) {
            return calls.getOrDefault(stage, 0);
        }

        private Map<String, Long> tokensByNode() {
            return Map.copyOf(tokensByNode);
        }

        private long totalTokens() {
            return tokensByNode.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
