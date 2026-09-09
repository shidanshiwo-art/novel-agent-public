package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.ChapterGraphStateSerializer;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;
import cn.ninth.novel.infrastructure.checkpoint.ChapterMysqlCheckpointSaver;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterHumanCheckpointResumeTest {

    private static final String REVIEW = "REVIEW";
    private static final String HUMAN = "HUMAN";
    private static final String REVISE = "REVISE";
    private static final String COMPRESSION = "COMPRESSION";
    private static final String PERSIST = "PERSIST";

    private final String threadPrefix = "human-checkpoint-test-" + UUID.randomUUID();
    private final AtomicInteger reviseExecutions = new AtomicInteger();
    private final AtomicInteger persistExecutions = new AtomicInteger();

    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ChapterCheckpointStateCodec codec;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE checkpoint
                FROM LANGRAPH4J_CHECKPOINT checkpoint
                JOIN LANGRAPH4J_THREAD workflow_thread
                  ON checkpoint.thread_id = workflow_thread.thread_id
                WHERE workflow_thread.thread_name LIKE ?
                """, threadPrefix + "%");
        jdbcTemplate.update(
                "DELETE FROM LANGRAPH4J_THREAD WHERE thread_name LIKE ?",
                threadPrefix + "%"
        );
    }

    @Test
    void shouldAuthorizeOneRevisionThenInterruptAgainBeforePass() throws Exception {
        String threadId = threadPrefix + "-revise";
        RunnableConfig config = config(threadId);
        ChapterMysqlCheckpointSaver saver = saver();
        CompiledGraph<ChapterGraphState> graph = graph(saver);

        NodeOutput<ChapterGraphState> firstPause = graph.invokeFinal(
                GraphInput.args(initialState()),
                config
        ).orElseThrow();

        assertThat(firstPause.isEND()).isFalse();
        assertThat(saver.get(config).orElseThrow().getNextNodeId()).isEqualTo(HUMAN);

        NodeOutput<ChapterGraphState> secondPause = graph.invokeFinal(
                GraphInput.resume(Map.of(
                        ChapterGraphKeys.HUMAN_DECISION,
                        HumanDecisionEnum.REVISE.name()
                )),
                config
        ).orElseThrow();

        assertThat(secondPause.isEND()).isFalse();
        assertThat(reviseExecutions).hasValue(1);
        ChapterGraphState pausedState = new ChapterGraphState(
                saver.get(config).orElseThrow().getState()
        );
        assertThat(pausedState.reviseRound()).isZero();
        assertThat(pausedState.humanReviseRound()).isEqualTo(1);
        assertThat(pausedState.humanDecision()).isEmpty();

        NodeOutput<ChapterGraphState> completed = graph.invokeFinal(
                GraphInput.resume(Map.of(
                        ChapterGraphKeys.HUMAN_DECISION,
                        HumanDecisionEnum.PASS.name()
                )),
                config
        ).orElseThrow();

        assertThat(completed.isEND()).isTrue();
        assertThat(persistExecutions).hasValue(1);
        assertThat(saver.get(config)).isEmpty();
    }

    @Test
    void shouldAcceptReviewFailureAndPersistFromCheckpoint() throws Exception {
        String threadId = threadPrefix + "-review-failed-accept";
        RunnableConfig config = config(threadId);
        ChapterMysqlCheckpointSaver saver = saver();
        CompiledGraph<ChapterGraphState> graph = graph(saver);

        NodeOutput<ChapterGraphState> paused = graph.invokeFinal(
                GraphInput.args(Map.of(
                        ChapterGraphKeys.PROJECT_CODE, "checkpoint-project",
                        ChapterGraphKeys.CHAPTER_NUMBER, 3,
                        ChapterGraphKeys.DRAFT, "审稿失败但保留的正文",
                        ChapterGraphKeys.REVISE_ROUND, 0,
                        ChapterGraphKeys.WORKFLOW_STATUS, "REVIEW_FAILED"
                )),
                config
        ).orElseThrow();

        assertThat(paused.isEND()).isFalse();
        assertThat(paused.state().workflowStatus()).contains("REVIEW_FAILED");
        assertThat(saver.get(config).orElseThrow().getNextNodeId()).isEqualTo(HUMAN);

        NodeOutput<ChapterGraphState> completed = graph.invokeFinal(
                GraphInput.resume(Map.of(
                        ChapterGraphKeys.HUMAN_DECISION,
                        HumanDecisionEnum.PASS.name()
                )),
                config
        ).orElseThrow();

        assertThat(completed.isEND()).isTrue();
        assertThat(persistExecutions).hasValue(1);
        assertThat(saver.get(config)).isEmpty();
        System.out.println("REVIEW_FAILED 采用当前正文检查：PASS 从 checkpoint 恢复并完成 COMPRESSION → PERSIST");
    }

    @Test
    void shouldAbortWithoutPersisting() throws Exception {
        String threadId = threadPrefix + "-abort";
        RunnableConfig config = config(threadId);
        ChapterMysqlCheckpointSaver saver = saver();
        CompiledGraph<ChapterGraphState> graph = graph(saver);

        graph.invokeFinal(GraphInput.args(initialState()), config).orElseThrow();
        NodeOutput<ChapterGraphState> aborted = graph.invokeFinal(
                GraphInput.resume(Map.of(
                        ChapterGraphKeys.HUMAN_DECISION,
                        HumanDecisionEnum.ABORT.name()
                )),
                config
        ).orElseThrow();

        assertThat(aborted.isEND()).isTrue();
        assertThat(aborted.state().workflowStatus()).contains("ABORTED");
        assertThat(persistExecutions).hasValue(0);
        assertThat(saver.get(config)).isEmpty();
    }

    private CompiledGraph<ChapterGraphState> graph(
            ChapterMysqlCheckpointSaver saver
    ) throws GraphStateException {
        ReviewRouter reviewRouter = new ReviewRouter();
        HumanDecisionRouter humanRouter = new HumanDecisionRouter();
        return new StateGraph<>(
                ChapterGraphState.SCHEMA,
                new ChapterGraphStateSerializer(codec)
        )
                .addNode(REVIEW, node_async(state -> Map.of(
                        ChapterGraphKeys.REVIEW_REPORT, blockerReport()
                )))
                .addNode(HUMAN, node_async(state -> Map.of(
                        ChapterGraphKeys.CURRENT_NODE, HUMAN
                )))
                .addNode(REVISE, node_async(state -> {
                    reviseExecutions.incrementAndGet();
                    return Map.of(ChapterGraphKeys.CURRENT_NODE, REVISE);
                }))
                .addNode(COMPRESSION, node_async(state -> Map.of(
                        ChapterGraphKeys.CURRENT_NODE, COMPRESSION
                )))
                .addNode(PERSIST, node_async(state -> {
                    persistExecutions.incrementAndGet();
                    return Map.of(ChapterGraphKeys.CURRENT_NODE, PERSIST);
                }))
                .addEdge(START, REVIEW)
                .addConditionalEdges(REVIEW, command_async(reviewRouter), Map.of(
                        ReviewRouter.PASS, COMPRESSION,
                        ReviewRouter.REVISE, REVISE,
                        ReviewRouter.HUMAN, HUMAN
                ))
                .addConditionalEdges(HUMAN, command_async(humanRouter), Map.of(
                        HumanDecisionRouter.COMPRESSION, COMPRESSION,
                        HumanDecisionRouter.REVISE, REVISE,
                        END, END
                ))
                .addEdge(REVISE, REVIEW)
                .addEdge(COMPRESSION, PERSIST)
                .addEdge(PERSIST, END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore(HUMAN)
                        .releaseThread(true)
                        .build());
    }

    private Map<String, Object> initialState() {
        return Map.of(
                ChapterGraphKeys.PROJECT_CODE, "checkpoint-project",
                ChapterGraphKeys.CHAPTER_NUMBER, 3,
                ChapterGraphKeys.DRAFT, "待人工处理正文",
                ChapterGraphKeys.REVISE_ROUND, ReviewRouter.MAX_REVISE_ROUND
        );
    }

    private ReviewReportVO blockerReport() {
        return ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.BLOCKER)
                        .description("仍有阻断问题")
                        .build()))
                .build();
    }

    private RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }

    private ChapterMysqlCheckpointSaver saver() {
        return new ChapterMysqlCheckpointSaver(dataSource, transactionManager, codec);
    }
}
