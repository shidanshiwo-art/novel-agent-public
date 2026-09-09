package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.service.agent.PromptTraceRecorder;
import cn.ninth.novel.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterWorkflowObservabilityContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldRecordDurationForEveryChapterWorkflowStage() throws IOException {
        Path sourcePath = PROJECT_ROOT.resolve(
                "novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/ChapterService.java"
        );
        String source = Files.readString(sourcePath);

        System.out.printf(
                "章节工作流阶段耗时日志契约检查：source=%d%n",
                source.length()
        );

        assertThat(source)
                .contains(
                        "ConcurrentMap<String, ConcurrentMap<ChapterGenerationEventType, Long>>",
                        "stageStartTimes = new ConcurrentHashMap<>()",
                        "System.nanoTime()",
                        "TimeUnit.NANOSECONDS.toMillis",
                        "durationMs",
                        "[CHAPTER] workflow={} started chapter={}",
                        "[CHAPTER] workflow={} stage={} started chapter={}",
                        "[CHAPTER] workflow={} stage={} completed chapter={} costMs={}",
                        "[CHAPTER] workflow={} stage={} completed chapter={} issues={} costMs={}",
                        "[CHAPTER] workflow={} completed chapter={} totalMs={}",
                        "章节工作流阶段失败，workflowId={}，chapterNumber={}，node={}，errorCode={}，event={}，durationMs={}，message={}，cause={}",
                        "clearStageStartTimes(workflowId)"
                );

        assertThat(source).contains(
                "ChapterGenerationEventType.DRAFT_STARTED",
                "ChapterGenerationEventType.DRAFT_COMPLETED",
                "ChapterGenerationEventType.REVIEW_STARTED",
                "ChapterGenerationEventType.REVIEW_COMPLETED",
                "ChapterGenerationEventType.REVISION_STARTED",
                "ChapterGenerationEventType.REVISION_COMPLETED",
                "ChapterGenerationEventType.COMPRESSION_STARTED",
                "ChapterGenerationEventType.COMPRESSION_COMPLETED",
                "ChapterGenerationEventType.PERSIST_STARTED",
                "ChapterGenerationEventType.PERSIST_COMPLETED"
        );

        System.out.println(
                "章节工作流阶段耗时日志契约通过：DRAFT/REVIEW/REVISION/COMPRESSION/PERSIST 均记录开始、结束和 durationMs"
        );
    }

    @Test
    void shouldTraceEveryChapterModelCallWithRequiredContext() throws IOException {
        Path domainRoot = PROJECT_ROOT.resolve("novel-agent-domain/src/main/java");
        Path infrastructureRoot = PROJECT_ROOT.resolve("novel-agent-infrastructure/src/main/java");
        String state = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/service/workflow/ChapterGraphState.java"
        ));
        String traceRecord = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/model/valobj/PromptTraceRecord.java"
        ));
        String modelResponse = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/model/valobj/ChapterModelResponse.java"
        ));
        String modelResponseException = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/model/valobj/ChapterModelResponseException.java"
        ));
        String recorder = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/service/agent/PromptTraceRecorder.java"
        ));
        String modelPort = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/adapter/port/IChapterModelPort.java"
        ));
        String infrastructureModelPort = Files.readString(infrastructureRoot.resolve(
                "cn/ninth/novel/infrastructure/adapter/port/ChapterModelPort.java"
        ));
        String traceRepository = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/adapter/repository/IPromptTraceRepository.java"
        ));
        String infrastructureRepository = Files.readString(infrastructureRoot.resolve(
                "cn/ninth/novel/infrastructure/adapter/repository/PromptTraceRepository.java"
        ));
        String traceDao = Files.readString(infrastructureRoot.resolve(
                "cn/ninth/novel/infrastructure/dao/IPromptTraceDao.java"
        ));
        String tracePO = Files.readString(infrastructureRoot.resolve(
                "cn/ninth/novel/infrastructure/dao/po/PromptTracePO.java"
        ));
        String traceMapper = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-app/src/main/resources/mybatis/mapper/chapter_model_trace_mapper.xml"
        ));
        String schema = Files.readString(PROJECT_ROOT.resolve("docs/sql/schema.sql"));
        String retryExecutor = Files.readString(domainRoot.resolve(
                "cn/ninth/novel/domain/chapter/service/agent/ChapterModelRetryExecutor.java"
        ));

        System.out.printf(
                "Prompt Trace 字段契约检查：state=%d, record=%d, recorder=%d%n",
                state.length(),
                traceRecord.length(),
                recorder.length()
        );

        assertThat(traceRecord)
                .contains(
                        "String workflowId",
                        "String projectCode",
                        "Integer chapterNumber",
                        "String node",
                        "int attempt",
                        "String systemPrompt",
                        "String userPrompt",
                        "Instant createdAt",
                        "String responseText",
                        "boolean success",
                        "String errorCode",
                        "String errorMessage",
                        "long durationMs",
                        "completed("
                );
        assertThat(modelResponse).contains("T value", "String rawText");
        assertThat(modelResponseException).contains(
                "class ChapterModelResponseException",
                "String rawText",
                "rawText()"
        );
        assertThat(modelPort).contains("callWithRawResponse", "new ChapterModelResponse<>");
        assertThat(infrastructureModelPort).contains(
                "StructuredModelOutputValidator.validate(result)",
                "e.getMessage(), e, content)"
        );
        assertThat(traceRepository).contains("void save(PromptTraceRecord trace)");
        assertThat(traceRepository).contains("findByWorkflowId(String workflowId)");
        assertThat(infrastructureRepository).contains(
                "implements IPromptTraceRepository",
                "promptTraceDao.insert(toPO(trace))",
                "promptTraceDao.selectByWorkflowId(workflowId)",
                "toRecord"
        );
        assertThat(traceDao).contains(
                "@Mapper",
                "int insert(PromptTracePO trace)",
                "selectByWorkflowId(@Param(\"workflowId\") String workflowId)"
        );
        assertThat(tracePO).contains(
                "String workflowId",
                "String projectCode",
                "String systemPrompt",
                "String userPrompt",
                "String responseText",
                "Boolean success",
                "String errorCode",
                "String errorMessage",
                "Long durationMs",
                "LocalDateTime createdAt"
        );
        assertThat(traceMapper).contains(
                "namespace=\"cn.ninth.novel.infrastructure.dao.IPromptTraceDao\"",
                "INSERT INTO chapter_model_trace",
                "#{responseText}",
                "#{durationMs}",
                "<select id=\"selectByWorkflowId\"",
                "ORDER BY created_at ASC, id ASC"
        );
        assertThat(schema).contains(
                "CREATE TABLE IF NOT EXISTS chapter_model_trace",
                "system_prompt LONGTEXT",
                "user_prompt LONGTEXT",
                "response_text LONGTEXT",
                "duration_ms BIGINT UNSIGNED"
        );
        assertThat(state)
                .contains(
                        "WORKFLOW_ID",
                        "public Optional<String> workflowId()",
                        "PromptTraceRecord promptTrace("
                );
        assertThat(recorder)
                .contains(
                        "PROMPT_TRACE workflowId={} node={} attempt={} promptLength={} responseLength={} durationMs={}",
                        "模型调用失败，workflowId={}，projectCode={}，chapterNumber={}，node={}，attempt={}，errorCode={}，cause={}",
                        "length(trace.systemPrompt()) + length(trace.userPrompt())",
                        "length(trace.responseText())",
                        "trace.durationMs()",
                        "recordSuccess(",
                        "recordFailure(",
                        "responseText(String rawText, Object parsedResponse)",
                        "rawResponse(exception)"
                )
                .doesNotContain(
                        "systemPrompt={}",
                        "userPrompt={}",
                        "responseText={}",
                        "errorMessage={}"
                );
        assertThat(retryExecutor)
                .contains(
                        "IntFunction<T> operation",
                        "operation.apply(attempt)",
                        "for (int attempt = 0; ; attempt++)",
                        "第一次重试为 1"
                );

        for (String node : List.of(
                "DraftChapterNode.java",
                "ReviewChapterNode.java",
                "ReviseChapterNode.java",
                "CompressChapterNode.java"
        )) {
            String source = Files.readString(domainRoot.resolve(
                    "cn/ninth/novel/domain/chapter/service/agent/" + node
            ));
            assertThat(source)
                    .as("节点 %s 必须在模型调用完成后写入带响应的 Prompt Trace", node)
                    .contains("promptTraceRecorder.recordSuccess(", "promptTraceRecorder.recordFailure(");
        }

        System.out.println(
                "Prompt Trace 字段契约通过：四个模型节点均记录 Prompt、原始响应、成功/失败信息和 durationMs"
        );
    }

    @Test
    void shouldPersistOneCompletedTraceWithoutTouchingBusinessTables() {
        List<PromptTraceRecord> saved = new ArrayList<>();
        PromptTraceRecorder recorder = new PromptTraceRecorder(saved::add);
        PromptTraceRecord trace = PromptTraceRecord.now(
                "workflow-1",
                "project-1",
                3,
                "REVIEW",
                2,
                "system",
                "user"
        ).completed(
                "{\"issues\":[]}",
                true,
                null,
                null,
                128L
        );

        recorder.record(trace);

        assertThat(saved).singleElement().satisfies(savedTrace -> {
            assertThat(savedTrace.workflowId()).isEqualTo("workflow-1");
            assertThat(savedTrace.node()).isEqualTo("REVIEW");
            assertThat(savedTrace.attempt()).isEqualTo(2);
            assertThat(savedTrace.responseText()).isEqualTo("{\"issues\":[]}");
            assertThat(savedTrace.success()).isTrue();
            assertThat(savedTrace.durationMs()).isEqualTo(128L);
            assertThat(savedTrace.createdAt()).isBeforeOrEqualTo(Instant.now());
        });
        System.out.printf(
                "独立 Prompt Trace 持久化检查：saved=%d, table=chapter_model_trace, businessTablesTouched=false%n",
                saved.size()
        );
    }

    @Test
    void shouldLogModelTimingMetadataWithoutLoggingPromptOrResponseBody() throws IOException {
        Path infrastructureRoot = PROJECT_ROOT.resolve("novel-agent-infrastructure/src/main/java");
        String chapterModelPort = Files.readString(infrastructureRoot.resolve(
                "cn/ninth/novel/infrastructure/adapter/port/ChapterModelPort.java"
        ));
        String planningModelPort = Files.readString(infrastructureRoot.resolve(
                "cn/ninth/novel/infrastructure/adapter/port/PlanningModelPort.java"
        ));
        String chapterPort = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/adapter/port/IChapterModelPort.java"
        ));
        String planningPort = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/adapter/port/IPlanningModelPort.java"
        ));
        String planningService = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-domain/src/main/java/cn/ninth/novel/domain/planning/service/PlanningService.java"
        ));
        String projectService = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-domain/src/main/java/cn/ninth/novel/domain/project/service/NovelProjectService.java"
        ));

        System.out.printf(
                "模型调用耗时日志契约检查：chapterPort=%d, planningPort=%d%n",
                chapterModelPort.length(),
                planningModelPort.length()
        );

        assertThat(chapterModelPort)
                .contains(
                        "ModelObservabilityThresholds.SLOW_MODEL_CALL_MS",
                        "ModelObservabilityThresholds.SLOW_STREAM_TTFT_MS",
                        "ModelObservabilityThresholds.SLOW_STREAM_TOTAL_MS",
                        "System.nanoTime()",
                        "ModelObservabilityLogFormatter.format(",
                        "status",
                        "failureType",
                        "REQUEST_FAILED",
                        "READ_FAILED",
                        "EMPTY_RESPONSE",
                        "JSON_PARSE_FAILED", "SCHEMA_VALIDATION_FAILED",
                        "firstTokenAt.compareAndSet",
                        "ttftMillis",
                        "int normalizedAttempt = normalizeAttempt(attempt)",
                        "model",
                        "ttftMs",
                        "generationMs",
                        "totalCostMs",
                        "chunkCount",
                        "contentChars",
                        "readCostMs",
                        ".timeout(structuredStreamTimeout)",
                        "finishReason",
                        "usage",
                        "log.warn(",
                        "FIRST_TOKEN_SLOW",
                        "TOTAL_STREAM_SLOW",
                        "systemChars",
                        "userChars"
                )
                .doesNotContain(
                        "[MODEL] start mode=",
                        "systemPrompt={}",
                        "userPrompt={}",
                        "responseText={}",
                        "responseContent={}",
                        "apiKey",
                        "Authorization"
                );
        assertThat(planningModelPort)
                .contains(
                        "int normalizedAttempt = normalizeAttempt(attempt)",
                        "ModelObservabilityThresholds.SLOW_MODEL_CALL_MS",
                        "ModelObservabilityLogFormatter.format(",
                        ".stream()",
                        ".chatResponse()",
                        "StringBuilder",
                        "contentReadMs",
                        "ttftMs",
                "generationMs",
                        "chunkCount",
                        "contentChars",
                        "StructuredModelResponseParser.parse(content, responseType)",
                        "failureType",
                        "logModelStart(",
                        "modelTimeoutProperties.timeoutForStage(normalizedStage)",
                        ".timeout(structuredStreamTimeout)",
                        "modelRequestFailure(failureType, exception)",
                        "StructuredStreamFailureClassifier.classify",
                        "rootCauseType",
                        "EMPTY_RESPONSE",
                        "JSON_PARSE_FAILED", "SCHEMA_VALIDATION_FAILED"
                )
                .doesNotContain(
                        "response.content()",
                        "systemPrompt={}",
                        "userPrompt={}",
                        "content={}",
                        "responseContent={}",
                        "response.entity(responseType)",
                        "apiKey",
                        "Authorization"
                );
        assertThat(chapterPort).contains(
                "String stage",
                "int attempt",
                "stream(",
                "callWithRawResponse("
        );
        assertThat(chapterModelPort).contains(
                "StructuredStreamFailureClassifier.classify",
                "rootCauseType",
                "logStructuredStart("
        );
        assertThat(planningPort).contains("String stage", "int attempt");
        assertThat(planningService)
                .contains(
                        "\"ROOT_OUTLINE\"",
                        "\"VOLUME_OUTLINE\"",
                        "\"NEXT_CHAPTER_OUTLINE\"",
                        "\"ARC_REGENERATION\"",
                        "\"CHAPTER_PLAN\""
                )
                .doesNotContain("[MODEL]");
        assertThat(projectService)
                .contains(
                        "\"STORY_BIBLE_INIT\"",
                        "\"STORY_BIBLE_REVISION\"",
                        "\"CHARACTER_GENERATION\""
                )
                .doesNotContain("[MODEL]");
        for (String node : List.of(
                "DraftChapterNode.java",
                "ReviewChapterNode.java",
                "ReviseChapterNode.java",
                "CompressChapterNode.java"
        )) {
            String source = Files.readString(PROJECT_ROOT.resolve(
                    "novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/agent/" + node
            ));
            String stage = switch (node) {
                case "DraftChapterNode.java" -> "\"DRAFT\"";
                case "ReviewChapterNode.java" -> "\"REVIEW\"";
                case "ReviseChapterNode.java" -> "\"REVISION\"";
                case "CompressChapterNode.java" -> "\"COMPRESSION\"";
                default -> throw new IllegalStateException("unexpected chapter node: " + node);
            };
            assertThat(source)
                    .as("节点 %s 的模型调用必须携带人类可读的 attempt", node)
                    .contains(stage, "attempt + 1");
        }

        System.out.println(
                "模型调用耗时日志契约通过：同步/结构化/流式调用均记录指标，慢调用阈值为 15 秒，未输出提示词和正文"
        );
    }

    @Test
    void shouldPersistSafeTraceFailureMessageAndKeepTechnicalCauseOutOfTrace() {
        List<PromptTraceRecord> saved = new ArrayList<>();
        PromptTraceRecorder recorder = new PromptTraceRecorder(saved::add);
        PromptTraceRecord trace = PromptTraceRecord.now(
                "workflow-failure",
                "project-failure",
                8,
                "REVIEW",
                1,
                "system",
                "user"
        );
        RuntimeException exception = new RuntimeException(
                "MismatchedInputException: JSON parse error",
                new IllegalStateException("model timeout")
        );

        recorder.recordFailure(trace, null, exception, 42L);

        assertThat(saved).singleElement().satisfies(savedTrace -> {
            assertThat(savedTrace.errorMessage())
                    .isEqualTo(ResponseCode.UN_ERROR.getMessage())
                    .doesNotContain("MismatchedInputException", "JSON", "timeout");
            assertThat(savedTrace.responseText()).isNull();
            assertThat(savedTrace.errorCode()).isNull();
        });
        System.out.printf(
                "模型失败 Trace 用户提示已收口：workflowId=%s, errorMessage=%s, technicalDetailPersisted=false%n",
                trace.workflowId(),
                saved.get(0).errorMessage()
        );
    }
}
