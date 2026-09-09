package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendLineIfPresent;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendList;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.value;
import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVISER_SYSTEM_PROMPT;

/**
 * REVISE 章节改稿节点。
 *
 * @author ninth
 * @date 2026/8/24
 * @description
 */
@Slf4j
@Service
public class ReviseChapterNode implements NodeAction<ChapterGraphState> {
    private final IChapterModelPort chapterModelPort;
    private final ChapterModelRetryExecutor retryExecutor;
    private final PromptTraceRecorder promptTraceRecorder;

    public ReviseChapterNode(IChapterModelPort chapterModelPort) {
        this(
                chapterModelPort,
                new ChapterModelRetryExecutor(),
                new PromptTraceRecorder()
        );
    }

    public ReviseChapterNode(
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
    public ReviseChapterNode(
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
                        "REVISE 节点缺少 context"
                ));
        requireChapterOutline(context);
        String draft = state.draft()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "REVISE 节点缺少 draft"
                ));
        ReviewReportVO report = state.reviewReport()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "REVISE 节点缺少 reviewReport"
                ));
        String revisionInstruction = state.revisionInstruction().orElse(null);
        AtomicReference<GenerationMetricsDelta> metrics =
                new AtomicReference<>(GenerationMetricsDelta.empty());
        ChapterModelRetryExecutor.RetryResult<String> result = reviseWithRetry(
                context,
                draft,
                report,
                revisionInstruction,
                state.retryCount(),
                state,
                delta -> metrics.updateAndGet(current -> current.plus(delta))
        );
        Map<String, Object> update = new HashMap<>();
        update.put(ChapterGraphKeys.DRAFT, result.value());
        update.put(ChapterGraphKeys.CURRENT_NODE, "REVISE");
        update.put(ChapterGraphKeys.COMPLETED_STAGES, List.of("REVISE"));
        update.put(ChapterGraphKeys.RETRY_COUNT, state.retryCount() + result.retryCount());
        update.put(ChapterGraphKeys.GENERATION_METRICS_DELTA, metrics.get());
        if (revisionInstruction != null) {
            update.put(ChapterGraphKeys.REVISION_INSTRUCTION, "");
        }
        return Map.copyOf(update);
    }

    private String revise(ChapterContextAggregate context, String draft, ReviewReportVO report) {
        return revise(context, draft, report, null);
    }

    private String revise(
            ChapterContextAggregate context,
            String draft,
            ReviewReportVO report,
            String revisionInstruction
    ) {
        return reviseWithRetry(context, draft, report, revisionInstruction, 0).value();
    }

    private ChapterModelRetryExecutor.RetryResult<String> reviseWithRetry(
            ChapterContextAggregate context,
            String draft,
            ReviewReportVO report,
            String revisionInstruction,
            int usedRetryCount
    ) {
        return reviseWithRetry(
                context,
                draft,
                report,
                revisionInstruction,
                usedRetryCount,
                null
        );
    }

    private ChapterModelRetryExecutor.RetryResult<String> reviseWithRetry(
            ChapterContextAggregate context,
            String draft,
            ReviewReportVO report,
            String revisionInstruction,
            int usedRetryCount,
            ChapterGraphState state
    ) {
        return reviseWithRetry(
                context,
                draft,
                report,
                revisionInstruction,
                usedRetryCount,
                state,
                ignored -> { }
        );
    }

    private ChapterModelRetryExecutor.RetryResult<String> reviseWithRetry(
            ChapterContextAggregate context,
            String draft,
            ReviewReportVO report,
            String revisionInstruction,
            int usedRetryCount,
            ChapterGraphState state,
            Consumer<GenerationMetricsDelta> metricsConsumer
    ) {
        String systemPrompt = REVISER_SYSTEM_PROMPT;
        String userPrompt = buildRevisePrompt(
                context,
                draft,
                report,
                revisionInstruction
        );
        ChapterModelRetryExecutor.RetryResult<String> result = retryExecutor.execute(
                usedRetryCount,
                attempt -> {
                    PromptTraceRecord trace = state == null
                            ? null
                            : state.promptTrace(
                                    "REVISION",
                                    attempt,
                                    systemPrompt,
                                    userPrompt
                            );
                    long startedAt = System.nanoTime();
                    String responseText = null;
                    try {
                        ChapterModelResponse<String> response = chapterModelPort.callWithUsage(
                                systemPrompt,
                                userPrompt,
                                "REVISION",
                                attempt + 1
                        );
                        metricsConsumer.accept(GenerationMetricsDelta.tokens(
                                response == null ? null : response.usage()));
                        responseText = response == null ? null : response.value();
                        if (responseText == null || responseText.isBlank()) {
                            throw AppException.internal(
                                    ResponseCode.E0005.getCode(),
                                    ResponseCode.E0005.getMessage() + "：正文为空"
                            );
                        }
                        metricsConsumer.accept(GenerationMetricsDelta.forCall("REVISE", null));
                        promptTraceRecorder.recordSuccess(
                                trace,
                                responseText,
                                elapsedMillis(startedAt)
                        );
                        return responseText;
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
                exception -> false
        );
        String revisedDraft = result.value();
        if (revisedDraft == null || revisedDraft.isBlank()) {
            throw AppException.internal(
                    ResponseCode.E0005.getCode(),
                    ResponseCode.E0005.getMessage() + "：正文为空"
            );
        }

        log.info("章节改稿完成");
        return new ChapterModelRetryExecutor.RetryResult<>(revisedDraft.trim(), result.retryCount());
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    String buildRevisePrompt(
            ChapterContextAggregate context,
            String draft,
            ReviewReportVO report
    ) {
        return buildRevisePrompt(context, draft, report, null);
    }

    String buildRevisePrompt(
            ChapterContextAggregate context,
            String draft,
            ReviewReportVO report,
            String revisionInstruction
    ) {
        StringBuilder prompt = new StringBuilder(65536);
        appendChapterPlan(prompt, context);
        appendStoryConstraints(prompt, context);
        appendCharacters(prompt, context);
        appendHistory(prompt, context);
        appendDraft(prompt, draft);
        appendRevisionInstruction(prompt, revisionInstruction);
        appendReviewIssues(prompt, report);

        return prompt.toString();
    }

    private void appendRevisionInstruction(
            StringBuilder prompt,
            String revisionInstruction
    ) {
        if (revisionInstruction == null || revisionInstruction.isBlank()) {
            return;
        }
        prompt.append("## 人工修改指令\n")
                .append(revisionInstruction.trim())
                .append("\n\n");
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
                    "REVISE 上下文缺少当前 ARC"
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
                    "REVISE 节点缺少 chapterPlan"
            );
        }
    }

    private void appendStoryConstraints(StringBuilder prompt, ChapterContextAggregate context) {
        StoryBibleEntity storyBible = context.getStoryBible();

        prompt.append("## 故事设定与改稿约束\n");
        ChapterPromptFormatter.appendHardRules(prompt, "不可违反的硬规则", storyBible.getHardRulesJson());
        appendLineIfPresent(prompt, "文风指南", storyBible.getStyleGuide());
        prompt.append('\n');
    }

    private void appendCharacters(StringBuilder prompt, ChapterContextAggregate context) {
        prompt.append("## 相关人物静态设定\n");
        List<StoryCharacterEntity> characters = context.getCharacters();
        if (characters == null || characters.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }

        for (StoryCharacterEntity character : characters) {
            prompt.append("### ")
                    .append(value(character.getName()))
                    .append('\n');
            ChapterPromptFormatter.appendCharacterStaticSettings(prompt, character);
            prompt.append('\n');
        }
    }

    private void appendHistory(StringBuilder prompt, ChapterContextAggregate context) {
        prompt.append("## 近期剧情记忆\n");
        ChapterHistoryVO history = context.getHistory();
        if (history == null) {
            prompt.append("无，这是第一章。\n\n");
            return;
        }

        List<ChapterMemoryVO> memories = history.getRecentMemories();
        if (memories == null || memories.isEmpty()) {
            prompt.append("近期剧情记忆：无\n\n");
        } else {
            for (ChapterMemoryVO memory : memories) {
                prompt.append("### 第 ")
                        .append(value(memory.getChapterNumber()))
                        .append(" 章\n");
                appendLineIfPresent(prompt, "摘要", memory.getShortSummary());
                appendList(prompt, "关键事件", memory.getKeyEvents());
                appendList(prompt, "未解决问题", memory.getUnresolved());
                appendLineIfPresent(prompt, "结尾钩子", memory.getEndingHook());
                prompt.append('\n');
            }
        }

        prompt.append("## 当前有效状态\n");
        ChapterPromptFormatter.appendStoryStateSnapshot(
                prompt, history.getStoryStateSnapshot());
        appendPreviousChapter(prompt, history.getPreviousChapter());
    }

    private void appendPreviousChapter(StringBuilder prompt, PreviousChapterVO previousChapter) {
        prompt.append("### 直接上一章正文\n");
        if (previousChapter == null) {
            prompt.append("无，这是第一章。\n\n");
            return;
        }

        appendLineIfPresent(prompt, "章节号", previousChapter.getChapterNumber());
        appendLineIfPresent(prompt, "章节标题", previousChapter.getTitle());
        appendLineIfPresent(prompt, "上一章正文", previousChapter.getContent());
        prompt.append('\n');
    }

    private void appendDraft(StringBuilder prompt, String draft) {
        prompt.append("## 当前待修改正文\n")
                .append(value(draft))
                .append("\n\n");
    }

    private void appendReviewIssues(StringBuilder prompt, ReviewReportVO report) {
        prompt.append("## 需要修复的问题\n");
        List<ReviewIssueVO> issues = report == null ? null : report.getReviewIssueVOList();
        if (issues == null || issues.isEmpty()) {
            prompt.append("无\n");
            return;
        }

        for (int index = 0; index < issues.size(); index++) {
            ReviewIssueVO issue = issues.get(index);
            prompt.append("### 问题 ").append(index + 1).append('\n');
            if (issue == null) {
                prompt.append("无\n\n");
                continue;
            }
            appendLineIfPresent(prompt, "严重程度",
                    issue.getSeverity() == null ? null : issue.getSeverity().getLabel());
            appendLineIfPresent(prompt, "问题分类", issue.getCategory());
            appendLineIfPresent(prompt, "问题说明", issue.getDescription());
            appendLineIfPresent(prompt, "原文证据", issue.getEvidence());
            prompt.append('\n');
        }
    }
}
