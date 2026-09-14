package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.domain.memory.service.FinalChapterCandidateExtractor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.COMPRESSION_SYSTEM_PROMPT;

/**
 * COMPRESSION 章节记忆节点。
 * 将定稿正文压缩为供后续章节使用的业务记忆。
 */
@Slf4j
@Component
public class CompressChapterNode implements NodeAction<ChapterGraphState> {

    private final IChapterModelPort chapterModelPort;
    private final ChapterModelRetryExecutor retryExecutor;
    private final PromptTraceRecorder promptTraceRecorder;
    private final FinalChapterCandidateExtractor candidateExtractor;

    public CompressChapterNode(IChapterModelPort chapterModelPort) {
        this(
                chapterModelPort,
                new ChapterModelRetryExecutor(),
                new PromptTraceRecorder(),
                new FinalChapterCandidateExtractor()
        );
    }

    public CompressChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                new PromptTraceRecorder(),
                new FinalChapterCandidateExtractor()
        );
    }

    @Autowired
    public CompressChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                promptTraceRecorder,
                new FinalChapterCandidateExtractor()
        );
    }

    public CompressChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder,
            FinalChapterCandidateExtractor candidateExtractor
    ) {
        this.chapterModelPort = chapterModelPort;
        this.retryExecutor = retryExecutor;
        this.promptTraceRecorder = promptTraceRecorder;
        this.candidateExtractor = candidateExtractor;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        ChapterContextAggregate context = state.context()
                .orElseThrow(() -> illegalParameter("COMPRESSION 节点缺少 context"));
        int chapterNumber = state.chapterNumber()
                .orElseThrow(() -> illegalParameter("COMPRESSION 节点缺少 chapterNumber"));
        String draft = state.draft()
                .orElseThrow(() -> illegalParameter("COMPRESSION 节点缺少 draft"));
        AtomicReference<GenerationMetricsDelta> metrics =
                new AtomicReference<>(GenerationMetricsDelta.empty());

        ChapterModelRetryExecutor.RetryResult<CompressionResult> result = compressWithRetry(
                context,
                chapterNumber,
                draft,
                state.retryCount(),
                state,
                delta -> metrics.updateAndGet(current -> current.plus(delta))
        );
        String finalContent = state.draft()
                .orElseThrow(() -> compressionFailure("章节正文为空"));
        MemorySourceVersion sourceVersion = state.sourceVersion()
                .filter(version -> version.matchesContent(finalContent))
                .orElseGet(() -> MemorySourceVersion.create(
                        "compression:" + chapterNumber, finalContent));
        String projectCode = state.projectCode().orElse("unknown-project");
        List<MemoryCandidate> candidates = candidateExtractor.extract(
                projectCode, chapterNumber, finalContent, sourceVersion);
        return Map.of(
                ChapterGraphKeys.MEMORY, result.value().memory(),
                ChapterGraphKeys.STORY_STATE_SNAPSHOT, result.value().storyStateSnapshot(),
                ChapterGraphKeys.SOURCE_VERSION, sourceVersion,
                ChapterGraphKeys.MEMORY_CANDIDATES, candidates,
                ChapterGraphKeys.CURRENT_NODE, "COMPRESSION",
                ChapterGraphKeys.COMPLETED_STAGES, List.of("COMPRESSION"),
                ChapterGraphKeys.RETRY_COUNT, state.retryCount() + result.retryCount(),
                ChapterGraphKeys.GENERATION_METRICS_DELTA, metrics.get()
        );
    }

    private ChapterMemoryVO compress(
            ChapterContextAggregate context,
            int chapterNumber,
            String finalContent
    ) {
        return compressWithRetry(context, chapterNumber, finalContent, 0).value().memory();
    }

    private ChapterModelRetryExecutor.RetryResult<CompressionResult> compressWithRetry(
            ChapterContextAggregate context,
            int chapterNumber,
            String finalContent,
            int usedRetryCount
    ) {
        return compressWithRetry(
                context,
                chapterNumber,
                finalContent,
                usedRetryCount,
                null
        );
    }

    private ChapterModelRetryExecutor.RetryResult<CompressionResult> compressWithRetry(
            ChapterContextAggregate context,
            int chapterNumber,
            String finalContent,
            int usedRetryCount,
            ChapterGraphState state
    ) {
        return compressWithRetry(
                context,
                chapterNumber,
                finalContent,
                usedRetryCount,
                state,
                ignored -> { }
        );
    }

    private ChapterModelRetryExecutor.RetryResult<CompressionResult> compressWithRetry(
            ChapterContextAggregate context,
            int chapterNumber,
            String finalContent,
            int usedRetryCount,
            ChapterGraphState state,
            Consumer<GenerationMetricsDelta> metricsConsumer
    ) {
        if (finalContent == null || finalContent.isBlank()) {
            throw compressionFailure("章节正文为空");
        }

        String systemPrompt = COMPRESSION_SYSTEM_PROMPT;
        String userPrompt = buildCompressionPrompt(context, chapterNumber, finalContent);
        ChapterModelRetryExecutor.RetryResult<CompressionResult> result =
                retryExecutor.execute(
                        usedRetryCount,
                        attempt -> {
                            PromptTraceRecord trace = state == null
                                    ? null
                                    : state.promptTrace(
                                            "COMPRESSION",
                                            attempt,
                                            systemPrompt,
                                            userPrompt
                                    );
                            long startedAt = System.nanoTime();
                            String responseText = null;
                            try {
                                ChapterModelResponse<ChapterMemoryResponse> response =
                                        chapterModelPort.callWithRawResponse(
                                                systemPrompt,
                                                userPrompt,
                                                ChapterMemoryResponse.class,
                                                "COMPRESSION",
                                                attempt + 1
                                        );
                                metricsConsumer.accept(GenerationMetricsDelta.tokens(
                                        response == null ? null : response.usage()));
                                responseText = promptTraceRecorder.responseText(
                                        response == null ? null : response.rawText(),
                                        response == null ? null : response.value()
                                );
                                CompressionResult compression = toCompression(
                                        response == null ? null : response.value(),
                                        chapterNumber
                                );
                                metricsConsumer.accept(
                                        GenerationMetricsDelta.forCall("COMPRESSION", null));
                                promptTraceRecorder.recordSuccess(
                                        trace,
                                        responseText,
                                        elapsedMillis(startedAt)
                                );
                                return compression;
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
        ChapterMemoryVO memory = result.value().memory();
        log.info("章节记忆压缩完成，chapterNumber={}，keyEventCount={}",
                chapterNumber, memory.getKeyEvents().size());
        return result;
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    String buildCompressionPrompt(
            ChapterContextAggregate context,
            int chapterNumber,
            String finalContent
    ) {
        StringBuilder prompt = new StringBuilder(finalContent == null ? 128 : finalContent.length() + 128);
        prompt.append("## 当前章节\n")
                .append("章节号：").append(chapterNumber).append("\n\n")
                .append("## 本章开始前的当前有效状态\n");
        ChapterPromptFormatter.appendStoryStateSnapshot(
                prompt,
                context == null || context.getHistory() == null
                        ? null : context.getHistory().getStoryStateSnapshot()
        );
        prompt.append("## 当前章节完整正文\n")
                .append(finalContent == null ? "无" : finalContent.trim())
                .append('\n');
        return prompt.toString();
    }

    private ChapterMemoryVO toMemory(
            ChapterMemoryResponse response,
            int chapterNumber
    ) {
        StructuredModelOutputValidator.validate(response);
        if (response == null) {
            throw compressionFailure("压缩结果为空");
        }

        String shortSummary = requireText(response.shortSummary(), "章节短摘要为空");
        List<String> keyEvents = normalizeTextList(response.keyEvents());
        List<String> unresolved = normalizeTextList(response.unresolved());
        String endingHook = requireText(response.endingHook(), "结尾钩子为空");
        return new ChapterMemoryVO(
                chapterNumber,
                shortSummary,
                keyEvents,
                unresolved,
                endingHook
        );
    }

    private CompressionResult toCompression(
            ChapterMemoryResponse response,
            int chapterNumber
    ) {
        ChapterMemoryVO memory = toMemory(response, chapterNumber);
        StoryStateSnapshot snapshot = response.state() == null
                ? StoryStateSnapshot.empty()
                : response.state();
        return new CompressionResult(memory, snapshot);
    }

    private List<String> normalizeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private String requireText(String value, String detail) {
        if (value == null || value.isBlank()) {
            throw compressionFailure(detail);
        }
        return value.trim();
    }

    private AppException illegalParameter(String detail) {
        return AppException.internal(
                ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
    }

    private AppException compressionFailure(String detail) {
        return AppException.internal(
                ResponseCode.E0006.getCode(),
                ResponseCode.E0006.getMessage() + "：" + detail
        );
    }

    private record CompressionResult(
            ChapterMemoryVO memory,
            StoryStateSnapshot storyStateSnapshot
    ) {
    }
}
