package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendLineIfPresent;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendList;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.value;
import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVIEW_SYSTEM_PROMPT;

/**
 * REVIEW 章节审稿节点。
 *
 * @author ninth
 * @date 2026/8/24
 * @description
 */
@Slf4j
@Service
public class ReviewChapterNode implements NodeAction<ChapterGraphState> {

    private static final int MAX_REVIEW_CHARACTERS = 4;

    private final IChapterModelPort chapterModelPort;
    private final ChapterModelRetryExecutor retryExecutor;
    private final PromptTraceRecorder promptTraceRecorder;

    public ReviewChapterNode(IChapterModelPort chapterModelPort) {
        this(
                chapterModelPort,
                new ChapterModelRetryExecutor(),
                new PromptTraceRecorder()
        );
    }

    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                new PromptTraceRecorder()
        );
    }

    @Autowired
    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder
    ) {
        this.chapterModelPort = chapterModelPort;
        this.retryExecutor = retryExecutor;
        this.promptTraceRecorder = promptTraceRecorder;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        ChapterContextAggregate context = state.context()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "REVIEW 节点缺少 context"
                ));
        requireChapterOutline(context);
        String draft = state.draft()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "REVIEW 节点缺少 draft"
                ));
        AtomicReference<GenerationMetricsDelta> metrics =
                new AtomicReference<>(GenerationMetricsDelta.empty());
        try {
            ChapterModelRetryExecutor.RetryResult<ReviewReportVO> result = reviewWithRetry(
                    context,
                    draft,
                    state.retryCount(),
                    state,
                    delta -> metrics.updateAndGet(current -> current.plus(delta))
            );
            return Map.of(
                    ChapterGraphKeys.REVIEW_REPORT, result.value(),
                    ChapterGraphKeys.CURRENT_NODE, "REVIEW",
                    ChapterGraphKeys.COMPLETED_STAGES, List.of("REVIEW"),
                    ChapterGraphKeys.RETRY_COUNT, state.retryCount() + result.retryCount(),
                    ChapterGraphKeys.FAILURE_MESSAGE, "",
                    ChapterGraphKeys.GENERATION_METRICS_DELTA, metrics.get()
            );
        } catch (AppException exception) {
            if (!ResponseCode.E0004.getCode().equals(exception.getCode())) {
                throw exception;
            }
            log.warn(
                    "REVIEW 报告校验重试耗尽，保留当前正文并转人工恢复，detail={}",
                    exception.getInternalDetail()
            );
            return Map.of(
                    ChapterGraphKeys.CURRENT_NODE, "REVIEW",
                    ChapterGraphKeys.COMPLETED_STAGES, List.of("REVIEW"),
                    ChapterGraphKeys.RETRY_COUNT,
                    Math.max(state.retryCount(), ChapterModelRetryExecutor.MAX_RETRIES),
                    ChapterGraphKeys.WORKFLOW_STATUS,
                    ChapterWorkflowStatusEnum.REVIEW_FAILED.name(),
                    ChapterGraphKeys.FAILURE_MESSAGE,
                    readableFailureMessage(exception),
                    ChapterGraphKeys.GENERATION_METRICS_DELTA,
                    metrics.get()
            );
        }
    }

    private ReviewReportVO review(ChapterContextAggregate context, String draft) {
        return reviewWithRetry(context, draft, 0).value();
    }

    private ChapterModelRetryExecutor.RetryResult<ReviewReportVO> reviewWithRetry(
            ChapterContextAggregate context,
            String draft,
            int usedRetryCount
    ) {
        return reviewWithRetry(context, draft, usedRetryCount, null);
    }

    private ChapterModelRetryExecutor.RetryResult<ReviewReportVO> reviewWithRetry(
            ChapterContextAggregate context,
            String draft,
            int usedRetryCount,
            ChapterGraphState state
    ) {
        return reviewWithRetry(context, draft, usedRetryCount, state, ignored -> { });
    }

    private ChapterModelRetryExecutor.RetryResult<ReviewReportVO> reviewWithRetry(
            ChapterContextAggregate context,
            String draft,
            int usedRetryCount,
            ChapterGraphState state,
            Consumer<GenerationMetricsDelta> metricsConsumer
    ) {
        String systemPrompt = REVIEW_SYSTEM_PROMPT;
        String userPrompt = buildReviewPrompt(context, draft);

        ChapterModelRetryExecutor.RetryResult<ReviewReportVO> result = retryExecutor.execute(
                usedRetryCount,
                attempt -> {
                    PromptTraceRecord trace = state == null
                            ? null
                            : state.promptTrace(
                                    "REVIEW",
                                    attempt,
                                    systemPrompt,
                                    userPrompt
                            );
                    long startedAt = System.nanoTime();
                    String responseText = null;
                    try {
                        ChapterModelResponse<ReviewReportVO> response =
                                chapterModelPort.callWithRawResponse(
                                        systemPrompt,
                                        userPrompt,
                                        ReviewReportVO.class,
                                        "REVIEW",
                                        attempt + 1
                                );
                        metricsConsumer.accept(GenerationMetricsDelta.tokens(
                                response == null ? null : response.usage()));
                        responseText = promptTraceRecorder.responseText(
                                response == null ? null : response.rawText(),
                                response == null ? null : response.value()
                        );
                        ReviewReportVO review = response == null ? null : response.value();
                        if (review == null) {
                            throw AppException.user(
                                    ResponseCode.E0004.getCode(),
                                    ResponseCode.E0004.getMessage()
                            );
                        }
                        validateReviewReport(review, draft);
                        metricsConsumer.accept(GenerationMetricsDelta.forCall("REVIEW", null));
                        promptTraceRecorder.recordSuccess(
                                trace,
                                responseText,
                                elapsedMillis(startedAt)
                        );
                        return review;
                    } catch (RuntimeException exception) {
                        if (exception instanceof ChapterModelResponseException responseException) {
                            metricsConsumer.accept(GenerationMetricsDelta.tokens(
                                    responseException.usage()));
                        }
                        promptTraceRecorder.recordFailure(
                                trace,
                                responseText,
                                exception,
                                elapsedMillis(startedAt)
                        );
                        throw exception;
                    }
                },
                exception -> ResponseCode.E0004.getCode().equals(exception.getCode())
        );

        log.info("review 节点:{}", result.value());
        return result;
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    String buildReviewPrompt(ChapterContextAggregate context, String draft) {
        StringBuilder prompt = new StringBuilder(65536);
        appendReviewConstraints(prompt, context);
        appendChapterPlan(prompt, context);
        ChapterMemoryVO previousMemory = previousChapterMemory(context);
        appendCharacters(prompt, context, draft, previousMemory);
        appendHistory(prompt, context, previousMemory);
        appendDraft(prompt, draft);

        return prompt.toString();
    }

    private void appendReviewConstraints(StringBuilder prompt, ChapterContextAggregate context) {
        StoryBibleEntity storyBible = context.getStoryBible();

        prompt.append("## 故事设定与审稿约束\n");
        ChapterPromptFormatter.appendHardRules(prompt, "不可违反的硬规则", storyBible.getHardRulesJson());
        ChapterPromptFormatter.appendPowerSystem(prompt, "力量体系", storyBible.getPowerSystemJson());
        appendLineIfPresent(prompt, "世界背景", storyBible.getWorldBackground());
        appendLineIfPresent(prompt, "文风指南", storyBible.getStyleGuide());
        NovelProjectEntity project = context.getProject();
        if (project != null) {
            appendLineIfPresent(prompt, "目标字数", project.getWordsPerChapter());
        }
        prompt.append('\n');
    }

    private void validateReviewReport(ReviewReportVO review, String draft) {
        StructuredModelOutputValidator.validate(review);
        List<ReviewIssueVO> issues = review.getReviewIssueVOList();
        if (issues == null) {
            throw invalidReview("reviewIssueVOList 必须返回数组");
        }
        String normalizedDraft = normalizeReviewText(draft);
        for (int index = 0; index < issues.size(); index++) {
            ReviewIssueVO issue = issues.get(index);
            String fieldPrefix = "reviewIssueVOList[" + index + "]";
            if (issue == null) {
                throw invalidReview(fieldPrefix + " 不能为 null");
            }
            if (issue.getSeverity() == null) {
                throw invalidReview(fieldPrefix + ".severity 不能为空");
            }
            requireReviewText(issue.getCategory(), fieldPrefix + ".category");
            requireReviewText(issue.getDescription(), fieldPrefix + ".description");
            requireReviewText(issue.getEvidence(), fieldPrefix + ".evidence");
            if (!normalizedDraft.contains(normalizeReviewText(issue.getEvidence()))) {
                throw invalidReview(fieldPrefix + ".evidence 必须是当前正文的连续原文");
            }
        }
    }

    private String normalizeReviewText(String text) {
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void requireReviewText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidReview(fieldName + " 不能为空");
        }
    }

    private AppException invalidReview(String detail) {
        return AppException.internal(
                ResponseCode.E0004.getCode(),
                "SCHEMA_VALIDATION_FAILED: " + ResponseCode.E0004.getMessage() + "：" + detail
        );
    }

    private String readableFailureMessage(AppException exception) {
        return ResponseCode.messageFor(
                exception.getCode(),
                exception.getUserMessage()
        );
    }

    private void appendChapterPlan(
            StringBuilder prompt,
            ChapterContextAggregate context
    ) {
        prompt.append("## 当前章节章纲\n");
        ChapterPlanEntity chapterPlan = context.getChapterPlan();
        if (chapterPlan == null) {
            prompt.append("无\n\n");
            return;
        }
        if (context.getArc() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "REVIEW 上下文缺少当前 ARC"
            );
        }

        appendLineIfPresent(prompt, "章节标题", context.getArc().title());
        appendLineIfPresent(prompt, "章节摘要", chapterPlan.getSummary());
        prompt.append('\n');
    }

    private void requireChapterOutline(ChapterContextAggregate context) {
        if (context.getChapterPlan() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "REVIEW 节点缺少 chapterPlan"
            );
        }
    }

    private void appendCharacters(
            StringBuilder prompt,
            ChapterContextAggregate context,
            String draft,
            ChapterMemoryVO previousMemory
    ) {
        prompt.append("## 相关人物静态设定\n");
        List<StoryCharacterEntity> characters = context.getCharacters();
        if (characters == null || characters.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }

        String relevanceText = reviewRelevanceText(context, draft, previousMemory);
        List<StoryCharacterEntity> selectedCharacters = new ArrayList<>(MAX_REVIEW_CHARACTERS);
        for (StoryCharacterEntity character : characters) {
            if (character == null || !containsCharacterName(relevanceText, character.getName())) {
                continue;
            }
            selectedCharacters.add(character);
            if (selectedCharacters.size() == MAX_REVIEW_CHARACTERS) {
                break;
            }
        }
        if (selectedCharacters.isEmpty()) {
            for (StoryCharacterEntity character : characters) {
                if (character != null && isMainCharacter(character)) {
                    selectedCharacters.add(character);
                    break;
                }
            }
        }
        if (selectedCharacters.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }

        for (StoryCharacterEntity character : selectedCharacters) {
            prompt.append("### ")
                    .append(value(character.getName()))
                    .append('\n');
            ChapterPromptFormatter.appendCharacterStaticSettings(prompt, character);
            prompt.append('\n');
        }
    }

    private void appendHistory(
            StringBuilder prompt,
            ChapterContextAggregate context,
            ChapterMemoryVO previousMemory
    ) {
        prompt.append("## 上一章压缩记忆\n");
        if (context.getHistory() == null || previousMemory == null) {
            prompt.append("无，这是第一章。\n\n");
        } else {
            appendLineIfPresent(prompt, "摘要", previousMemory.getShortSummary());
            appendList(prompt, "关键事件", previousMemory.getKeyEvents());
            appendList(prompt, "未解决问题", previousMemory.getUnresolved());
            appendLineIfPresent(prompt, "结尾钩子", previousMemory.getEndingHook());
            prompt.append('\n');
        }
        prompt.append("## 当前有效状态\n");
        ChapterPromptFormatter.appendStoryStateSnapshot(
                prompt,
                context.getHistory() == null
                        ? null : context.getHistory().getStoryStateSnapshot()
        );
    }

    private ChapterMemoryVO previousChapterMemory(ChapterContextAggregate context) {
        ChapterHistoryVO history = context.getHistory();
        if (history == null || history.getRecentMemories() == null) {
            return null;
        }
        Integer previousChapterNumber = history.getPreviousChapter() == null
                ? null
                : history.getPreviousChapter().getChapterNumber();
        if (previousChapterNumber == null && context.getChapterPlan() != null
                && context.getChapterPlan().getChapterNumber() != null) {
            previousChapterNumber = context.getChapterPlan().getChapterNumber() - 1;
        }
        for (int index = history.getRecentMemories().size() - 1; index >= 0; index--) {
            ChapterMemoryVO memory = history.getRecentMemories().get(index);
            if (memory == null || memory.getShortSummary() == null) {
                continue;
            }
            if (previousChapterNumber == null
                    || previousChapterNumber.equals(memory.getChapterNumber())) {
                return memory;
            }
        }
        return null;
    }

    private boolean containsCharacterName(String text, String name) {
        return name != null && !name.isBlank() && containsNormalized(text, name);
    }

    private boolean containsNormalized(String text, String candidate) {
        return candidate != null && !candidate.isBlank()
                && text.contains(candidate.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isMainCharacter(StoryCharacterEntity character) {
        return character.getRoleType() != null
                && (character.getRoleType().contains("主角")
                || character.getRoleType().equalsIgnoreCase("PROTAGONIST"));
    }

    private String reviewRelevanceText(
            ChapterContextAggregate context,
            String draft,
            ChapterMemoryVO previousMemory
    ) {
        StringBuilder text = new StringBuilder();
        ChapterPlanEntity plan = context.getChapterPlan();
        if (plan != null) {
            appendRelevancePart(
                    text,
                    context.getArc() == null ? null : context.getArc().title()
            );
            appendRelevancePart(text, plan.getSummary());
        }
        appendRelevancePart(text, draft);
        if (previousMemory != null) {
            appendRelevancePart(text, previousMemory.getShortSummary());
            appendRelevanceParts(text, previousMemory.getKeyEvents());
            appendRelevanceParts(text, previousMemory.getUnresolved());
            appendRelevancePart(text, previousMemory.getEndingHook());
        }
        StoryBibleEntity storyBible = context.getStoryBible();
        if (storyBible != null) {
            appendRelevancePart(text, storyBible.getWorldBackground());
            appendRelevancePart(text, storyBible.getHardRulesJson());
            appendRelevancePart(text, storyBible.getPowerSystemJson());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private void appendRelevancePart(StringBuilder text, String part) {
        if (part != null && !part.isBlank()) {
            text.append(part).append('\n');
        }
    }

    private void appendRelevanceParts(StringBuilder text, List<String> parts) {
        if (parts == null) {
            return;
        }
        for (String part : parts) {
            appendRelevancePart(text, part);
        }
    }

    private void appendDraft(StringBuilder prompt, String draft) {
        prompt.append("## 当前待审稿正文\n")
                .append(value(draft))
                .append('\n');
    }

}
