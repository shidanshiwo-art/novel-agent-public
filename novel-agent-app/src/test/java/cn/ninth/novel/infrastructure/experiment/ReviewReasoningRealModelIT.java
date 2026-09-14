package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.service.agent.ReviseChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviewChapterNode;
import cn.ninth.novel.domain.chapter.service.data.IDataService;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.infrastructure.config.ChapterModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * REVIEW reasoning A/B 回放入口。
 *
 * <p>默认关闭。显式开启后，从同一已完成 V1 项目读取 Ch14/Ch16 的既有正文，
 * 将它作为 A/B 相同的 REVIEW 初始输入；A 沿用当前 REVIEW 配置，B 只关闭 REVIEW
 * thinking。DRAFT、COMPRESSION、Memory、Prompt 和 Revision 模型配置均不在本回放中改变。</p>
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ReviewReasoningRealModelIT {

    private static final String SOURCE_PROJECT = "novel-001-v1-improved-exp";
    private static final int REVISION_LIMIT = 3;
    private static final Pattern SPECULATIVE_PATTERN = Pattern.compile(
            "可能|或许|似乎|可以进一步|未说明|没有说明|未交代|没有交代|缺少过渡|突然|凭空"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemoryAbExperimentHarness experimentHarness;
    @Autowired
    private IDataService dataService;
    @Autowired
    private ReviewChapterNode reviewChapterNode;
    @Autowired
    private ReviseChapterNode reviseChapterNode;
    @Autowired
    private ChapterModelProperties chapterModelProperties;

    @Test
    void runReviewReasoningReplay() {
        if (!"true".equalsIgnoreCase(System.getProperty("runReviewReasoning"))) {
            System.out.println(
                    "Review reasoning real-model replay is disabled; "
                            + "use -DrunReviewReasoning=true explicitly.");
            return;
        }

        String experimentId = System.getProperty(
                "reviewReasoningExperimentId",
                "review-reasoning-" + UUID.randomUUID().toString().substring(0, 8)
        );
        List<Map<String, Object>> results = new ArrayList<>();
        for (int chapter : List.of(14, 16)) {
            int copyThrough = chapter - 1;
            String onProject = projectCode(experimentId, chapter, "on");
            String offProject = projectCode(experimentId, chapter, "off");
            experimentHarness.cloneProjectSnapshot(
                    SOURCE_PROJECT, onProject, copyThrough, experimentId + "-on");
            experimentHarness.cloneProjectSnapshot(
                    SOURCE_PROJECT, offProject, copyThrough, experimentId + "-off");

            String initialDraft = sourceDraft(chapter);
            results.add(runOne(
                    experimentId,
                    chapter,
                    "A",
                    onProject,
                    initialDraft,
                    ChapterGenerationVariant.REVIEW_REASONING_ON
            ));
            results.add(runOne(
                    experimentId,
                    chapter,
                    "B",
                    offProject,
                    initialDraft,
                    ChapterGenerationVariant.REVIEW_REASONING_OFF
            ));
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("experimentId", experimentId);
        output.put("sourceProject", SOURCE_PROJECT);
        output.put("memoryMode", "V1");
        output.put("configuredReviewReasoning", chapterModelProperties.reasoningForStage("REVIEW"));
        output.put("A", "current configured reasoning");
        output.put("B", "OFF via thinking.disabled and no reasoning_effort");
        output.put("sameDraftWithinChapter", true);
        output.put("samePreviousSnapshot", true);
        output.put("results", results);
        System.out.println("========== REVIEW REASONING A/B REPORT ==========");
        System.out.println(objectMapper.writeValueAsString(output));
        System.out.println("========== END REVIEW REASONING A/B REPORT ==========");
    }

    private Map<String, Object> runOne(
            String experimentId,
            int chapter,
            String group,
            String projectCode,
            String initialDraft,
            ChapterGenerationVariant variant
    ) {
        String workflowId = UUID.randomUUID().toString();
        Replay replay = new Replay();
        String draft = initialDraft;
        MemorySourceVersion sourceVersion = MemorySourceVersion.create(draft);
        List<MemoryCandidate> candidates = List.of();
        int retryCount = 0;
        String status = "PASS";
        Throwable failure = null;

        try {
            ChapterGraphState state = state(
                    projectCode,
                    chapter,
                    workflowId,
                    variant,
                    draft,
                    sourceVersion,
                    candidates,
                    retryCount,
                    null
            );
            for (int round = 0; ; round++) {
                Map<String, Object> reviewUpdate = reviewChapterNode.apply(state);
                replay.addReviewDelta(delta(reviewUpdate));
                retryCount = intValue(reviewUpdate.get(ChapterGraphKeys.RETRY_COUNT), retryCount);
                if (reviewUpdate.get(ChapterGraphKeys.WORKFLOW_STATUS) != null) {
                    status = String.valueOf(reviewUpdate.get(ChapterGraphKeys.WORKFLOW_STATUS));
                    break;
                }

                ReviewReportVO report = (ReviewReportVO) reviewUpdate.get(
                        ChapterGraphKeys.REVIEW_REPORT);
                replay.reports.add(report);
                if (report == null || !report.requiresRevision()) {
                    status = "PASS";
                    break;
                }
                if (replay.revisionRounds >= REVISION_LIMIT) {
                    status = "REVISION_LIMIT_REACHED";
                    break;
                }

                state = state(
                        projectCode,
                        chapter,
                        workflowId,
                        variant,
                        draft,
                        sourceVersion,
                        candidates,
                        retryCount,
                        report
                );
                Map<String, Object> reviseUpdate = reviseChapterNode.apply(state);
                replay.addRevisionDelta(delta(reviseUpdate));
                replay.revisionRounds++;
                draft = (String) reviseUpdate.get(ChapterGraphKeys.DRAFT);
                sourceVersion = (MemorySourceVersion) reviseUpdate.get(
                        ChapterGraphKeys.SOURCE_VERSION);
                @SuppressWarnings("unchecked")
                List<MemoryCandidate> updatedCandidates =
                        (List<MemoryCandidate>) reviseUpdate.get(ChapterGraphKeys.MEMORY_CANDIDATES);
                candidates = updatedCandidates == null ? List.of() : updatedCandidates;
                retryCount = intValue(reviseUpdate.get(ChapterGraphKeys.RETRY_COUNT), retryCount);
                state = state(
                        projectCode,
                        chapter,
                        workflowId,
                        variant,
                        draft,
                        sourceVersion,
                        candidates,
                        retryCount,
                        null
                );
            }
        } catch (RuntimeException exception) {
            status = "EXCEPTION";
            failure = exception;
        }

        return summarize(
                experimentId,
                chapter,
                group,
                projectCode,
                workflowId,
                status,
                replay,
                failure
        );
    }

    private ChapterGraphState state(
            String projectCode,
            int chapter,
            String workflowId,
            ChapterGenerationVariant variant,
            String draft,
            MemorySourceVersion sourceVersion,
            List<MemoryCandidate> candidates,
            int retryCount,
            ReviewReportVO report
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(ChapterGraphKeys.PROJECT_CODE, projectCode);
        values.put(ChapterGraphKeys.CHAPTER_NUMBER, chapter);
        values.put(ChapterGraphKeys.WORKFLOW_ID, workflowId);
        values.put(ChapterGraphKeys.MEMORY_MODE, MemoryMode.V1);
        values.put(ChapterGraphKeys.GENERATION_VARIANT, variant);
        values.put(
                ChapterGraphKeys.CONTEXT,
                dataService.loadContext(projectCode, chapter, MemoryMode.V1, workflowId)
        );
        values.put(ChapterGraphKeys.DRAFT, draft);
        values.put(ChapterGraphKeys.SOURCE_VERSION, sourceVersion);
        values.put(ChapterGraphKeys.MEMORY_CANDIDATES, candidates);
        values.put(ChapterGraphKeys.RETRY_COUNT, retryCount);
        if (report != null) {
            values.put(ChapterGraphKeys.REVIEW_REPORT, report);
        }
        return new ChapterGraphState(values);
    }

    private Map<String, Object> summarize(
            String experimentId,
            int chapter,
            String group,
            String projectCode,
            String workflowId,
            String status,
            Replay replay,
            Throwable failure
    ) {
        long successfulReviewCalls = countTrace(projectCode, chapter, workflowId, "REVIEW", true);
        long failedReviewCalls = countTrace(projectCode, chapter, workflowId, "REVIEW", false);
        long successfulRevisionCalls = countTrace(projectCode, chapter, workflowId, "REVISION", true);
        long failedRevisionCalls = countTrace(projectCode, chapter, workflowId, "REVISION", false);
        IssueStats issues = issueStats(replay.reports);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("experimentId", experimentId);
        result.put("chapter", chapter);
        result.put("group", group);
        result.put("projectCode", projectCode);
        result.put("workflowId", workflowId);
        result.put("reviewConfiguredMode", "A".equals(group) ? "CURRENT" : "OFF");
        result.put("status", status);
        result.put("successfulReviewCalls", successfulReviewCalls);
        result.put("failedReviewCalls", failedReviewCalls);
        result.put("reviewRetries", Math.max(0, failedReviewCalls));
        result.put("successfulRevisionCalls", successfulRevisionCalls);
        result.put("failedRevisionCalls", failedRevisionCalls);
        result.put("revisionRounds", replay.revisionRounds);
        result.put("revisionTriggers", replay.revisionTriggers());
        result.put("sameIssueRepeats", issues.sameIssueRepeats);
        result.put("newIssuesAfterRevision", issues.newIssuesAfterRevision);
        result.put("reviewIssueCount", issues.total);
        result.put("blockerCount", issues.blocker);
        result.put("majorCount", issues.major);
        result.put("minorCount", issues.minor);
        result.put("issueCategoryCounts", issues.categoryCounts);
        result.put("speculativeIssueCount", issues.speculative);
        result.put("speculativeIssueRatio", ratio(issues.speculative, issues.total));
        result.put("reviewInputTokens", replay.review.inputTokens);
        result.put("reviewOutputTokens", replay.review.outputTokens);
        result.put("reviewTotalTokens", replay.review.totalTokens);
        result.put("reviewInputTokensPerAttempt", average(replay.review.inputTokens,
                successfulReviewCalls + failedReviewCalls));
        result.put("reviewOutputTokensPerAttempt", average(replay.review.outputTokens,
                successfulReviewCalls + failedReviewCalls));
        result.put("reviewTotalTokensPerAttempt", average(replay.review.totalTokens,
                successfulReviewCalls + failedReviewCalls));
        result.put("chapterInputTokens", replay.totalInputTokens());
        result.put("chapterOutputTokens", replay.totalOutputTokens());
        result.put("chapterTotalTokens", replay.totalTokens());
        result.put("chapterTotalTokensPerModelAttempt", average(
                replay.totalTokens(),
                successfulReviewCalls + failedReviewCalls
                        + successfulRevisionCalls + failedRevisionCalls));
        result.put("hardGold", hardGold(chapter, replay.reports));
        result.put("failure", failure == null ? null : failure.toString());
        return result;
    }

    private IssueStats issueStats(List<ReviewReportVO> reports) {
        IssueStats stats = new IssueStats();
        Set<String> previous = Set.of();
        boolean previousReportExists = false;
        for (ReviewReportVO report : reports) {
            Set<String> current = new HashSet<>();
            if (report == null || report.getReviewIssueVOList() == null) {
                previous = current;
                previousReportExists = true;
                continue;
            }
            for (ReviewIssueVO issue : report.getReviewIssueVOList()) {
                if (issue == null) {
                    continue;
                }
                stats.total++;
                if (issue.getSeverity() != null) {
                    switch (issue.getSeverity()) {
                        case BLOCKER -> stats.blocker++;
                        case MAJOR -> stats.major++;
                        case MINOR -> stats.minor++;
                        default -> { }
                    }
                }
                String category = classify(issue);
                stats.categoryCounts.merge(category, 1, Integer::sum);
                if (isSpeculative(issue)) {
                    stats.speculative++;
                }
                String key = issueKey(issue);
                if (!current.add(key) || previous.contains(key)) {
                    stats.sameIssueRepeats++;
                }
                if (previousReportExists && !previous.contains(key)) {
                    stats.newIssuesAfterRevision++;
                }
            }
            previous = current;
            previousReportExists = true;
        }
        return stats;
    }

    private Map<String, Object> hardGold(int chapter, List<ReviewReportVO> reports) {
        List<Map<String, Object>> gold = new ArrayList<>();
        if (chapter == 14) {
            gold.add(goldItem(
                    "CH14_HAOLE_PRESENCE",
                    "郝乐上一章明确不在当前行动现场，当前章直接出现在宿舍",
                    true,
                    reports,
                    "郝乐",
                    "上一章",
                    "不在",
                    "宿舍"
            ));
        } else {
            for (String category : List.of(
                    "HARD_CONTINUITY",
                    "ABILITY_RULE",
                    "KNOWLEDGE_BOUNDARY",
                    "TIMELINE",
                    "ITEM_STATE",
                    "CHARACTER_PRESENCE")) {
                gold.add(goldItem(
                        "CH16_NEGATIVE_" + category,
                        "既有独立 Ch16 连续性审计未确认该类硬错误",
                        false,
                        reports,
                        category
                ));
            }
        }
        long tp = gold.stream().filter(item -> Boolean.TRUE.equals(item.get("tp"))).count();
        long fn = gold.stream().filter(item -> Boolean.TRUE.equals(item.get("fn"))).count();
        long hardFp = gold.stream().filter(item -> Boolean.TRUE.equals(item.get("hardFp"))).count();
        return Map.of("items", gold, "hardTP", tp, "hardFN", fn, "hardFP", hardFp);
    }

    private Map<String, Object> goldItem(
            String id,
            String definition,
            boolean positive,
            List<ReviewReportVO> reports,
            String... terms
    ) {
        boolean hit = reports.stream()
                .filter(report -> report != null && report.getReviewIssueVOList() != null)
                .flatMap(report -> report.getReviewIssueVOList().stream())
                .filter(java.util.Objects::nonNull)
                .anyMatch(issue -> {
                    String text = normalize(issue.getDescription()) + normalize(issue.getEvidence())
                            + normalize(issue.getCategory());
                    if (positive) {
                        return "HARD_CONTINUITY".equals(classify(issue))
                                && containsAll(text, terms);
                    }
                    return classify(issue).equals(terms[0]);
                });
        boolean tp = positive && hit;
        boolean fn = positive && !hit;
        boolean hardFp = !positive && hit;
        return Map.of(
                "id", id,
                "definition", definition,
                "positive", positive,
                "hit", hit,
                "tp", tp,
                "fn", fn,
                "hardFp", hardFp
        );
    }

    private String classify(ReviewIssueVO issue) {
        String type = normalize(issue.getIssueType());
        String category = normalize(issue.getCategory());
        String text = type + category + normalize(issue.getDescription())
                + normalize(issue.getEvidence());
        if ("DETERMINISTIC".equalsIgnoreCase(normalize(issue.getCheckerType()))) {
            if (type.contains("ABILITY") || type.contains("WORLD_RULE")) {
                return "ABILITY_RULE";
            }
            if (type.contains("KNOWLEDGE")) {
                return "KNOWLEDGE_BOUNDARY";
            }
            return "HARD_CONTINUITY";
        }
        if (containsAny(text, "重复", "复述", "过度解释", "Over-explanation")) {
            return "REPETITION";
        }
        if (containsAny(text, "文风", "用词", "句式", "节奏", "表达", "自然度")) {
            return "STYLE";
        }
        if (containsAny(text, "章纲", "偏离", "缺失", "未完成", "节拍")) {
            return "OUTLINE_MISS";
        }
        if (containsAny(text, "能力", "规则", "激活", "触发", "消耗", "代价", "药剂")) {
            return "ABILITY_RULE";
        }
        if (containsAny(text, "知道", "知识", "档案", "信息", "来源", "听说", "得知")) {
            return "KNOWLEDGE_BOUNDARY";
        }
        if (containsAny(text, "位置", "地点", "宿舍", "行政楼", "在场", "伤势", "伤口",
                "时间", "日线", "时间线", "持有", "携带", "物品")) {
            return "HARD_CONTINUITY";
        }
        if (containsAny(text, "因果", "动机", "逻辑", "解释")) {
            return "CAUSAL_LOGIC";
        }
        return "CAUSAL_LOGIC";
    }

    private boolean isSpeculative(ReviewIssueVO issue) {
        String text = normalize(issue.getDescription()) + normalize(issue.getEvidence());
        return normalize(issue.getEvidence()).isBlank() || SPECULATIVE_PATTERN.matcher(text).find();
    }

    private String issueKey(ReviewIssueVO issue) {
        return normalize(issue.getCategory()) + "|"
                + normalize(issue.getDescription()) + "|"
                + normalize(issue.getEvidence());
    }

    private boolean containsAllOrAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAll(String text, String... terms) {
        for (String term : terms) {
            if (!text.contains(normalize(term))) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAny(String text, String... terms) {
        return containsAllOrAny(text, terms);
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

    private String projectCode(String experimentId, int chapter, String group) {
        String code = "rr-" + compact(experimentId) + "-ch" + chapter + "-" + group;
        return code.length() <= 64 ? code : code.substring(0, 64);
    }

    private String compact(String value) {
        return value.replaceAll("[^A-Za-z0-9-]", "-");
    }

    private long countTrace(
            String projectCode,
            int chapter,
            String workflowId,
            String node,
            boolean success
    ) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM chapter_model_trace
                WHERE project_code = ? AND chapter_number = ? AND workflow_id = ?
                  AND node = ? AND success = ?
                """, Long.class, projectCode, chapter, workflowId, node, success);
        return value == null ? 0L : value;
    }

    private GenerationMetricsDelta delta(Map<String, Object> update) {
        Object value = update == null ? null : update.get(ChapterGraphKeys.GENERATION_METRICS_DELTA);
        return value instanceof GenerationMetricsDelta current
                ? current : GenerationMetricsDelta.empty();
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private double average(Long total, long count) {
        return total == null || count == 0 ? 0.0d : (double) total / count;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class Replay {
        private final List<ReviewReportVO> reports = new ArrayList<>();
        private final NodeMetrics review = new NodeMetrics();
        private final NodeMetrics revision = new NodeMetrics();
        private int revisionRounds;

        private void addReviewDelta(GenerationMetricsDelta delta) {
            review.add(delta);
        }

        private void addRevisionDelta(GenerationMetricsDelta delta) {
            revision.add(delta);
        }

        private List<String> revisionTriggers() {
            return reports.stream()
                    .filter(report -> report != null && report.getReviewIssueVOList() != null)
                    .flatMap(report -> report.getReviewIssueVOList().stream())
                    .filter(issue -> issue != null && issue.getSeverity() != null
                            && issue.getSeverity().getRank() >= 2)
                    .map(issue -> issue.getSeverity().getLabel() + ": "
                            + (issue.getCategory() == null ? "" : issue.getCategory()))
                    .toList();
        }

        private Long totalInputTokens() {
            return add(review.inputTokens, revision.inputTokens);
        }

        private Long totalOutputTokens() {
            return add(review.outputTokens, revision.outputTokens);
        }

        private Long totalTokens() {
            return add(review.totalTokens, revision.totalTokens);
        }

        private Long add(Long left, Long right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left + right;
        }
    }

    private static final class NodeMetrics {
        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;

        private void add(GenerationMetricsDelta delta) {
            inputTokens = add(inputTokens, delta.inputTokens());
            outputTokens = add(outputTokens, delta.outputTokens());
            totalTokens = add(totalTokens, delta.totalTokens());
        }

        private Long add(Long left, Long right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left + right;
        }
    }

    private static final class IssueStats {
        private int total;
        private int blocker;
        private int major;
        private int minor;
        private int speculative;
        private int sameIssueRepeats;
        private int newIssuesAfterRevision;
        private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    }
}
