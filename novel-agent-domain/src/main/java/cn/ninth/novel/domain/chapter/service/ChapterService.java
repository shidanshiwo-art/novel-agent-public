package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.service.agent.DraftChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.CompressChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.PersistChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviewChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviseChapterNode;
import cn.ninth.novel.domain.chapter.service.data.IDataService;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionEvent;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionRegistry;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter;
import cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter.HUMAN;
import static cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter.PASS;
import static cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter.REVISE;
import static cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter.COMPRESSION;
import static cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter.ABORTED;
/**
 * ChapterService
 *
 * @author ninth
 * @date 2026/8/24
 * @description
 */
@Service
@Slf4j
public class ChapterService implements  IChapterService {

    private final IDataService dataService;
    private final INovelProjectRepository projectRepository;
    private final CompressChapterNode compressChapterNode;
    private final IChapterPersistRepository chapterPersistRepository;
    private final ChapterGenerationSessionRegistry sessionRegistry;
    private final ChapterGenerationMetricsRecorder metricsRecorder;
    private final BaseCheckpointSaver checkpointSaver;
    private final CompiledGraph<ChapterGraphState> chapterGraph;
    private final ConcurrentMap<String, ConcurrentMap<ChapterGenerationEventType, Long>>
            stageStartTimes = new ConcurrentHashMap<>();

    public ChapterService(
            IDataService dataService,
            INovelProjectRepository projectRepository,
            DraftChapterNode draftChapterNode,
            ReviewChapterNode reviewChapterNode,
            ReviseChapterNode reviseChapterNode,
            ReviewRouter reviewRouter,
            CompressChapterNode compressChapterNode,
            PersistChapterNode persistChapterNode,
            IChapterPersistRepository chapterPersistRepository,
            ChapterGenerationSessionRegistry sessionRegistry,
            HumanDecisionRouter humanDecisionRouter,
            BaseCheckpointSaver checkpointSaver,
            StateSerializer<ChapterGraphState> stateSerializer
    ) {
        this(
                dataService,
                projectRepository,
                draftChapterNode,
                reviewChapterNode,
                reviseChapterNode,
                reviewRouter,
                compressChapterNode,
                persistChapterNode,
                chapterPersistRepository,
                sessionRegistry,
                new ChapterGenerationMetricsRecorder(),
                humanDecisionRouter,
                checkpointSaver,
                stateSerializer
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChapterService(
            IDataService dataService,
            INovelProjectRepository projectRepository,
            DraftChapterNode draftChapterNode,
            ReviewChapterNode reviewChapterNode,
            ReviseChapterNode reviseChapterNode,
            ReviewRouter reviewRouter,
            CompressChapterNode compressChapterNode,
            PersistChapterNode persistChapterNode,
            IChapterPersistRepository chapterPersistRepository,
            ChapterGenerationSessionRegistry sessionRegistry,
            ChapterGenerationMetricsRecorder metricsRecorder,
            HumanDecisionRouter humanDecisionRouter,
            BaseCheckpointSaver checkpointSaver,
            StateSerializer<ChapterGraphState> stateSerializer
    ) {
        this.dataService = dataService;
        this.projectRepository = projectRepository;
        this.compressChapterNode = compressChapterNode;
        this.chapterPersistRepository = chapterPersistRepository;
        this.sessionRegistry = sessionRegistry;
        this.metricsRecorder = metricsRecorder;
        this.checkpointSaver = checkpointSaver;
        this.chapterGraph = buildChapterGraph(
                draftChapterNode,
                reviewChapterNode,
                reviseChapterNode,
                reviewRouter,
                compressChapterNode,
                persistChapterNode,
                humanDecisionRouter,
                checkpointSaver,
                stateSerializer
        );
    }
    @Override
    public ChapterGenerationResultVO generateChapter(String projectCode,
                                                     int chapterNumber) {
        String workflowId = UUID.randomUUID().toString();
        return executeChapterWorkflow(workflowId, projectCode, chapterNumber);
    }

    @Override
    public String createGenerationSession(String projectCode, int chapterNumber) {
        String workflowId = UUID.randomUUID().toString();
        sessionRegistry.register(workflowId, projectCode, chapterNumber);
        CompletableFuture.runAsync(() -> {
            try {
                executeChapterWorkflow(workflowId, projectCode, chapterNumber);
            } catch (RuntimeException exception) {
                log.error(
                        "异步章节生成工作流执行失败，workflowId={}, projectCode={}, chapterNumber={}",
                        workflowId,
                        projectCode,
                        chapterNumber,
                        exception
                );
            }
        });
        return workflowId;
    }

    @Override
    public Optional<ChapterGenerationSessionSnapshot> findActiveGenerationSession(
            String projectCode,
            int chapterNumber
    ) {
        return sessionRegistry.findRecoverableSession(projectCode, chapterNumber);
    }

    @Override
    public List<GenerationMetricsDO> findGenerationMetrics(
            String projectCode,
            int chapterNumber
    ) {
        return metricsRecorder.findByChapter(projectCode, chapterNumber);
    }

    @Override
    public GenerationMetricsSummaryVO summarizeGenerationMetrics(String projectCode) {
        return metricsRecorder.summarizeByProject(projectCode);
    }

    @Override
    public GenerationMetricsDO findGenerationMetrics(
            String projectCode,
            int chapterNumber,
            String generationSessionId
    ) {
        return metricsRecorder.find(projectCode, chapterNumber, generationSessionId);
    }

    @Override
    public void stopGenerationSession(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw illegalParameter("章节生成会话标识不能为空");
        }
        ChapterGenerationSessionRegistry.StopResult result =
                sessionRegistry.requestStop(workflowId);
        if (!result.found()) {
            throw illegalParameter("章节生成会话不存在");
        }
        if (result.accepted() && !result.executionActive()) {
            // REVIEW_FAILED 已经是暂停态，原工作流 finally 已结束；STOP 需要在命令边界释放其保留的资源。
            releaseWorkflowResources(workflowId);
        }
        if (!result.accepted()
                && result.status() != ChapterWorkflowStatusEnum.CANCELLED) {
            throw illegalParameter("当前状态不允许停止章节生成");
        }
    }

    @Override
    public void acceptGenerationSession(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw illegalParameter("章节生成会话标识不能为空");
        }
        ChapterGenerationSessionRegistry.AcceptResult result =
                sessionRegistry.requestAccept(workflowId);
        if (!result.found()) {
            throw illegalParameter("章节生成会话不存在");
        }
        if (!result.accepted() && !result.alreadyAccepted()) {
            throw illegalParameter("当前状态不允许采用当前正文");
        }
        if (result.accepted()
                && (result.status() == ChapterWorkflowStatusEnum.WAITING_HUMAN
                || result.status() == ChapterWorkflowStatusEnum.REVIEW_FAILED)) {
            sessionRegistry.publish(
                    workflowId,
                    ChapterGenerationEventType.COMPRESSION_STARTED
            );
            CompletableFuture.runAsync(() -> {
                try {
                    resumeChapter(workflowId, HumanDecisionEnum.PASS);
                } catch (RuntimeException exception) {
                    log.error(
                            "ACCEPT 恢复章节生成工作流失败，workflowId={}",
                            workflowId,
                            exception
                    );
                }
            });
        }
    }

    private ChapterGenerationResultVO executeChapterWorkflow(
            String workflowId,
            String projectCode,
            int chapterNumber
    ) {
        long workflowStartedAt = System.nanoTime();
        boolean keepCheckpointForHuman = false;
        try {
            metricsRecorder.startSession(workflowId, projectCode, chapterNumber);
            sessionRegistry.publish(
                    workflowId,
                    ChapterGenerationEventType.GENERATION_STARTED
            );
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(workflowId)
                    .build();
            log.info(
                    "[CHAPTER] workflow={} started chapter={}",
                    workflowId,
                    chapterNumber
            );
            NodeOutput<ChapterGraphState> output = chapterGraph.invokeFinal(
                    GraphInput.args(Map.of(
                            ChapterGraphKeys.PROJECT_CODE, projectCode,
                            ChapterGraphKeys.CHAPTER_NUMBER, chapterNumber,
                            ChapterGraphKeys.WORKFLOW_ID, workflowId
                    )),
                    config
            ).orElseThrow(()->workflowException("章节生成工作流未返回最终状态"));
            ChapterGenerationResultVO result = toGenerationResult(workflowId, output);
            updateSessionSnapshot(result);
            publishWorkflowResultEvents(workflowId, output, result);
            if (output.isEND()) {
                metricsRecorder.finishSession(
                        workflowId,
                        projectCode,
                        chapterNumber,
                        result.content()
                );
            }
            keepCheckpointForHuman = !output.isEND();
            if (result.status() == ChapterWorkflowStatusEnum.COMPLETED) {
                log.info(
                        "[CHAPTER] workflow={} completed chapter={} totalMs={}",
                        workflowId,
                        chapterNumber,
                        elapsedMillis(workflowStartedAt)
                );
            }
            return result;
        } catch (Throwable exception) {
            if (isCancellation(exception)) {
                metricsRecorder.finishSession(
                        workflowId, projectCode, chapterNumber, null);
                return cancelledResult(workflowId, projectCode, chapterNumber);
            }
            metricsRecorder.finishSession(
                    workflowId, projectCode, chapterNumber, null);
            AppException appException = unwrapAppException(exception);
            sessionRegistry.publish(
                    workflowId,
                    ChapterGenerationEventType.GENERATION_FAILED,
                    readableFailureMessage(appException != null ? appException : exception)
            );
            // 节点抛出的 AppException 可能被 langgraph 异步层包装成 CompletionException 等，
            // 需从 cause 链中还原，避免业务错误码被吞成 UN_ERROR
            if (appException != null) {
                throw appException;
            }
            // 将第三方框架等未受控异常转换为统一异常
            if (exception instanceof Error error) {
                throw error;
            }
            throw workflowException("章节生成工作流执行失败", exception);
        } finally {
            clearStageStartTimes(workflowId);
            if (!keepCheckpointForHuman) {
                releaseWorkflowResources(workflowId);
            }
        }
    }

    @Override
    public ChapterGenerationResultVO resyncChapterDerivedData(
            String projectCode,
            int chapterNumber
    ) {
        String workflowId = UUID.randomUUID().toString();
        try {
            GeneratedChapterVO chapter = projectRepository.findChapter(
                    projectCode, chapterNumber);
            if (chapter == null) {
                throw illegalParameter("章节不存在，chapterNumber=" + chapterNumber);
            }
            if (!"DIRTY".equalsIgnoreCase(chapter.status())) {
                throw illegalParameter(
                        "正文派生数据同步仅允许 DIRTY 章节，chapterNumber="
                                + chapterNumber + "，status=" + chapter.status());
            }
            if (chapter.content() == null || chapter.content().isBlank()) {
                throw illegalParameter("正文派生数据同步对应的章节正文为空");
            }

            ChapterContextAggregate context = dataService.loadContext(
                    projectCode, chapterNumber);
            validateContextForDerivedDataSync(context, chapterNumber);

            Map<String, Object> chapterMemoryUpdate = compressChapterNode.apply(
                    new ChapterGraphState(Map.of(
                            ChapterGraphKeys.PROJECT_CODE, projectCode,
                            ChapterGraphKeys.CHAPTER_NUMBER, chapterNumber,
                            ChapterGraphKeys.WORKFLOW_ID, workflowId,
                            ChapterGraphKeys.CONTEXT, context,
                            ChapterGraphKeys.DRAFT, chapter.content(),
                            ChapterGraphKeys.RETRY_COUNT, 0
                    ))
            );
            Object chapterMemoryValue = chapterMemoryUpdate.get(ChapterGraphKeys.MEMORY);
            if (!(chapterMemoryValue instanceof ChapterMemoryVO chapterMemory)) {
                throw workflowException("COMPRESSION 未返回章节派生数据");
            }
            Object snapshotValue = chapterMemoryUpdate.get(ChapterGraphKeys.STORY_STATE_SNAPSHOT);
            StoryStateSnapshot storyStateSnapshot = snapshotValue instanceof StoryStateSnapshot snapshot
                    ? snapshot
                    : StoryStateSnapshot.empty();

            chapterPersistRepository.persistDerivedData(
                    projectCode,
                    chapterNumber,
                    chapterMemory,
                    storyStateSnapshot);

            return new ChapterGenerationResultVO(
                    workflowId,
                    ChapterWorkflowStatusEnum.COMPLETED,
                    projectCode,
                    chapterNumber,
                    chapter.content(),
                    null,
                    chapterMemory,
                    List.of("COMPRESSION", "PERSIST_DERIVED_DATA"),
                    false
            );
        } catch (RuntimeException exception) {
            AppException appException = unwrapAppException(exception);
            if (appException != null) {
                throw appException;
            }
            throw workflowException(
                    "章节正文派生数据同步失败，projectCode=" + projectCode
                            + "，chapterNumber=" + chapterNumber,
                    exception
            );
        }
    }

    private void validateContextForDerivedDataSync(
            ChapterContextAggregate context,
            int chapterNumber
    ) {
        if (context == null || context.getProject() == null) {
            throw illegalParameter("正文派生数据同步对应的小说项目不存在");
        }
        if (context.getChapterPlan() == null) {
            throw illegalParameter(
                    "正文派生数据同步对应的章节计划不存在，chapterNumber=" + chapterNumber);
        }
    }

    @Override
    public ChapterGenerationResultVO resumeChapter(
            String workflowId,
            HumanDecisionEnum decision
    ) {
        return resumeChapter(workflowId, decision, null);
    }

    @Override
    public ChapterGenerationResultVO resumeChapter(
            String workflowId,
            HumanDecisionEnum decision,
            String revisionInstruction
    ) {
        if (workflowId == null || workflowId.isBlank()) {
            throw illegalParameter("章节生成会话标识不能为空");
        }
        if (decision == null) {
            throw illegalParameter("人工处理选项不能为空");
        }
        boolean keepCheckpointForHuman = false;
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(workflowId)
                    .build();
            log.info(
                    "恢复章节生成工作流，workflowId={}, humanDecision={}",
                    workflowId,
                    decision
            );
            NodeOutput<ChapterGraphState> output = chapterGraph.invokeFinal(
                    GraphInput.resume(resumeInput(
                            workflowId,
                            decision,
                            revisionInstruction
                    )),
                    config
            ).orElseThrow(() -> workflowException("章节生成工作流恢复后未返回状态"));
            ChapterGenerationResultVO result = toGenerationResult(workflowId, output);
            updateSessionSnapshot(result);
            publishWorkflowResultEvents(workflowId, output, result);
            if (output.isEND()) {
                metricsRecorder.finishSession(
                        workflowId,
                        result.projectCode(),
                        result.chapterNumber(),
                        result.content()
                );
            }
            keepCheckpointForHuman = !output.isEND();
            return result;
        } catch (Throwable exception) {
            metricsRecorder.finishSession(workflowId, null);
            AppException appException = unwrapAppException(exception);
            sessionRegistry.publish(
                    workflowId,
                    ChapterGenerationEventType.GENERATION_FAILED,
                    readableFailureMessage(appException != null ? appException : exception)
            );
            if (appException != null) {
                throw appException;
            }
            if (exception instanceof Error error) {
                throw error;
            }
            throw workflowException("章节生成工作流恢复失败", exception);
        } finally {
            clearStageStartTimes(workflowId);
            if (!keepCheckpointForHuman) {
                releaseWorkflowResources(workflowId);
            }
        }
    }

    private void releaseWorkflowResources(String workflowId) {
        releaseCheckpointThread(workflowId);
        metricsRecorder.releaseSession(workflowId);
    }

    /**
     * 释放 LangGraph thread。框架只会在正常走到 END 时自动释放，
     * 节点异常、取消或恢复异常必须由服务层在终态 finally 兜底。
     */
    private void releaseCheckpointThread(String workflowId) {
        if (checkpointSaver == null || workflowId == null || workflowId.isBlank()) {
            return;
        }
        try {
            checkpointSaver.release(RunnableConfig.builder()
                    .threadId(workflowId)
                    .build());
        } catch (Exception exception) {
            // 清理失败不能覆盖工作流原始异常，也不能阻止后续会话重试。
            log.warn("章节工作流 checkpoint thread 释放失败，workflowId={}", workflowId, exception);
        }
    }

    static Map<String, Object> resumeInput(
            HumanDecisionEnum decision,
            String revisionInstruction
    ) {
        return resumeInput(null, decision, revisionInstruction);
    }

    private static Map<String, Object> resumeInput(
            String workflowId,
            HumanDecisionEnum decision,
            String revisionInstruction
    ) {
        Map<String, Object> input = new HashMap<>();
        if (workflowId != null && !workflowId.isBlank()) {
            input.put(ChapterGraphKeys.WORKFLOW_ID, workflowId);
        }
        input.put(ChapterGraphKeys.HUMAN_DECISION, decision.name());
        input.put(
                ChapterGraphKeys.REVISION_INSTRUCTION,
                decision != HumanDecisionEnum.REVISE
                        || revisionInstruction == null
                        || revisionInstruction.isBlank()
                        ? ""
                        : revisionInstruction.trim()
        );
        return Map.copyOf(input);
    }

    private ChapterGenerationResultVO toGenerationResult(
            String workflowId,
            NodeOutput<ChapterGraphState> output
    ) {
        ChapterGraphState state = output.state();
        String projectCode = state.projectCode()
                .orElseThrow(() ->
                        workflowException("生成结果缺少 projectCode")
                );

        int chapterNumber = state.chapterNumber()
                .orElseThrow(() ->
                        workflowException("生成结果缺少 chapterNumber")
                );
        ChapterWorkflowStatusEnum status;
        if (output.isEND()) {
            status = state.workflowStatus()
                    .filter(ABORTED::equals)
                    .map(value -> ChapterWorkflowStatusEnum.ABORTED)
                    .orElse(ChapterWorkflowStatusEnum.COMPLETED);
        } else {
            status = state.workflowStatus()
                    .filter(ChapterWorkflowStatusEnum.REVIEW_FAILED.name()::equals)
                    .map(value -> ChapterWorkflowStatusEnum.REVIEW_FAILED)
                    .orElse(ChapterWorkflowStatusEnum.WAITING_HUMAN);
        }
        String content = state.draft()
                .filter(value -> !value.isBlank())
                .orElse(null);
        if (status == ChapterWorkflowStatusEnum.COMPLETED && content == null) {
            throw workflowException("生成结果缺少章节正文");
        }
        boolean canHumanRevise = status == ChapterWorkflowStatusEnum.WAITING_HUMAN
                && state.humanReviseRound()
                < HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND;

        return new ChapterGenerationResultVO(
                workflowId,
                status,
                projectCode,
                chapterNumber,
                content,
                state.reviewReport().orElse(null),
                state.chapterMemory().orElse(null),
                state.completedStages(),
                canHumanRevise
        );

    }

    private void updateSessionSnapshot(ChapterGenerationResultVO result) {
        sessionRegistry.updateSnapshot(
                result.workflowId(),
                result.content(),
                result.completedStages(),
                reviewIssues(result.reviewReport())
        );
    }

    private List<String> reviewIssues(ReviewReportVO report) {
        if (report == null || report.getReviewIssueVOList() == null) {
            return List.of();
        }
        return report.getReviewIssueVOList().stream()
                .filter(java.util.Objects::nonNull)
                .map(ReviewIssueVO::getDescription)
                .filter(description -> description != null && !description.isBlank())
                .toList();
    }

    private List<String> reviewIssuesFromUpdate(Map<String, Object> update) {
        Object report = update == null
                ? null
                : update.get(ChapterGraphKeys.REVIEW_REPORT);
        return report instanceof ReviewReportVO reviewReport
                ? reviewIssues(reviewReport)
                : List.of();
    }

    /**
     * 应用启动时只构建一次章节图。
     */
    private CompiledGraph<ChapterGraphState> buildChapterGraph(
            DraftChapterNode draftChapterNode,
            ReviewChapterNode reviewChapterNode,
            ReviseChapterNode reviseChapterNode,
            ReviewRouter reviewRouter,
            CompressChapterNode compressChapterNode,
            PersistChapterNode persistChapterNode,
            HumanDecisionRouter humanDecisionRouter,
            BaseCheckpointSaver checkpointSaver,
            StateSerializer<ChapterGraphState> stateSerializer
    ) {
        try {
            return new StateGraph<>(
                    ChapterGraphState.SCHEMA,
                    stateSerializer
            )
                    .addNode(
                            "LOAD_CONTEXT",
                            node_async(this::loadContext)
                    )
                    .addNode(
                            "DRAFT",
                            draftNodeWithEvents(draftChapterNode)
                    )
                    .addNode(
                            "REVIEW",
                            nodeWithEvents(
                                    reviewChapterNode,
                                    ChapterGenerationEventType.REVIEW_STARTED,
                                    ChapterGenerationEventType.REVIEW_COMPLETED,
                                    this::isAcceptRequested
                            )
                    )
                    .addNode(
                            REVISE,
                            nodeWithEvents(
                                    reviseChapterNode,
                                    ChapterGenerationEventType.REVISION_STARTED,
                                    ChapterGenerationEventType.REVISION_COMPLETED,
                                    this::isAcceptRequested
                            )
                    )
                    .addNode(
                            HUMAN,
                            node_async(this::markHumanReviewRequired)
                    )
                    .addNode(
                            "COMPRESSION",
                            nodeWithEvents(
                                    compressChapterNode,
                                    ChapterGenerationEventType.COMPRESSION_STARTED,
                                    ChapterGenerationEventType.COMPRESSION_COMPLETED
                            )
                    )
                    .addNode(
                            "PERSIST",
                            nodeWithEvents(
                                    persistChapterNode,
                                    ChapterGenerationEventType.PERSIST_STARTED,
                                    ChapterGenerationEventType.PERSIST_COMPLETED
                            )
                    )

                    .addEdge(START, "LOAD_CONTEXT")
                    .addEdge("LOAD_CONTEXT", "DRAFT")
                    .addConditionalEdges(
                            "DRAFT",
                            command_async(this::routeAfterDraft),
                            Map.of(
                                    "REVIEW", "REVIEW",
                                    COMPRESSION, COMPRESSION
                            )
                    )
                    .addConditionalEdges(
                            "REVIEW",
                            command_async(
                                    (state, config) -> routeAfterReview(
                                            state, config, reviewRouter
                                    )
                            ),
                            Map.of(
                                    PASS, "COMPRESSION",
                                    REVISE, REVISE,
                                    HUMAN, HUMAN,
                                    COMPRESSION, COMPRESSION
                            )
                    )
                    .addConditionalEdges(
                            HUMAN,
                            command_async(humanDecisionRouter),
                            Map.of(
                                    COMPRESSION, COMPRESSION,
                                    HumanDecisionRouter.REVISE, REVISE,
                                    HumanDecisionRouter.REVIEW, "REVIEW",
                                    END, END
                            )
                    )
                    .addConditionalEdges(
                            REVISE,
                            command_async(this::routeAfterRevise),
                            Map.of(
                                    "REVIEW", "REVIEW",
                                    COMPRESSION, COMPRESSION
                            )
                    )
                    .addEdge("COMPRESSION", "PERSIST")
                    .addEdge("PERSIST", END)

                    .compile(CompileConfig.builder()
                            .checkpointSaver(checkpointSaver)
                            .interruptBefore(HUMAN)
                            .releaseThread(true)
                            .build());
        } catch (GraphStateException exception) {
            throw workflowException("章节生成工作流构建失败", exception);
        }
    }

    private AsyncNodeActionWithConfig<ChapterGraphState> nodeWithEvents(
            NodeAction<ChapterGraphState> action,
            ChapterGenerationEventType startedEvent,
            ChapterGenerationEventType completedEvent
    ) {
        return nodeWithEvents(
                action,
                startedEvent,
                completedEvent,
                workflowId -> false
        );
    }

    private AsyncNodeActionWithConfig<ChapterGraphState> nodeWithEvents(
            NodeAction<ChapterGraphState> action,
            ChapterGenerationEventType startedEvent,
            ChapterGenerationEventType completedEvent,
            java.util.function.Predicate<String> skipAction
    ) {
        return (state, config) -> {
            String workflowId = config.threadId().orElse(null);
            ensureNotCancelled(workflowId);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    ensureNotCancelled(workflowId);
                    if (skipAction.test(workflowId)) {
                        return Map.of();
                    }
                    publishStageStarted(workflowId, state, startedEvent);
                    Map<String, Object> update = action.apply(state);
                    metricsRecorder.recordStageResult(
                            state,
                            metricsStage(startedEvent),
                            update
                    );
                    ensureNotCancelled(workflowId);
                    if (isReviewFailed(update)) {
                        String failureMessage = stringValue(
                                update.get(ChapterGraphKeys.FAILURE_MESSAGE),
                                "审稿报告校验失败，重试次数已用尽"
                        );
                        logStageFailed(
                                workflowId,
                                state,
                                startedEvent,
                                ChapterGenerationEventType.REVIEW_FAILED,
                                ResponseCode.E0004.getCode(),
                                failureMessage
                        );
                        publishEvent(
                                workflowId,
                                ChapterGenerationEventType.REVIEW_FAILED,
                                failureMessage
                        );
                    } else {
                        if (completedEvent == ChapterGenerationEventType.REVIEW_COMPLETED) {
                            List<String> reviewIssues = reviewIssuesFromUpdate(update);
                            logStageCompleted(
                                    workflowId,
                                    state,
                                    startedEvent,
                                    completedEvent,
                                    reviewIssues.size()
                            );
                            publishEvent(
                                    workflowId,
                                    completedEvent,
                                    null,
                                    reviewIssues
                            );
                        } else {
                            publishStageCompleted(
                                    workflowId,
                                    state,
                                    startedEvent,
                                    completedEvent
                            );
                        }
                    }
                    return update;
                } catch (Exception exception) {
                    logStageFailed(workflowId, state, startedEvent, exception);
                    if (!isCancellation(exception)) {
                        publishEvent(
                                workflowId,
                                ChapterGenerationEventType.GENERATION_FAILED,
                                ChapterGenerationSessionEvent.safeFailureMessageForStage(startedEvent)
                        );
                    }
                    throw new CompletionException(exception);
                }
            });
        };
    }

    private Command routeAfterDraft(
            ChapterGraphState state,
            RunnableConfig config
    ) {
        return new Command(
                isAcceptRequested(config.threadId().orElse(null))
                        ? COMPRESSION
                        : "REVIEW"
        );
    }

    private Command routeAfterReview(
            ChapterGraphState state,
            RunnableConfig config,
            ReviewRouter reviewRouter
    ) {
        if (isAcceptRequested(config.threadId().orElse(null))) {
            return new Command(COMPRESSION);
        }
        return reviewRouter.apply(state, config);
    }

    private boolean isReviewFailed(Map<String, Object> update) {
        return ChapterWorkflowStatusEnum.REVIEW_FAILED.name().equals(
                update.get(ChapterGraphKeys.WORKFLOW_STATUS)
        );
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private Command routeAfterRevise(
            ChapterGraphState state,
            RunnableConfig config
    ) {
        return new Command(
                isAcceptRequested(config.threadId().orElse(null))
                        ? COMPRESSION
                        : "REVIEW"
        );
    }

    private AsyncNodeActionWithConfig<ChapterGraphState> draftNodeWithEvents(
            DraftChapterNode draftChapterNode
    ) {
        return (state, config) -> {
            String workflowId = config.threadId().orElse(null);
            ensureNotCancelled(workflowId);
            publishStageStarted(
                    workflowId,
                    state,
                    ChapterGenerationEventType.DRAFT_STARTED
            );
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, Object> update = draftChapterNode.applyStreaming(
                            state,
                            chunk -> publishEvent(
                                    workflowId,
                                    ChapterGenerationEventType.DRAFT_CHUNK,
                                    chunk
                            ),
                            () -> sessionRegistry.isCancellationRequested(workflowId),
                            subscription -> {
                                if (subscription == null) {
                                    sessionRegistry.clearDraftSubscription(workflowId);
                                } else {
                                    sessionRegistry.registerDraftSubscription(
                                            workflowId,
                                            subscription
                                    );
                                }
                            }
                    );
                    metricsRecorder.recordStageResult(
                            state,
                            "DRAFT",
                            update
                    );
                    ensureNotCancelled(workflowId);
                    publishStageCompleted(
                            workflowId,
                            state,
                            ChapterGenerationEventType.DRAFT_STARTED,
                            ChapterGenerationEventType.DRAFT_COMPLETED
                    );
                    return update;
                } catch (Exception exception) {
                    logStageFailed(
                            workflowId,
                            state,
                            ChapterGenerationEventType.DRAFT_STARTED,
                            exception
                    );
                    if (!isCancellation(exception)) {
                        publishEvent(
                                workflowId,
                                ChapterGenerationEventType.GENERATION_FAILED,
                                ChapterGenerationSessionEvent.safeFailureMessageForStage(
                                        ChapterGenerationEventType.DRAFT_STARTED
                                )
                        );
                    }
                    throw new CompletionException(exception);
                }
            });
        };
    }

    private void publishStageStarted(
            String workflowId,
            ChapterGraphState state,
            ChapterGenerationEventType startedEvent
    ) {
        if (workflowId == null || startedEvent == null) {
            return;
        }
        stageStartTimes.computeIfAbsent(
                workflowId,
                ignored -> new ConcurrentHashMap<>()
        ).put(startedEvent, System.nanoTime());
        log.info(
                "[CHAPTER] workflow={} stage={} started chapter={}",
                workflowId,
                stageName(startedEvent),
                chapterNumber(state)
        );
        publishEvent(workflowId, startedEvent);
    }

    private void publishStageCompleted(
            String workflowId,
            ChapterGraphState state,
            ChapterGenerationEventType startedEvent,
            ChapterGenerationEventType completedEvent
    ) {
        logStageCompleted(workflowId, state, startedEvent, completedEvent, null);
        publishEvent(workflowId, completedEvent);
    }

    private void logStageCompleted(
            String workflowId,
            ChapterGraphState state,
            ChapterGenerationEventType startedEvent,
            ChapterGenerationEventType completedEvent,
            Integer reviewIssueCount
    ) {
        long durationMs = durationSinceStageStart(workflowId, startedEvent);
        if (reviewIssueCount != null) {
            log.info(
                    "[CHAPTER] workflow={} stage={} completed chapter={} issues={} costMs={}",
                    workflowId,
                    stageName(startedEvent),
                    chapterNumber(state),
                    reviewIssueCount,
                    durationMs
            );
        } else {
            log.info(
                    "[CHAPTER] workflow={} stage={} completed chapter={} costMs={}",
                    workflowId,
                    stageName(startedEvent),
                    chapterNumber(state),
                    durationMs
            );
        }
    }

    private Integer chapterNumber(ChapterGraphState state) {
        return state == null ? null : state.chapterNumber().orElse(null);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void logStageFailed(
            String workflowId,
            ChapterGraphState state,
            ChapterGenerationEventType startedEvent,
            Throwable exception
    ) {
        long durationMs = durationSinceStageStart(workflowId, startedEvent);
        AppException appException = unwrapAppException(exception);
        log.warn(
                "章节工作流阶段失败，workflowId={}，chapterNumber={}，node={}，errorCode={}，event={}，durationMs={}，message={}，cause={}",
                workflowId,
                state == null ? null : state.chapterNumber().orElse(null),
                stageName(startedEvent),
                appException == null
                        ? ResponseCode.UN_ERROR.getCode()
                        : appException.getCode(),
                startedEvent,
                durationMs,
                readableFailureMessage(exception),
                exception == null ? null : exception.getCause(),
                exception
        );
    }

    private void logStageFailed(
            String workflowId,
            ChapterGraphState state,
            ChapterGenerationEventType startedEvent,
            ChapterGenerationEventType terminalEvent,
            String errorCode,
            String message
    ) {
        long durationMs = durationSinceStageStart(workflowId, startedEvent);
        log.warn(
                "章节工作流阶段失败，workflowId={}，chapterNumber={}，node={}，errorCode={}，event={}，durationMs={}，message={}",
                workflowId,
                state == null ? null : state.chapterNumber().orElse(null),
                stageName(startedEvent),
                errorCode,
                terminalEvent,
                durationMs,
                message
        );
    }

    private long durationSinceStageStart(
            String workflowId,
            ChapterGenerationEventType startedEvent
    ) {
        if (workflowId == null || startedEvent == null) {
            return -1L;
        }
        ConcurrentMap<ChapterGenerationEventType, Long> workflowStageTimes =
                stageStartTimes.get(workflowId);
        if (workflowStageTimes == null) {
            return -1L;
        }
        Long startedAt = workflowStageTimes.remove(startedEvent);
        if (startedAt == null) {
            return -1L;
        }
        return Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        );
    }

    private void clearStageStartTimes(String workflowId) {
        if (workflowId != null) {
            stageStartTimes.remove(workflowId);
        }
    }

    private String stageName(ChapterGenerationEventType startedEvent) {
        return switch (startedEvent) {
            case DRAFT_STARTED -> "DRAFT";
            case REVIEW_STARTED -> "REVIEW";
            case REVISION_STARTED -> "REVISION";
            case COMPRESSION_STARTED -> "COMPRESSION";
            case PERSIST_STARTED -> "PERSIST";
            default -> startedEvent.name();
        };
    }

    private String metricsStage(ChapterGenerationEventType startedEvent) {
        return switch (startedEvent) {
            case DRAFT_STARTED -> "DRAFT";
            case REVIEW_STARTED -> "REVIEW";
            case REVISION_STARTED -> "REVISE";
            case COMPRESSION_STARTED -> "COMPRESSION";
            default -> "";
        };
    }

    private void publishEvent(
            String workflowId,
            ChapterGenerationEventType eventType
    ) {
        publishEvent(workflowId, eventType, null);
    }

    private void publishEvent(
            String workflowId,
            ChapterGenerationEventType eventType,
            String content
    ) {
        publishEvent(workflowId, eventType, content, null);
    }

    private void publishEvent(
            String workflowId,
            ChapterGenerationEventType eventType,
            String content,
            List<String> reviewIssues
    ) {
        if (workflowId != null && eventType != null) {
            sessionRegistry.publish(workflowId, eventType, content, reviewIssues);
        }
    }

    private void ensureNotCancelled(String workflowId) {
        if (workflowId != null && sessionRegistry.isCancellationRequested(workflowId)) {
            throw new CancellationException("章节生成工作流已取消");
        }
    }

    private boolean isAcceptRequested(String workflowId) {
        return workflowId != null && sessionRegistry.isAcceptRequested(workflowId);
    }

    private boolean isCancellation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private ChapterGenerationResultVO cancelledResult(
            String workflowId,
            String projectCode,
            int chapterNumber
    ) {
        return new ChapterGenerationResultVO(
                workflowId,
                ChapterWorkflowStatusEnum.CANCELLED,
                projectCode,
                chapterNumber,
                null,
                null,
                null,
                List.of(),
                false
        );
    }

    private void publishWorkflowResultEvents(
            String workflowId,
            NodeOutput<ChapterGraphState> output,
            ChapterGenerationResultVO result
    ) {
        if (output.isEND()) {
            if (result.status() == ChapterWorkflowStatusEnum.COMPLETED) {
                publishEvent(workflowId, ChapterGenerationEventType.GENERATION_COMPLETED);
            } else if (result.status() == ChapterWorkflowStatusEnum.ABORTED) {
                publishEvent(
                        workflowId,
                        ChapterGenerationEventType.GENERATION_ABORTED,
                        "用户选择结束章节生成流程"
                );
            }
            return;
        }
        if (result.status() == ChapterWorkflowStatusEnum.REVIEW_FAILED) {
            return;
        }
        publishEvent(workflowId, ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED);
    }

    private String readableFailureMessage(Throwable throwable) {
        AppException appException = unwrapAppException(throwable);
        if (appException != null) {
            return ResponseCode.messageFor(
                    appException.getCode(),
                    appException.getUserMessage()
            );
        }
        return ResponseCode.UN_ERROR.getMessage();
    }

    /**
     * HUMAN 当前只标记工作流已转人工处理；接入 checkpointer 后替换为可恢复的中断节点。
     */
    private Map<String, Object> markHumanReviewRequired(ChapterGraphState state) {
        metricsRecorder.markHumanIntervened(state);
        return Map.of(
                ChapterGraphKeys.CURRENT_NODE, HUMAN,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(HUMAN)
        );
    }

    private Map<String, Object> loadContext(ChapterGraphState chapterGraphState) {
        String projectCode = chapterGraphState.projectCode()
                .orElseThrow(()->workflowException(
                        "LOAD_CONTEXT 节点缺少 chapterNumber"
                )
                );
        int chapterNumber = chapterGraphState.chapterNumber()
                .orElseThrow(() ->
                        workflowException(
                                "LOAD_CONTEXT 节点缺少 chapterNumber"
                        )
                );
        ChapterContextAggregate context;
        try {
            context = dataService.loadContext(
                    projectCode,
                    chapterNumber
            );
            context.validateReadyForGeneration();
        }catch (AppException exception){
            throw exception;
        }catch (RuntimeException exception){
            throw workflowException(
                    "章节上下文加载失败，projectCode=" + projectCode
                            + "，chapterNumber=" + chapterNumber,
                    exception
            );
        }
        return Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.CURRENT_NODE, "LOAD_CONTEXT",
                ChapterGraphKeys.COMPLETED_STAGES,
                List.of("LOAD_CONTEXT")
        );
    }

    /**
     * 从异常 cause 链中还原节点抛出的 AppException。
     * langgraph 异步执行会把节点异常包装成 CompletionException 等，
     * 直接 catch(AppException) 无法命中，需沿 cause 链回溯。
     */
    private AppException unwrapAppException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AppException appException) {
                return appException;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    private AppException illegalParameter(String detail) {
        if (detail != null
                && (detail.contains("chapterNumber=")
                || detail.contains("projectCode=")
                || detail.contains("status=")
                || detail.contains("workflowId=")
                || detail.contains("Session"))) {
            return AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
        }
        return AppException.user(ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
    }
    private AppException workflowException(String detail) {
        return AppException.internal(
                ResponseCode.UN_ERROR.getCode(),
                detail
        );
    }
    private AppException workflowException(
            String detail,
            Throwable cause
    ) {
        return AppException.internal(
                ResponseCode.UN_ERROR.getCode(),
                detail,
                cause
        );
    }
}
