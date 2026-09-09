package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import cn.ninth.novel.infrastructure.config.ModelStageConfig;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import cn.ninth.novel.types.enums.ResponseCode;
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

@Slf4j
@Service
public class PlanningModelPort implements IPlanningModelPort {

    private static final String UNKNOWN_STAGE = "UNKNOWN";

    private final ChatClient chatClient;
    private final PlanningModelProperties planningModelProperties;
    private final ModelTimeoutProperties modelTimeoutProperties;
    private final ModelObservabilityMetadata modelMetadata;
    private final boolean localStructuredDebug;

    public PlanningModelPort(ChatClient chatClient) {
        this(
                chatClient,
                new PlanningModelProperties(),
                new ModelTimeoutProperties(),
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    PlanningModelPort(ChatClient chatClient, Duration structuredStreamTimeout) {
        this(
                chatClient,
                new PlanningModelProperties(),
                ModelTimeoutProperties.withDefaultTimeout(structuredStreamTimeout),
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    @Autowired
    public PlanningModelPort(
            ChatClient chatClient,
            Environment environment,
            PlanningModelProperties planningModelProperties,
            ModelTimeoutProperties modelTimeoutProperties
    ) {
        this(
                chatClient,
                planningModelProperties,
                modelTimeoutProperties,
                ModelObservabilityMetadata.from(environment),
                environment.acceptsProfiles(Profiles.of("dev", "local"))
        );
    }

    PlanningModelPort(ChatClient chatClient, PlanningModelProperties planningModelProperties) {
        this(
                chatClient,
                planningModelProperties,
                new ModelTimeoutProperties(),
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    PlanningModelPort(
            ChatClient chatClient,
            PlanningModelProperties planningModelProperties,
            ModelTimeoutProperties modelTimeoutProperties
    ) {
        this(
                chatClient,
                planningModelProperties,
                modelTimeoutProperties,
                ModelObservabilityMetadata.unknown(),
                false
        );
    }

    public PlanningModelPort(ChatClient chatClient, Environment environment) {
        this(
                chatClient,
                new PlanningModelProperties(),
                new ModelTimeoutProperties(),
                ModelObservabilityMetadata.from(environment),
                environment.acceptsProfiles(Profiles.of("dev", "local"))
        );
    }

    private PlanningModelPort(
            ChatClient chatClient,
            PlanningModelProperties planningModelProperties,
            ModelTimeoutProperties modelTimeoutProperties,
            ModelObservabilityMetadata modelMetadata,
            boolean localStructuredDebug
    ) {
        this.chatClient = chatClient;
        this.planningModelProperties = Objects.requireNonNull(planningModelProperties);
        this.modelTimeoutProperties = Objects.requireNonNull(modelTimeoutProperties);
        this.modelMetadata = modelMetadata;
        this.localStructuredDebug = localStructuredDebug;
    }

    @Override
    public <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        return call(systemPrompt, userPrompt, responseType, UNKNOWN_STAGE, 1);
    }

    @Override
    public <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt
    ) {
        for (int retry = 0; ; retry++) {
            try {
                return callOnce(systemPrompt, userPrompt, responseType, stage, normalizeAttempt(attempt) + retry);
            } catch (AppException exception) {
                if (retry == 0 && exception.getCause() instanceof StructuredModelResponseParser.ParseException parsed
                        && "JSON_PARSE_FAILED".equals(parsed.failureType())) {
                    continue;
                }
                throw exception;
            }
        }
    }

    private <T> T callOnce(
            String systemPrompt, String userPrompt, Class<T> responseType, String stage, int attempt
    ) {
        String normalizedStage = normalizeStage(stage);
        int normalizedAttempt = normalizeAttempt(attempt);
        String responseTypeName = responseType.getSimpleName();
        long startedAt = System.nanoTime();
        long requestCostMs;
        Flux<ChatResponse> responses;
        AtomicLong firstResponseAt = new AtomicLong();
        AtomicLong firstChunkAt = new AtomicLong();
        AtomicInteger chunkCount = new AtomicInteger();
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();

        logModelStart(
                systemPrompt,
                userPrompt,
                normalizedStage,
                normalizedAttempt,
                responseTypeName
        );

        try {
            var request = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt);
            responses = request
                    .options(optionsForStage(normalizedStage))
                    .stream()
                    .chatResponse();
            if (responses == null) {
                throw new IllegalStateException("模型请求未返回响应流");
            }
            requestCostMs = elapsedMillis(startedAt);
        } catch (Exception exception) {
            requestCostMs = elapsedMillis(startedAt);
            String failureType = StructuredStreamFailureClassifier.classify(exception, false);
            logModelResult(
                    true,
                    "failed",
                    failureType,
                    systemPrompt,
                    userPrompt,
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
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
            logModelResult(
                    true,
                    "failed",
                    failureType,
                    systemPrompt,
                    userPrompt,
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
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
                    exception
            );
            throw modelRequestFailure(failureType, exception);
        }

        long completedAt = System.nanoTime();
        long contentReadMs = elapsedMillis(completedAt, streamStartedAt);
        long totalCostMs = elapsedMillis(completedAt, startedAt);
        Long ttftMs = ttftMillis(startedAt, firstChunkAt.get());
        Long generationMs = generationMillis(firstChunkAt.get(), completedAt);
        String content = contentBuilder.toString();
        if (content == null || content.isBlank()) {
            AppException emptyResponse = AppException.user(
                    ResponseCode.E0001.getCode(),
                    ResponseCode.E0001.getMessage()
            );
            logModelResult(
                    true,
                    "failed",
                    "EMPTY_RESPONSE",
                    systemPrompt,
                    userPrompt,
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    requestCostMs,
                    contentReadMs,
                    null,
                    firstResponseMillis(startedAt, firstResponseAt.get()),
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount.get(),
                    contentBuilder.length(),
                    lastResponse.get(),
                    emptyResponse
            );
            throw emptyResponse;
        }

        long parseStartedAt = System.nanoTime();
        String failureType = "SCHEMA_VALIDATION_FAILED";
        try {
            T result = StructuredModelResponseParser.parse(content, responseType);
            StructuredModelOutputValidator.validate(result);
            long parseCostMs = elapsedMillis(parseStartedAt);
            totalCostMs = elapsedMillis(startedAt);
            logModelResult(
                    false,
                    "success",
                    null,
                    systemPrompt,
                    userPrompt,
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    requestCostMs,
                    contentReadMs,
                    parseCostMs,
                    firstResponseMillis(startedAt, firstResponseAt.get()),
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount.get(),
                    content.length(),
                    lastResponse.get(),
                    null
            );
            return result;
        } catch (Exception exception) {
            if (exception instanceof StructuredModelResponseParser.ParseException parsed) {
                failureType = parsed.failureType();
            }
            long parseCostMs = elapsedMillis(parseStartedAt);
            totalCostMs = elapsedMillis(startedAt);
            logModelResult(
                    true,
                    "parse failed",
                    failureType,
                    systemPrompt,
                    userPrompt,
                    normalizedStage,
                    normalizedAttempt,
                    responseTypeName,
                    requestCostMs,
                    contentReadMs,
                    parseCostMs,
                    firstResponseMillis(startedAt, firstResponseAt.get()),
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    chunkCount.get(),
                    content.length(),
                    lastResponse.get(),
                    exception
            );
            logStructuredParseDebug(failureType, content, exception);
            throw AppException.internal(
                    ResponseCode.E0007.getCode(),
                    failureType + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    private void logModelResult(
            boolean failed,
            String event,
            String failureType,
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            String responseType,
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
            Exception exception
    ) {
        PlanningModelConfigurationSummary configuration = planningConfiguration(stage);
        String message = ModelObservabilityLogFormatter.format(
                event,
                "structured",
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
        message = ModelObservabilityLogFormatter.withEffectiveConfiguration(
                message,
                configuration.profile(),
                configuration.timeoutMs(),
                configuration.reasoning(),
                configuration.temperature()
        );
        message = ModelObservabilityLogFormatter.withStreamMetrics(
                message,
                firstResponseMs,
                ttftMs,
                completionTokens(response)
        );
        if (exception instanceof StructuredModelResponseParser.ParseException parsed) {
            message = ModelObservabilityLogFormatter.withLocation(
                    message, parsed.line(), parsed.column()
            );
        }
        writeModelLog(failed, message, exception);
        logSlowCall(
                stage,
                attempt,
                responseType,
                requestCostMs,
                contentReadMs,
                parseCostMs,
                firstResponseMs,
                ttftMs,
                generationMs,
                totalCostMs,
                response,
                configuration
        );
    }

    private void logModelStart(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            String responseType
    ) {
        PlanningModelConfigurationSummary configuration = planningConfiguration(stage);
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
        log.info(ModelObservabilityLogFormatter.withEffectiveConfiguration(
                message,
                configuration.profile(),
                configuration.timeoutMs(),
                configuration.reasoning(),
                configuration.temperature()
        ));
    }

    private void logStructuredParseDebug(
            String failureType,
            String raw,
            Exception exception
    ) {
        if (!localStructuredDebug
                || !"JSON_PARSE_FAILED".equals(failureType)
                || !(exception instanceof StructuredModelResponseParser.ParseException parsed)) {
            return;
        }
        log.debug("[MODEL_RAW] {}", raw);
        log.debug("[MODEL_NORMALIZED] {}", parsed.normalizedResponse());
    }

    private String finishReason(ChatResponse response) {
        try {
            if (response == null) {
                return null;
            }
            if (response.getResult() == null || response.getResult().getMetadata() == null) {
                return null;
            }
            return response.getResult().getMetadata().getFinishReason();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object usage(ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null) {
                return null;
            }
            return response.getMetadata().getUsage();
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

    private void logSlowCall(
            String stage,
            int attempt,
            String responseType,
            Long requestCostMs,
            Long contentReadMs,
            Long parseCostMs,
            Long firstResponseMs,
            Long ttftMs,
            Long generationMs,
            Long totalCostMs,
            ChatResponse response,
            PlanningModelConfigurationSummary configuration
    ) {
        if (totalCostMs != null && totalCostMs >= ModelObservabilityThresholds.SLOW_MODEL_CALL_MS) {
            String message = ModelObservabilityLogFormatter.format(
                    "slow",
                    "structured",
                    stage,
                    attempt,
                    null,
                    "TOTAL_CALL_SLOW",
                    configuration.metadata(),
                    responseType,
                    null,
                    null,
                    requestCostMs,
                    null,
                    contentReadMs,
                    parseCostMs,
                    ttftMs,
                    generationMs,
                    totalCostMs,
                    null,
                    null,
                    finishReason(response),
                    usage(response)
            );
            message = ModelObservabilityLogFormatter.withEffectiveConfiguration(
                    message,
                    configuration.profile(),
                    configuration.timeoutMs(),
                    configuration.reasoning(),
                    configuration.temperature()
            );
            log.warn(ModelObservabilityLogFormatter.withStreamMetrics(
                    message,
                    firstResponseMs,
                    ttftMs,
                    completionTokens(response)
            ));
        }
    }

    private void writeModelLog(boolean failed, String message, Exception exception) {
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

    private AppException modelRequestFailure(String failureType, Throwable cause) {
        String message = failureType != null && failureType.endsWith("_TIMEOUT")
                ? "模型响应超时，请重试"
                : ResponseCode.E0001.getMessage();
        return AppException.user(ResponseCode.E0001.getCode(), message, cause);
    }

    private String normalizeStage(String stage) {
        return stage == null || stage.isBlank() ? UNKNOWN_STAGE : stage;
    }

    private OpenAiChatOptions.Builder optionsForStage(String stage) {
        return buildOptions(planningModelProperties.forStage(stage));
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

    private PlanningModelConfigurationSummary planningConfiguration(String stage) {
        PlanningModelProperties.PlanningModelProfile profile =
                PlanningModelProperties.profileForStage(stage);
        ModelStageConfig config = planningModelProperties.forStage(stage);
        if (config.model() == null || config.model().isBlank()) {
            return new PlanningModelConfigurationSummary(
                    profile.name(),
                    modelMetadata.modelName(),
                    config.temperature(),
                    config.reasoning().name(),
                    timeoutMs(stage),
                    modelMetadata
            );
        }
        String model = config.model() == null || config.model().isBlank()
                ? modelMetadata.modelName()
                : config.model().trim();
        return new PlanningModelConfigurationSummary(
                profile.name(),
                model,
                config.temperature(),
                config.reasoning().name(),
                timeoutMs(stage),
                modelMetadata.withModel(model)
        );
    }

    private Long timeoutMs(String stage) {
        return modelTimeoutProperties.timeoutForStage(stage).toMillis();
    }

    private record PlanningModelConfigurationSummary(
            String profile,
            String model,
            Double temperature,
            String reasoning,
            Long timeoutMs,
            ModelObservabilityMetadata metadata
    ) {
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

    private Long firstResponseMillis(long startedAt, long firstResponseAt) {
        return firstResponseAt == 0 ? null : elapsedMillis(firstResponseAt, startedAt);
    }

    private Long ttftMillis(long startedAt, long firstChunkAt) {
        return firstChunkAt == 0 ? null : elapsedMillis(firstChunkAt, startedAt);
    }

    private Long generationMillis(long firstChunkAt, long completedAt) {
        return firstChunkAt == 0 ? null : elapsedMillis(completedAt, firstChunkAt);
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
}
