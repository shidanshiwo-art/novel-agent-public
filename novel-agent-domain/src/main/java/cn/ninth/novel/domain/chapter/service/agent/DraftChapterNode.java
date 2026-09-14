package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterTokenUsage;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendLineIfPresent;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.value;

/**
 * DRAFT 章节初稿节点。
 *
 * @author ninth
 * @date 2026/8/24
 * @description
 */
@Service
@Slf4j
public class DraftChapterNode implements NodeAction<ChapterGraphState> {
    private final IChapterModelPort chapterModelPort;
    private final ChapterModelRetryExecutor retryExecutor;
    private final PromptTraceRecorder promptTraceRecorder;

    public DraftChapterNode(IChapterModelPort chapterModelPort) {
        this(
                chapterModelPort,
                new ChapterModelRetryExecutor(),
                new PromptTraceRecorder()
        );
    }

    public DraftChapterNode(
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
    public DraftChapterNode(
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
        return applyStreaming(state, chunk -> { });
    }

    /**
     * 以流式方式生成初稿，并在收到每个模型 chunk 时通知调用方。
     */
    public Map<String, Object> applyStreaming(
            ChapterGraphState state,
            Consumer<String> chunkConsumer
    ) {
        return applyStreaming(state, chunkConsumer, () -> false, subscription -> { });
    }

    /**
     * 以流式方式生成初稿，并允许上层在生成期间取消模型订阅。
     */
    public Map<String, Object> applyStreaming(
            ChapterGraphState state,
            Consumer<String> chunkConsumer,
            BooleanSupplier cancellationRequested,
            Consumer<Disposable> activeSubscription
    ) {
        ChapterContextAggregate context = state.context()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "DRAFT 节点缺少 context"
                ));
        requireChapterOutline(context);
        AtomicReference<GenerationMetricsDelta> metrics =
                new AtomicReference<>(GenerationMetricsDelta.empty());
        ChapterModelRetryExecutor.RetryResult<String> result = draftWithRetry(
                context,
                state.retryCount(),
                chunkConsumer,
                cancellationRequested,
                activeSubscription,
                state,
                delta -> metrics.updateAndGet(current -> current.plus(delta))
        );
        MemorySourceVersion sourceVersion = MemorySourceVersion.create(result.value());
        return Map.of(
                ChapterGraphKeys.DRAFT, result.value(),
                ChapterGraphKeys.SOURCE_VERSION, sourceVersion,
                ChapterGraphKeys.MEMORY_CANDIDATES, MemoryCandidate.revalidateAll(
                        state.memoryCandidates(), sourceVersion, result.value()),
                ChapterGraphKeys.CURRENT_NODE, "DRAFT",
                ChapterGraphKeys.COMPLETED_STAGES, List.of("DRAFT"),
                ChapterGraphKeys.RETRY_COUNT, state.retryCount() + result.retryCount(),
                ChapterGraphKeys.GENERATION_METRICS_DELTA, metrics.get()
        );
    }

    private void requireChapterOutline(ChapterContextAggregate context) {
        if (context.getChapterPlan() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "DRAFT 节点缺少 chapterPlan"
            );
        }
        if (context.getArc() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "DRAFT 节点缺少当前 ARC"
            );
        }
    }

    private String draft(ChapterContextAggregate context) {
        return draftWithRetry(context, 0, chunk -> { }).value();
    }

    private ChapterModelRetryExecutor.RetryResult<String> draftWithRetry(
            ChapterContextAggregate context,
            int usedRetryCount
    ) {
        return draftWithRetry(context, usedRetryCount, chunk -> { });
    }

    private ChapterModelRetryExecutor.RetryResult<String> draftWithRetry(
            ChapterContextAggregate context,
            int usedRetryCount,
            Consumer<String> chunkConsumer
    ) {
        return draftWithRetry(
                context,
                usedRetryCount,
                chunkConsumer,
                () -> false,
                subscription -> { }
        );
    }

    private ChapterModelRetryExecutor.RetryResult<String> draftWithRetry(
            ChapterContextAggregate context,
            int usedRetryCount,
            Consumer<String> chunkConsumer,
            BooleanSupplier cancellationRequested,
            Consumer<Disposable> activeSubscription
    ) {
        return draftWithRetry(
                context,
                usedRetryCount,
                chunkConsumer,
                cancellationRequested,
                activeSubscription,
                null
        );
    }

    private ChapterModelRetryExecutor.RetryResult<String> draftWithRetry(
            ChapterContextAggregate context,
            int usedRetryCount,
            Consumer<String> chunkConsumer,
            BooleanSupplier cancellationRequested,
            Consumer<Disposable> activeSubscription,
            ChapterGraphState state
    ) {
        return draftWithRetry(
                context,
                usedRetryCount,
                chunkConsumer,
                cancellationRequested,
                activeSubscription,
                state,
                ignored -> { }
        );
    }

    private ChapterModelRetryExecutor.RetryResult<String> draftWithRetry(
            ChapterContextAggregate context,
            int usedRetryCount,
            Consumer<String> chunkConsumer,
            BooleanSupplier cancellationRequested,
            Consumer<Disposable> activeSubscription,
            ChapterGraphState state,
            Consumer<GenerationMetricsDelta> metricsConsumer
    ) {
        log.info(
                "开始生成章节初稿，projectCode={}，chapterNumber={}",
                context.getProject().getProjectCode(),
                context.getChapterPlan().getChapterNumber()
        );
        Consumer<String> listener = chunkConsumer == null ? chunk -> { } : chunkConsumer;
        BooleanSupplier cancellation = cancellationRequested == null
                ? () -> false
                : cancellationRequested;
        Consumer<Disposable> subscriptionHandler = activeSubscription == null
                ? subscription -> { }
                : activeSubscription;
        ChapterGenerationVariant generationVariant = state == null
                ? ChapterGenerationVariant.defaultVariant() : state.generationVariant();
        String systemPrompt = SystemPrompt.draftSystemPrompt(generationVariant);
        String userPrompt = buildDraftUserPrompt(context, generationVariant);
        logDraftContextSize(context, systemPrompt, userPrompt);
        ChapterModelRetryExecutor.RetryResult<String> result = retryExecutor.execute(
                usedRetryCount,
                attempt -> {
                    PromptTraceRecord trace = state == null
                            ? null
                            : state.promptTrace(
                                    "DRAFT",
                                    attempt,
                                    systemPrompt,
                                    userPrompt
                            );
                    long startedAt = System.nanoTime();
                    StringBuilder responseBuffer = new StringBuilder();
                    try {
                        String responseText = streamDraft(
                                listener,
                                cancellation,
                                subscriptionHandler,
                                systemPrompt,
                                userPrompt,
                                responseBuffer,
                                attempt,
                                usage -> metricsConsumer.accept(
                                    GenerationMetricsDelta.tokens(usage))
                        );
                        if (responseText == null || responseText.isBlank()) {
                            throw AppException.internal(
                                    ResponseCode.E0003.getCode(),
                                    ResponseCode.E0003.getMessage() + "：正文为空"
                            );
                        }
                        metricsConsumer.accept(GenerationMetricsDelta.forCall("DRAFT", null));
                        promptTraceRecorder.recordSuccess(
                                trace,
                                responseText,
                                elapsedMillis(startedAt)
                        );
                        return responseText;
                    } catch (RuntimeException exception) {
                        promptTraceRecorder.recordFailure(
                                trace,
                                responseBuffer.toString(),
                                exception,
                                elapsedMillis(startedAt)
                        );
                        throw exception;
                    }
                },
                exception -> false
        );
        String draft = result.value();
        if (draft == null || draft.isBlank()) {
            throw AppException.internal(
                    ResponseCode.E0003.getCode(),
                    ResponseCode.E0003.getMessage() + "：正文为空"
            );
        }

        return new ChapterModelRetryExecutor.RetryResult<>(draft.trim(), result.retryCount());
    }

    private void logDraftContextSize(
            ChapterContextAggregate context,
            String systemPrompt,
            String userPrompt
    ) {
        String memorySection = sectionBetween(userPrompt, "## 此前章节记忆", "## 人物资料");
        String characterSection = sectionBetween(userPrompt, "## 人物资料", "## 故事设定");
        String storySettingsSection = sectionBetween(userPrompt, "## 故事设定", "## 写作风格");
        String writingStyleSection = sectionBetween(userPrompt, "## 写作风格", "## 本章写作参数");

        log.info(
                "DRAFT context: chapter={}, systemPromptChars={}, userPromptChars={}, "
                        + "previousChapterChars={}, memoryCount={}, memoryChars={}, "
                        + "characterContextChars={}, storyBibleChars={}",
                context.getChapterPlan().getChapterNumber(),
                systemPrompt.length(),
                userPrompt.length(),
                previousChapterChars(context),
                countOccurrences(memorySection, "### 第 "),
                memorySection.length(),
                characterSection.length(),
                storySettingsSection.length() + writingStyleSection.length()
        );
    }

    private int previousChapterChars(ChapterContextAggregate context) {
        ChapterHistoryVO history = context.getHistory();
        PreviousChapterVO previousChapter = history == null ? null : history.getPreviousChapter();
        Integer currentChapterNumber = context.getChapterPlan().getChapterNumber();
        return previousChapter != null && isDirectPreviousChapter(previousChapter, currentChapterNumber)
                && previousChapter.getContent() != null
                ? previousChapter.getContent().length()
                : 0;
    }

    private String sectionBetween(String prompt, String startMarker, String endMarker) {
        int start = prompt.indexOf(startMarker);
        int end = prompt.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0 || end < start) {
            return "";
        }
        return prompt.substring(start, end);
    }

    private int countOccurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private String streamDraft(
            Consumer<String> chunkConsumer,
            BooleanSupplier cancellationRequested,
            Consumer<Disposable> activeSubscription,
            String systemPrompt,
            String userPrompt,
            StringBuilder responseBuffer,
            int attempt,
            Consumer<ChapterTokenUsage> usageConsumer
    ) {
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("章节初稿生成已取消");
        }
        Flux<String> chunks = chapterModelPort.stream(
                systemPrompt,
                userPrompt,
                "DRAFT",
                attempt + 1,
                usageConsumer
        );
        if (chunks == null) {
            throw AppException.internal(
                    ResponseCode.E0001.getCode(),
                "章节模型流式调用未返回内容流"
            );
        }
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Disposable subscription = chunks
                .doOnNext(chunk -> {
                    if (cancellationRequested.getAsBoolean()) {
                        throw new CancellationException("章节初稿生成已取消");
                    }
                    responseBuffer.append(chunk);
                    chunkConsumer.accept(chunk);
                })
                .subscribe(
                        ignored -> { },
                        error -> {
                            failure.set(error);
                            finished.countDown();
                        },
                        finished::countDown
                );
        activeSubscription.accept(subscription);
        try {
            while (!finished.await(100, TimeUnit.MILLISECONDS)) {
                if (cancellationRequested.getAsBoolean()) {
                    subscription.dispose();
                    throw new CancellationException("章节初稿生成已取消");
                }
            }
        } catch (InterruptedException exception) {
            subscription.dispose();
            Thread.currentThread().interrupt();
            throw new CancellationException("章节初稿生成等待被中断");
        } finally {
            activeSubscription.accept(null);
        }
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("章节初稿生成已取消");
        }
        Throwable streamFailure = failure.get();
        if (streamFailure != null) {
            if (streamFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("章节模型流式调用失败", streamFailure);
        }
        return responseBuffer.toString();
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    String buildDraftUserPrompt(ChapterContextAggregate context) {
        return buildDraftUserPrompt(context, ChapterGenerationVariant.defaultVariant());
    }

    String buildDraftUserPrompt(
            ChapterContextAggregate context,
            ChapterGenerationVariant generationVariant
    ) {
        StringBuilder prompt = new StringBuilder(16184);
        appendChapterPlan(prompt, context);
        appendHistory(prompt, context, generationVariant);
        appendCharacters(prompt, context);
        appendStorySettings(prompt, context, generationVariant);
        appendWritingStyle(prompt, context);
        appendWritingParameters(prompt, context);

        return prompt.toString();
    }

    private void appendChapterPlan(
            StringBuilder prompt,
            ChapterContextAggregate context
    ) {
        prompt.append("## 本章计划\n");
        ChapterPlanEntity chapterPlan = context.getChapterPlan();
        if (chapterPlan == null) {
            prompt.append("无\n\n");
            return;
        }
        if (context.getArc() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "DRAFT 上下文缺少当前 ARC"
            );
        }

        appendLineIfPresent(prompt, "章节号", chapterPlan.getChapterNumber());
        appendLineIfPresent(prompt, "章节标题", context.getArc().title());
        appendLineIfPresent(prompt, "章节摘要", chapterPlan.getSummary());
        prompt.append('\n');
    }

    private void appendStorySettings(
            StringBuilder prompt,
            ChapterContextAggregate context,
            ChapterGenerationVariant generationVariant
    ) {
        StoryBibleEntity storyBible = context.getStoryBible();

        prompt.append("## 故事设定\n");
        ChapterPromptFormatter.appendHardRules(prompt, "不可违反的硬规则", storyBible.getHardRulesJson());
        MemoryContextPack memoryContextPack = context.getMemoryContextPack();
        if (memoryContextPack != null && !memoryContextPack.rules().isEmpty()) {
            appendMemoryItems(
                    prompt, "记忆召回的相关规则", memoryContextPack.rules(), generationVariant);
        }
        ChapterPromptFormatter.appendPowerSystem(prompt, "力量体系", storyBible.getPowerSystemJson());
        appendLineIfPresent(prompt, "世界背景", storyBible.getWorldBackground());
        prompt.append('\n');
    }

    private void appendWritingStyle(StringBuilder prompt, ChapterContextAggregate context) {
        prompt.append("## 写作风格\n");
        appendLineIfPresent(prompt, "文风指南", context.getStoryBible().getStyleGuide());
        prompt.append('\n');
    }

    private void appendCharacters(StringBuilder prompt, ChapterContextAggregate context) {
        prompt.append("## 人物资料\n");
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

    private void appendHistory(
            StringBuilder prompt,
            ChapterContextAggregate context,
            ChapterGenerationVariant generationVariant
    ) {
        ChapterHistoryVO history = context.getHistory();
        Integer currentChapterNumber = context.getChapterPlan() == null
                ? null
                : context.getChapterPlan().getChapterNumber();
        appendPreviousChapter(
                prompt,
                history == null ? null : history.getPreviousChapter(),
                currentChapterNumber
        );
        appendMemoryHistory(
                prompt, history, currentChapterNumber,
                context.getMemoryContextPack(), generationVariant);
        appendStoryState(prompt, history, context.getMemoryContextPack(), generationVariant);
    }

    private void appendStoryState(
            StringBuilder prompt,
            ChapterHistoryVO history,
            MemoryContextPack memoryContextPack,
            ChapterGenerationVariant generationVariant
    ) {
        prompt.append("## 当前有效状态\n");
        if (memoryContextPack != null && !memoryContextPack.currentStates().isEmpty()) {
            appendMemoryItems(
                    prompt, "记忆召回状态", memoryContextPack.currentStates(), generationVariant);
            prompt.append('\n');
            return;
        }
        ChapterPromptFormatter.appendStoryStateSnapshot(
                prompt,
                history == null ? null : history.getStoryStateSnapshot()
        );
    }

    private void appendMemoryHistory(
            StringBuilder prompt,
            ChapterHistoryVO history,
            Integer currentChapterNumber,
            MemoryContextPack memoryContextPack,
            ChapterGenerationVariant generationVariant
    ) {
        if (memoryContextPack == null || memoryContextPack.items().isEmpty()) {
            appendEarlierMemories(prompt, history, currentChapterNumber);
            return;
        }

        prompt.append("## 此前章节记忆\n");
        appendMemoryItems(
                prompt, "未解决剧情线程", memoryContextPack.openLoops(), generationVariant);
        appendMemoryItems(
                prompt, "长期压缩记忆", memoryContextPack.consolidated(), generationVariant);
        appendMemoryItems(
                prompt, "必要剧情桥接", memoryContextPack.episodes(), generationVariant);
        prompt.append('\n');
    }

    private void appendMemoryItems(
            StringBuilder prompt,
            String label,
            List<MemoryContextItem> items,
            ChapterGenerationVariant generationVariant
    ) {
        if (generationVariant.knownVsNewDraftControlEnabled()) {
            ChapterPromptFormatter.appendDraftMemoryItems(prompt, label, items);
        } else {
            ChapterPromptFormatter.appendMemoryItems(prompt, label, items);
        }
    }

    private void appendPreviousChapter(
            StringBuilder prompt,
            PreviousChapterVO previousChapter,
            Integer currentChapterNumber
    ) {
        prompt.append("## 上一章正文\n");
        if (previousChapter == null) {
            prompt.append("无，这是第一章。\n\n");
            return;
        }
        if (!isDirectPreviousChapter(previousChapter, currentChapterNumber)) {
            prompt.append("无，未找到当前章节的直接上一章。\n\n");
            return;
        }

        appendLineIfPresent(prompt, "章节号", previousChapter.getChapterNumber());
        appendLineIfPresent(prompt, "章节标题", previousChapter.getTitle());
        appendLineIfPresent(prompt, "上一章正文", previousChapter.getContent());
        prompt.append('\n');
    }

    private void appendEarlierMemories(
            StringBuilder prompt,
            ChapterHistoryVO history,
            Integer currentChapterNumber
    ) {
        prompt.append("## 此前章节记忆\n");
        if (history == null || history.getRecentMemories() == null) {
            prompt.append("无\n\n");
            return;
        }

        boolean rendered = false;
        // N-1 已在上方以完整正文传入，不能再次渲染其 Memory，避免同一剧情重复进入 DRAFT。
        List<ChapterMemoryVO> earlierMemories = history.getRecentMemories().stream()
                .filter(memory -> isEarlierChapterMemory(memory, currentChapterNumber))
                .sorted(Comparator.comparing(ChapterMemoryVO::getChapterNumber))
                .toList();
        for (ChapterMemoryVO memory : earlierMemories) {
            StringBuilder memoryBody = new StringBuilder();
            appendLineIfPresent(memoryBody, "摘要", memory.getShortSummary());
            appendMemoryListIfPresent(memoryBody, "关键事件", memory.getKeyEvents());
            appendMemoryListIfPresent(memoryBody, "未解决", memory.getUnresolved());
            appendLineIfPresent(memoryBody, "结尾钩子", memory.getEndingHook());
            if (memoryBody.length() == 0) {
                continue;
            }

            rendered = true;
            prompt.append("### 第 ")
                    .append(value(memory.getChapterNumber()))
                    .append(" 章\n")
                    .append(memoryBody)
                    .append('\n');
        }
        if (!rendered) {
            prompt.append("无\n\n");
        }
    }

    private void appendMemoryListIfPresent(
            StringBuilder prompt,
            String label,
            List<String> values
    ) {
        if (values == null || values.stream().noneMatch(item -> item != null && !item.isBlank())) {
            return;
        }
        prompt.append(label).append("：\n");
        for (String item : values) {
            if (item != null && !item.isBlank()) {
                prompt.append("- ").append(item.trim()).append('\n');
            }
        }
    }

    private boolean isEarlierChapterMemory(
            ChapterMemoryVO memory,
            Integer currentChapterNumber
    ) {
        return memory != null
                && currentChapterNumber != null
                && memory.getChapterNumber() != null
                && memory.getChapterNumber() <= currentChapterNumber - 2;
    }

    private boolean isDirectPreviousChapter(
            PreviousChapterVO previousChapter,
            Integer currentChapterNumber
    ) {
        return currentChapterNumber != null
                && currentChapterNumber > 1
                && previousChapter.getChapterNumber() != null
                && previousChapter.getChapterNumber().equals(currentChapterNumber - 1);
    }

    private void appendWritingParameters(StringBuilder prompt, ChapterContextAggregate context) {
        NovelProjectEntity project = context.getProject();

        prompt.append("## 本章写作参数\n");
        appendLineIfPresent(prompt, "小说标题", project.getTitle());
        appendLineIfPresent(prompt, "小说题材", project.getGenre());
        appendLineIfPresent(prompt, "目标字数", project.getWordsPerChapter());
    }

}
