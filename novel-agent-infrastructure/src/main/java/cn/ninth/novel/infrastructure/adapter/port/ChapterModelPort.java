package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterTokenUsage;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.infrastructure.config.ChapterModelProperties;
import cn.ninth.novel.infrastructure.config.ModelStageConfig;
import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.ninth.novel.types.enums.ResponseCode.E0001;
import static cn.ninth.novel.types.enums.ResponseCode.E0007;

/**
 * ChapterModelPort
 *
 * @author ninth
 * @date 2026/8/20
 * @description
 */

@Slf4j
@Service
public class ChapterModelPort implements IChapterModelPort {

    private static final String UNKNOWN_STAGE = "UNKNOWN";

    private final ChatClient chatClient;
    private final ChapterModelProperties chapterModelProperties;
    private final ModelTimeoutProperties modelTimeoutProperties;
    private final ModelObservabilityMetadata modelMetadata;
    private final boolean localStructuredDebug;

    public ChapterModelPort(ChatClient chatClient) {
        this(
                chatClient,
                new ChapterModelProperties(),
                new ModelTimeoutProperties(),
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    ChapterModelPort(ChatClient chatClient, Duration structuredStreamTimeout) {
        this(
                chatClient,
                new ChapterModelProperties(),
                ModelTimeoutProperties.withDefaultTimeout(structuredStreamTimeout),
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    @Autowired
    public ChapterModelPort(
            ChatClient chatClient,
            Environment environment,
            ChapterModelProperties chapterModelProperties,
            ModelTimeoutProperties modelTimeoutProperties
    ) {
        this(
                chatClient,
                chapterModelProperties,
                modelTimeoutProperties,
                ModelObservabilityMetadata.from(environment),
                environment.acceptsProfiles(Profiles.of("dev", "local"))
        );
    }

    public ChapterModelPort(ChatClient chatClient, Environment environment) {
        this(
                chatClient,
                environment,
                new ChapterModelProperties(),
                new ModelTimeoutProperties()
        );
    }

    ChapterModelPort(ChatClient chatClient, ModelTimeoutProperties modelTimeoutProperties) {
        this(chatClient, new ChapterModelProperties(), modelTimeoutProperties);
    }

    ChapterModelPort(ChatClient chatClient, ChapterModelProperties chapterModelProperties) {
        this(chatClient, chapterModelProperties, new ModelTimeoutProperties());
    }

    ChapterModelPort(
            ChatClient chatClient,
            ChapterModelProperties chapterModelProperties,
            ModelTimeoutProperties modelTimeoutProperties
    ) {
        this(
                chatClient,
                chapterModelProperties,
                modelTimeoutProperties,
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    private ChapterModelPort(
            ChatClient chatClient,
            ChapterModelProperties chapterModelProperties,
            ModelTimeoutProperties modelTimeoutProperties,
            ModelObservabilityMetadata modelMetadata,
            boolean localStructuredDebug
    ) {
        this.chatClient = chatClient;
        this.chapterModelProperties = Objects.requireNonNull(chapterModelProperties);
        this.modelTimeoutProperties = Objects.requireNonNull(modelTimeoutProperties);
        this.modelMetadata = chapterModelProperties.getModel() == null
                || chapterModelProperties.getModel().isBlank()
                ? modelMetadata
                : modelMetadata.withModel(chapterModelProperties.getModel().trim());
        this.localStructuredDebug = localStructuredDebug;
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        return stream(systemPrompt, userPrompt, UNKNOWN_STAGE, 1);
    }

    @Override
    public Flux<String> stream(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt
    ) {
        return stream(systemPrompt, userPrompt, stage, attempt, ignored -> { });
    }

    @Override
    public Flux<String> stream(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            Consumer<ChapterTokenUsage> usageConsumer
    ) {
        String normalizedStage = normalizeStage(stage);
        int normalizedAttempt = normalizeAttempt(attempt);
        return Flux.defer(() -> {
            long startedAt = System.nanoTime();
            AtomicLong firstTokenAt = new AtomicLong();
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicInteger contentLength = new AtomicInteger();
            AtomicReference<String> finishReason = new AtomicReference<>();
            AtomicReference<Object> usage = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Flux<String> chunks;
            try {
                chunks = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .options(optionsForStage(normalizedStage))
                        .stream()
                        .chatResponse()
                        .map(chatResponse -> {
                            String responseFinishReason = finishReason(chatResponse);
                            if (responseFinishReason != null) {
                                finishReason.set(responseFinishReason);
                            }
                            Object responseUsage = usage(chatResponse);
                            if (responseUsage != null) {
                                usage.set(responseUsage);
                            }
                            return streamContent(chatResponse);
                        });
                if (chunks == null) {
                    throw AppException.internal(
                            E0001.getCode(),
                            "章节模型流式调用未返回内容流"
                    );
                }
            } catch (Throwable exception) {
                logStreamResult(
                        true,
                        "REQUEST_FAILED",
                        systemPrompt,
                        userPrompt,
                        normalizedStage,
                        normalizedAttempt,
                        startedAt,
                        firstTokenAt,
                        chunkCount,
                        contentLength,
                        finishReason.get(),
                        usage.get(),
                        exception
                );
                return Flux.error(exception);
            }

            return chunks
                    .doOnNext(chunk -> {
                        chunkCount.incrementAndGet();
                        if (chunk != null) {
                            contentLength.addAndGet(chunk.length());
                            if (!chunk.isEmpty() && firstTokenAt.compareAndSet(0, System.nanoTime())) {
                                log.info(ModelObservabilityLogFormatter.format(
                                        "first-token",
                                        "stream",
                                        normalizedStage,
                                        normalizedAttempt,
                                        null,
                                        null,
                                        modelMetadata,
                                        "String",
                                        length(systemPrompt),
                                        length(userPrompt),
                                        null,
                                        null,
                                        null,
                                        ttftMillis(startedAt, firstTokenAt.get()),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ));
                            }
                        }
                    })
                    .doOnError(failure::set)
                    .doFinally(signalType -> {
                        boolean streamFailed = signalType == reactor.core.publisher.SignalType.ON_ERROR;
                        String failureType = streamFailed
                                ? "READ_FAILED"
                                : contentLength.get() == 0 ? "EMPTY_RESPONSE" : null;
                        logStreamResult(
                                streamFailed || "EMPTY_RESPONSE".equals(failureType),
                                failureType,
                                systemPrompt,
                                userPrompt,
                                normalizedStage,
                                normalizedAttempt,
                                startedAt,
                                firstTokenAt,
                                chunkCount,
                                contentLength,
                                finishReason.get(),
                                usage.get(),
                                failure.get()
                        );
                        notifyUsage(usageConsumer, tokenUsage(usage.get()));
                    })
                    .onErrorMap(exception ->
                            AppException.user(E0001.getCode(), E0001.getMessage(), exception)
                    );
        });
    }

    @Override
    public String call(String systemPrompt, String userPrompt) {
        return callWithUsage(systemPrompt, userPrompt, UNKNOWN_STAGE, 1).value();
    }

    @Override
    public String call(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt
    ) {
        return callWithUsage(systemPrompt, userPrompt, stage, attempt).value();
    }

    @Override
    public ChapterModelResponse<String> callWithUsage(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt
    ) {
        String normalizedStage = normalizeStage(stage);
        int normalizedAttempt = normalizeAttempt(attempt);
        long startedAt = System.nanoTime();
        long requestCostMs;
        ChatClient.CallResponseSpec response = null;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(optionsForStage(normalizedStage))
                    .call();
            if (response == null) {
                throw new IllegalStateException("模型请求未返回响应");
            }
        } catch (Exception exception) {
            requestCostMs = elapsedMillis(startedAt);
            logCallResult(
                    true,
                    "failed",
                    "REQUEST_FAILED",
                    "sync",
                    normalizedStage,
                    normalizedAttempt,
                    "String",
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    null,
                    null,
                    requestCostMs,
                    0,
                    response,
                    exception
            );
            throw modelRequestFailure(exception);
        }

        requestCostMs = elapsedMillis(startedAt);
        long readStartedAt = System.nanoTime();
        String content;
        try {
            content = response.content();
        } catch (Exception exception) {
            logCallResult(
                    true,
                    "failed",
                    "READ_FAILED",
                    "sync",
                    normalizedStage,
                    normalizedAttempt,
                    "String",
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    elapsedMillis(readStartedAt),
                    null,
                    elapsedMillis(startedAt),
                    0,
                    response,
                    exception
            );
            throw modelRequestFailure(exception);
        }

        long readCostMs = elapsedMillis(readStartedAt);
        if (content == null || content.isBlank()) {
            AppException emptyResponse = AppException.internal(
                    E0001.getCode(),
                    "章节模型返回空内容"
            );
            logCallResult(
                    true,
                    "failed",
                    "EMPTY_RESPONSE",
                    "sync",
                    normalizedStage,
                    normalizedAttempt,
                    "String",
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    readCostMs,
                    null,
                    elapsedMillis(startedAt),
                    0,
                    response,
                    emptyResponse
            );
            throw emptyResponse;
        }

        logCallResult(
                false,
                "success",
                null,
                "sync",
                normalizedStage,
                normalizedAttempt,
                "String",
                systemPrompt,
                userPrompt,
                requestCostMs,
                readCostMs,
                null,
                elapsedMillis(startedAt),
                content.length(),
                response,
                null
        );
        return new ChapterModelResponse<>(content, null, tokenUsage(response));
    }

    @Override
    public <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        return callWithRawResponse(
                systemPrompt,
                userPrompt,
                responseType,
                UNKNOWN_STAGE,
                1
        ).value();
    }

    @Override
    public <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt
    ) {
        return callWithRawResponse(
                systemPrompt,
                userPrompt,
                responseType,
                stage,
                attempt
        ).value();
    }

    @Override
    public <T> ChapterModelResponse<T> callWithRawResponse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        return callWithRawResponse(
                systemPrompt,
                userPrompt,
                responseType,
                UNKNOWN_STAGE,
                1
        );
    }

    @Override
    public <T> ChapterModelResponse<T> callWithRawResponse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt
    ) {
        return callWithRawResponse(
                systemPrompt,
                userPrompt,
                responseType,
                stage,
                attempt,
                null
        );
    }

    @Override
    public <T> ChapterModelResponse<T> callWithRawResponse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt,
            Boolean reasoningEnabledOverride
    ) {
        ModelStageConfig effectiveConfig = effectiveConfig(stage, reasoningEnabledOverride);
        ChapterTokenUsage accumulatedUsage = null;
        for (int retry = 0; ; retry++) {
            try {
                ChapterModelResponse<T> response = callOnce(
                        systemPrompt,
                        userPrompt,
                        responseType,
                        stage,
                        normalizeAttempt(attempt) + retry,
                        effectiveConfig
                );
                return new ChapterModelResponse<>(
                        response.value(),
                        response.rawText(),
                        plusUsage(accumulatedUsage, response.usage())
                );
            } catch (AppException exception) {
                if (exception instanceof ChapterModelResponseException response) {
                    accumulatedUsage = plusUsage(accumulatedUsage, response.usage());
                }
                if (retry == 0 && exception.getCause() instanceof StructuredModelResponseParser.ParseException parsed
                        && "JSON_PARSE_FAILED".equals(parsed.failureType())) {
                    continue;
                }
                if (retry == 1) {
                    throw new ChapterModelResponseException(
                            exception.getCode(), exception.getInternalDetail(), exception,
                            exception instanceof ChapterModelResponseException response
                                    ? response.rawText() : null,
                            true,
                            accumulatedUsage);
                }
                throw exception;
            }
        }
    }

    private <T> ChapterModelResponse<T> callOnce(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt,
            ModelStageConfig effectiveConfig
    ) {
        String normalizedStage = normalizeStage(stage);
        int normalizedAttempt = normalizeAttempt(attempt);
        String responseTypeName = responseType == ReviewReportVO.class
                ? ReviewModelResponse.class.getSimpleName()
                : responseType.getSimpleName();
        long startedAt = System.nanoTime();
        long requestCostMs;
        Flux<ChatResponse> responses;
        AtomicLong firstResponseAt = new AtomicLong();
        AtomicLong firstChunkAt = new AtomicLong();
        AtomicInteger chunkCount = new AtomicInteger();
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();

        logStructuredStart(
                systemPrompt,
                userPrompt,
                normalizedStage,
                normalizedAttempt,
                responseTypeName,
                chapterConfiguration(normalizedStage, effectiveConfig)
        );

        try {
            responses = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(optionsForStage(effectiveConfig))
                    .stream()
                    .chatResponse();
            if (responses == null) {
                throw new IllegalStateException("模型请求未返回响应流");
            }
            requestCostMs = elapsedMillis(startedAt);
        } catch (Exception exception) {
            requestCostMs = elapsedMillis(startedAt);
            String failureType = StructuredStreamFailureClassifier.classify(exception, false);
            logCallResult(
                    true,
                    "failed",
                    failureType,
                    "structured",
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    null,
                    null,
                    null,
                    null,
                    null,
                    requestCostMs,
                    0,
                    0,
                    null,
                    exception
            );
            throw modelRequestFailure(failureType, exception);
        }

        long streamStartedAt = System.nanoTime();
        Duration structuredStreamTimeout = modelTimeoutProperties.timeoutForStage(normalizedStage);
        try {
            responses
                    .timeout(structuredStreamTimeout)
                    .doOnNext(chatResponse -> {
                        lastResponse.set(chatResponse);
                        firstResponseAt.compareAndSet(0, System.nanoTime());
                        String chunk = streamContent(chatResponse);
                        chunkCount.incrementAndGet();
                        if (chunk != null && !chunk.isEmpty()) {
                            contentBuilder.append(chunk);
                            firstChunkAt.compareAndSet(0, System.nanoTime());
                        }
                    })
                    .then()
                    .block();
        } catch (Exception exception) {
            long completedAt = System.nanoTime();
            String failureType = StructuredStreamFailureClassifier.classify(
                    exception,
                    firstChunkAt.get() != 0
            );
            logCallResult(
                    true,
                    "failed",
                    failureType,
                    "structured",
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    elapsedMillis(completedAt, streamStartedAt),
                    null,
                    firstResponseMillis(startedAt, firstResponseAt.get()),
                    ttftMillis(startedAt, firstChunkAt.get()),
                    generationMillis(firstChunkAt.get(), completedAt),
                    elapsedMillis(completedAt, startedAt),
                    chunkCount.get(),
                    contentBuilder.length(),
                    lastResponse.get(),
                    exception,
                    chapterConfiguration(normalizedStage, effectiveConfig)
            );
            throw modelRequestFailure(failureType, exception);
        }

        long completedAt = System.nanoTime();
        long readCostMs = elapsedMillis(completedAt, streamStartedAt);
        long totalCostMs = elapsedMillis(completedAt, startedAt);
        Long firstResponseMs = firstResponseMillis(startedAt, firstResponseAt.get());
        Long ttftMs = ttftMillis(startedAt, firstChunkAt.get());
        Long generationMs = generationMillis(firstChunkAt.get(), completedAt);
        String content = contentBuilder.toString();
        if (content == null || content.isBlank()) {
            AppException emptyResponse = AppException.internal(
                    E0001.getCode(),
                    "章节模型返回空内容"
            );
            logCallResult(
                    true,
                    "failed",
                    "EMPTY_RESPONSE",
                    "structured",
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    readCostMs,
                    null,
                    firstResponseMs,
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount.get(),
                    contentBuilder.length(),
                    lastResponse.get(),
                    emptyResponse,
                    chapterConfiguration(normalizedStage, effectiveConfig)
            );
            throw emptyResponse;
        }

        long parseStartedAt = System.nanoTime();
        String failureType = "SCHEMA_VALIDATION_FAILED";
        try {
            T result;
            if (responseType == ReviewReportVO.class) {
                ReviewModelResponse review = StructuredModelResponseParser.parse(content, ReviewModelResponse.class);
                result = responseType.cast(review == null ? null : review.toDomain());
            } else {
                result = StructuredModelResponseParser.parse(content, responseType);
            }
            StructuredModelOutputValidator.validate(result);
            totalCostMs = elapsedMillis(startedAt);

            logCallResult(
                    false,
                    "success",
                    null,
                    "structured",
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    readCostMs,
                    elapsedMillis(parseStartedAt),
                    firstResponseMs,
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount.get(),
                    content.length(),
                    lastResponse.get(),
                    null,
                    chapterConfiguration(normalizedStage, effectiveConfig)
            );
            return new ChapterModelResponse<>(
                    result,
                    content,
                    tokenUsage(lastResponse.get())
            );
        } catch (Exception e) {
            if (e instanceof StructuredModelResponseParser.ParseException parsed) {
                failureType = parsed.failureType();
            }
            totalCostMs = elapsedMillis(startedAt);
            AppException structuredFailure = structuredResponseFailure(
                    failureType + ": " + e.getMessage(),
                    e,
                    content,
                    tokenUsage(lastResponse.get())
            );
            logCallResult(
                    true,
                    "parse failed",
                    failureType,
                    "structured",
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    systemPrompt,
                    userPrompt,
                    requestCostMs,
                    readCostMs,
                    elapsedMillis(parseStartedAt),
                    firstResponseMs,
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount.get(),
                    length(content),
                    lastResponse.get(),
                    structuredFailure,
                    chapterConfiguration(normalizedStage, effectiveConfig)
            );
            logStructuredParseDebug(failureType, content, e);
            throw structuredFailure;
        }
    }

    private AppException modelRequestFailure(Throwable cause) {
        return modelRequestFailure("REQUEST_FAILED", cause);
    }

    private AppException modelRequestFailure(String failureType, Throwable cause) {
        String message = failureType != null && failureType.endsWith("_TIMEOUT")
                ? "模型响应超时，请重试"
                : E0001.getMessage();
        return AppException.user(E0001.getCode(), message, cause);
    }

    private ChapterModelResponseException structuredResponseFailure(
            String detail,
            Throwable cause,
            String rawText,
            ChapterTokenUsage usage
    ) {
        String message = E0007.getMessage() + (detail == null ? "" : "：" + detail);
        return new ChapterModelResponseException(
                cause instanceof AppException app && detail.startsWith("SCHEMA_VALIDATION_FAILED")
                        ? app.getCode() : E0007.getCode(),
                message,
                cause,
                rawText,
                false,
                usage
        );
    }

    private void logCallResult(
            boolean failed,
            String status,
            String failureType,
            String mode,
            String stage,
            int attempt,
            String responseType,
            String systemPrompt,
            String userPrompt,
            Long requestCostMs,
            Long readCostMs,
            Long parseCostMs,
            Long totalCostMs,
            Integer contentChars,
            ChatClient.CallResponseSpec response,
            Throwable exception
    ) {
        logCallResult(
                failed,
                status,
                failureType,
                mode,
                stage,
                attempt,
                responseType,
                systemPrompt,
                userPrompt,
                requestCostMs,
                readCostMs,
                parseCostMs,
                totalCostMs,
                contentChars,
                response,
                exception,
                chapterConfiguration(stage)
        );
    }

    private void logCallResult(
            boolean failed,
            String status,
            String failureType,
            String mode,
            String stage,
            int attempt,
            String responseType,
            String systemPrompt,
            String userPrompt,
            Long requestCostMs,
            Long readCostMs,
            Long parseCostMs,
            Long totalCostMs,
            Integer contentChars,
            ChatClient.CallResponseSpec response,
            Throwable exception,
            ChapterModelConfigurationSummary configuration
    ) {
        String message = ModelObservabilityLogFormatter.format(
                status,
                mode,
                stage,
                attempt,
                failureType,
                null,
                configuration.metadata(),
                responseType,
                length(systemPrompt),
                length(userPrompt),
                requestCostMs,
                readCostMs,
                parseCostMs,
                null,
                totalCostMs,
                null,
                contentChars,
                finishReason(response),
                usage(response)
        );
        message = withEffectiveConfiguration(message, configuration);
        StructuredModelResponseParser.ParseException parsed = parseException(exception);
        if (parsed != null) {
            message = ModelObservabilityLogFormatter.withLocation(
                    message, parsed.line(), parsed.column()
            );
        }
        writeModelLog(failed, message, exception);
        logSlowCall(
                mode,
                stage,
                attempt,
                responseType,
                length(systemPrompt),
                length(userPrompt),
                requestCostMs,
                readCostMs,
                parseCostMs,
                totalCostMs
        );
    }

    private void logCallResult(
            boolean failed,
            String status,
            String failureType,
            String mode,
            String stage,
            int attempt,
            String responseType,
            String systemPrompt,
            String userPrompt,
            Long requestCostMs,
            Long contentReadMs,
            Long parseCostMs,
            Long firstResponseMs,
            Long ttftMs,
            Long generationMs,
            Long totalCostMs,
            Integer chunkCount,
            Integer contentChars,
            ChatResponse response,
            Throwable exception
    ) {
        logCallResult(
                failed,
                status,
                failureType,
                mode,
                stage,
                attempt,
                responseType,
                systemPrompt,
                userPrompt,
                requestCostMs,
                contentReadMs,
                parseCostMs,
                firstResponseMs,
                ttftMs,
                generationMs,
                totalCostMs,
                chunkCount,
                contentChars,
                response,
                exception,
                chapterConfiguration(stage)
        );
    }

    private void logCallResult(
            boolean failed,
            String status,
            String failureType,
            String mode,
            String stage,
            int attempt,
            String responseType,
            String systemPrompt,
            String userPrompt,
            Long requestCostMs,
            Long contentReadMs,
            Long parseCostMs,
            Long firstResponseMs,
            Long ttftMs,
            Long generationMs,
            Long totalCostMs,
            Integer chunkCount,
            Integer contentChars,
            ChatResponse response,
            Throwable exception,
            ChapterModelConfigurationSummary configuration
    ) {
        String message = ModelObservabilityLogFormatter.format(
                status,
                mode,
                stage,
                attempt,
                failureType,
                null,
                configuration.metadata(),
                responseType,
                length(systemPrompt),
                length(userPrompt),
                requestCostMs,
                null,
                contentReadMs,
                parseCostMs,
                ttftMs,
                generationMs,
                totalCostMs,
                chunkCount,
                contentChars,
                finishReason(response),
                usage(response)
        );
        message = withEffectiveConfiguration(message, configuration);
        message = ModelObservabilityLogFormatter.withStreamMetrics(
                message,
                firstResponseMs,
                ttftMs,
                completionTokens(response)
        );
        StructuredModelResponseParser.ParseException parsed = parseException(exception);
        if (parsed != null) {
            message = ModelObservabilityLogFormatter.withLocation(
                    message, parsed.line(), parsed.column()
            );
        }
        writeModelLog(failed, message, exception);
        logSlowCall(
                mode,
                stage,
                attempt,
                responseType,
                length(systemPrompt),
                length(userPrompt),
                requestCostMs,
                contentReadMs,
                parseCostMs,
                ttftMs,
                generationMs,
                totalCostMs
        );
    }

    private void logStructuredStart(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            String responseType
    ) {
        logStructuredStart(
                systemPrompt,
                userPrompt,
                stage,
                attempt,
                responseType,
                chapterConfiguration(stage)
        );
    }

    private void logStructuredStart(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            String responseType,
            ChapterModelConfigurationSummary configuration
    ) {
        String message = ModelObservabilityLogFormatter.format(
                "start",
                "structured",
                stage,
                attempt,
                null,
                null,
                configuration.metadata(),
                responseType,
                length(systemPrompt),
                length(userPrompt),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        log.info(withEffectiveConfiguration(message, configuration));
    }

    private void logStructuredParseDebug(
            String failureType,
            String raw,
            Throwable exception
    ) {
        StructuredModelResponseParser.ParseException parsed = parseException(exception);
        if (!localStructuredDebug
                || !"JSON_PARSE_FAILED".equals(failureType)
                || parsed == null) {
            return;
        }
        log.debug("[MODEL_RAW] {}", raw);
        log.debug("[MODEL_NORMALIZED] {}", parsed.normalizedResponse());
    }

    private StructuredModelResponseParser.ParseException parseException(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 3; depth++) {
            if (current instanceof StructuredModelResponseParser.ParseException parsed) {
                return parsed;
            }
            current = current.getCause();
        }
        return null;
    }

    private void logStreamResult(
            boolean failed,
            String failureType,
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            long startedAt,
            AtomicLong firstTokenAt,
            AtomicInteger chunkCount,
            AtomicInteger contentLength,
            String finishReason,
            Object usage,
            Throwable exception
    ) {
        long completedAt = System.nanoTime();
        long totalCostMs = elapsedMillis(completedAt, startedAt);
        Long ttftMs = ttftMillis(startedAt, firstTokenAt.get());
        Long generationMs = generationMillis(firstTokenAt.get(), completedAt);
        ChapterModelConfigurationSummary configuration = chapterConfiguration(stage);
        String message = ModelObservabilityLogFormatter.format(
                failed ? "failed" : "success",
                "stream",
                stage,
                attempt,
                failureType,
                null,
                configuration.metadata(),
                "String",
                length(systemPrompt),
                length(userPrompt),
                null,
                null,
                null,
                null,
                ttftMs,
                generationMs,
                totalCostMs,
                chunkCount.get(),
                contentLength.get(),
                finishReason,
                usage
        );
        message = withEffectiveConfiguration(message, configuration);
        writeModelLog(failed, message, exception);
        logStreamSlowCalls(
                stage,
                attempt,
                length(systemPrompt),
                length(userPrompt),
                ttftMs,
                generationMs,
                totalCostMs,
                chunkCount.get(),
                contentLength.get()
        );
    }

    private void writeModelLog(
            boolean failed,
            String message,
            Throwable exception
    ) {
        if (!failed) {
            log.info(message);
            return;
        }
        if (exception == null) {
            log.error(message);
            return;
        }
        log.error(
                "{} exceptionType={} rootCauseType={}",
                message,
                StructuredStreamFailureClassifier.exceptionType(exception),
                StructuredStreamFailureClassifier.rootCauseType(exception)
        );
    }

    private void logSlowCall(
            String mode,
            String stage,
            int attempt,
            String responseType,
            Integer systemChars,
            Integer userChars,
            Long requestCostMs,
            Long readCostMs,
            Long parseCostMs,
            Long totalCostMs
    ) {
        if (totalCostMs != null && totalCostMs >= ModelObservabilityThresholds.SLOW_MODEL_CALL_MS) {
            log.warn(ModelObservabilityLogFormatter.format(
                    "slow",
                    mode,
                    stage,
                    attempt,
                    null,
                    "TOTAL_CALL_SLOW",
                    modelMetadata,
                    responseType,
                    systemChars,
                    userChars,
                    requestCostMs,
                    readCostMs,
                    parseCostMs,
                    null,
                    totalCostMs,
                    null,
                    null,
                    null,
                    null
            ));
        }
    }

    private void logSlowCall(
            String mode,
            String stage,
            int attempt,
            String responseType,
            Integer systemChars,
            Integer userChars,
            Long requestCostMs,
            Long contentReadMs,
            Long parseCostMs,
            Long ttftMs,
            Long generationMs,
            Long totalCostMs
    ) {
        if (totalCostMs != null && totalCostMs >= ModelObservabilityThresholds.SLOW_MODEL_CALL_MS) {
            log.warn(ModelObservabilityLogFormatter.format(
                    "slow",
                    mode,
                    stage,
                    attempt,
                    null,
                    "TOTAL_CALL_SLOW",
                    modelMetadata,
                    responseType,
                    systemChars,
                    userChars,
                    requestCostMs,
                    null,
                    contentReadMs,
                    parseCostMs,
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    null,
                    null,
                    null,
                    null
            ));
        }
    }

    private void logStreamSlowCalls(
            String stage,
            int attempt,
            Integer systemChars,
            Integer userChars,
            Long ttftMs,
            Long generationMs,
            long totalCostMs,
            Integer chunkCount,
            Integer contentChars
    ) {
        if (ttftMs != null && ttftMs >= ModelObservabilityThresholds.SLOW_STREAM_TTFT_MS) {
            log.warn(ModelObservabilityLogFormatter.format(
                    "slow",
                    "stream",
                    stage,
                    attempt,
                    null,
                    "FIRST_TOKEN_SLOW",
                    modelMetadata,
                    "String",
                    systemChars,
                    userChars,
                    null,
                    null,
                    null,
                    ttftMs,
                    null,
                    chunkCount,
                    contentChars,
                    null,
                    null
            ));
        }
        if (totalCostMs >= ModelObservabilityThresholds.SLOW_STREAM_TOTAL_MS) {
            log.warn(ModelObservabilityLogFormatter.format(
                    "slow",
                    "stream",
                    stage,
                    attempt,
                    null,
                    "TOTAL_STREAM_SLOW",
                    modelMetadata,
                    "String",
                    systemChars,
                    userChars,
                    null,
                    null,
                    null,
                    null,
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount,
                    contentChars,
                    null,
                    null
            ));
        }
    }

    private String finishReason(ChatClient.CallResponseSpec response) {
        try {
            if (response == null) {
                return null;
            }
            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse == null || chatResponse.getResult() == null
                    || chatResponse.getResult().getMetadata() == null) {
                return null;
            }
            return chatResponse.getResult().getMetadata().getFinishReason();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String finishReason(ChatResponse response) {
        try {
            if (response == null || response.getResult() == null
                    || response.getResult().getMetadata() == null) {
                return null;
            }
            return response.getResult().getMetadata().getFinishReason();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object usage(ChatClient.CallResponseSpec response) {
        try {
            if (response == null || response.chatResponse() == null
                    || response.chatResponse().getMetadata() == null) {
                return null;
            }
            return response.chatResponse().getMetadata().getUsage();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object usage(ChatResponse response) {
        try {
            return response == null || response.getMetadata() == null
                    ? null
                    : response.getMetadata().getUsage();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer completionTokens(ChatResponse response) {
        Object responseUsage = usage(response);
        if (responseUsage instanceof org.springframework.ai.chat.metadata.Usage usage) {
            return usage.getCompletionTokens();
        }
        return null;
    }

    private ChapterTokenUsage tokenUsage(ChatResponse response) {
        return tokenUsage(usage(response));
    }

    private ChapterTokenUsage tokenUsage(ChatClient.CallResponseSpec response) {
        return tokenUsage(usage(response));
    }

    private ChapterTokenUsage tokenUsage(Object responseUsage) {
        if (!(responseUsage instanceof org.springframework.ai.chat.metadata.Usage usage)) {
            return null;
        }
        return ChapterTokenUsage.of(
                toLong(usage.getPromptTokens()),
                toLong(usage.getCompletionTokens()),
                toLong(usage.getTotalTokens())
        );
    }

    private ChapterTokenUsage plusUsage(
            ChapterTokenUsage accumulated,
            ChapterTokenUsage current
    ) {
        return accumulated == null ? current : accumulated.plus(current);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private void notifyUsage(
            Consumer<ChapterTokenUsage> usageConsumer,
            ChapterTokenUsage usage
    ) {
        if (usageConsumer == null) {
            return;
        }
        try {
            usageConsumer.accept(usage);
        } catch (RuntimeException exception) {
            log.warn("模型 Usage 回调失败，忽略指标回调异常", exception);
        }
    }

    private String streamContent(ChatResponse response) {
        try {
            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                return "";
            }
            String content = response.getResult().getOutput().getText();
            return content == null ? "" : content;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeStage(String stage) {
        return stage == null || stage.isBlank() ? UNKNOWN_STAGE : stage;
    }

    private OpenAiChatOptions.Builder optionsForStage(String stage) {
        return buildOptions(chapterModelProperties.forStage(stage));
    }

    private ModelStageConfig effectiveConfig(
            String stage,
            Boolean reasoningEnabledOverride
    ) {
        ModelStageConfig configured = chapterModelProperties.forStage(stage);
        if (!Boolean.FALSE.equals(reasoningEnabledOverride)) {
            return configured;
        }
        return new ModelStageConfig(
                configured.model(),
                configured.temperature(),
                cn.ninth.novel.infrastructure.config.ReasoningLevel.OFF
        );
    }

    private OpenAiChatOptions.Builder optionsForStage(ModelStageConfig config) {
        return buildOptions(config);
    }

    private String withEffectiveConfiguration(
            String message,
            ChapterModelConfigurationSummary configuration
    ) {
        return ModelObservabilityLogFormatter.withEffectiveConfiguration(
                message,
                configuration.profile(),
                configuration.timeoutMs(),
                configuration.reasoning(),
                configuration.temperature()
        );
    }

    private ChapterModelConfigurationSummary chapterConfiguration(String stage) {
        return chapterConfiguration(stage, chapterModelProperties.forStage(stage));
    }

    private ChapterModelConfigurationSummary chapterConfiguration(
            String stage,
            ModelStageConfig config
    ) {
        String model = config.model() == null || config.model().isBlank()
                ? modelMetadata.modelName()
                : config.model().trim();
        ModelObservabilityMetadata metadata = config.model() == null || config.model().isBlank()
                ? modelMetadata
                : modelMetadata.withModel(model);
        return new ChapterModelConfigurationSummary(
                chapterModelProperties.effectiveProfile(),
                model,
                config.temperature(),
                config.reasoning().name(),
                modelTimeoutProperties.timeoutForStage(stage).toMillis(),
                metadata
        );
    }

    private OpenAiChatOptions.Builder buildOptions(ModelStageConfig config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (config.model() != null && !config.model().isBlank()) {
            builder.model(config.model().trim());
        }
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        DeepSeekReasoningOptions.apply(builder, config.reasoning());
        return builder;
    }

    private int normalizeAttempt(int attempt) {
        return attempt <= 0 ? 1 : attempt;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private long elapsedMillis(long endAt, long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(endAt - startedAt);
    }

    private Long ttftMillis(long startedAt, long firstTokenAt) {
        return firstTokenAt == 0 ? null : elapsedMillis(firstTokenAt, startedAt);
    }

    private Long firstResponseMillis(long startedAt, long firstResponseAt) {
        return firstResponseAt == 0 ? null : elapsedMillis(firstResponseAt, startedAt);
    }

    private Long generationMillis(long firstTokenAt, long completedAt) {
        return firstTokenAt == 0 ? null : elapsedMillis(completedAt, firstTokenAt);
    }

    private record ChapterModelConfigurationSummary(
            String profile,
            String model,
            Double temperature,
            String reasoning,
            Long timeoutMs,
            ModelObservabilityMetadata metadata
    ) {
    }
}
