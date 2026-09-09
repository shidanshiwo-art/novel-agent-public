package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.ChapterGraphStateSerializer;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;
import cn.ninth.novel.infrastructure.checkpoint.ChapterMysqlCheckpointSaver;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
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
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterGraphCheckpointResumeTest {

    private static final String FIRST = "FIRST";
    private static final String SECOND = "SECOND";

    private final String threadId = "checkpoint-resume-test-" + UUID.randomUUID();
    private final AtomicInteger firstExecutions = new AtomicInteger();
    private final AtomicInteger secondExecutions = new AtomicInteger();

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
                WHERE workflow_thread.thread_name = ?
                """, threadId);
        jdbcTemplate.update(
                "DELETE FROM LANGRAPH4J_THREAD WHERE thread_name = ?",
                threadId
        );
    }

    @Test
    void shouldResumeAtNextNodeAfterGraphAndSaverAreRecreated() throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        ChapterMysqlCheckpointSaver firstSaver = newSaver();
        CompiledGraph<ChapterGraphState> interruptedGraph =
                buildGraph(firstSaver, true);

        interruptedGraph.invoke(GraphInput.args(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "checkpoint-project",
                ChapterGraphKeys.CHAPTER_NUMBER, 3
        )), config);

        assertThat(firstExecutions).hasValue(1);
        assertThat(secondExecutions).hasValue(0);
        assertThat(firstSaver.get(config).orElseThrow().getState()
                .get(ChapterGraphKeys.DRAFT))
                .isEqualTo("恢复后仍为正文草稿");

        ChapterMysqlCheckpointSaver restartedSaver = newSaver();
        CompiledGraph<ChapterGraphState> restartedGraph =
                buildGraph(restartedSaver, false);

        ChapterGraphState resumedState = restartedGraph
                .invoke(GraphInput.resume(), config)
                .orElseThrow();

        assertThat(firstExecutions).hasValue(1);
        assertThat(secondExecutions).hasValue(1);
        assertThat(resumedState.draft())
                .contains("恢复后仍为正文草稿");
        assertThat(resumedState.completedStages())
                .containsExactly(FIRST, SECOND);
        assertThat(restartedSaver.get(config)).isEmpty();
    }

    private ChapterMysqlCheckpointSaver newSaver() {
        return new ChapterMysqlCheckpointSaver(
                dataSource,
                transactionManager,
                codec
        );
    }

    private CompiledGraph<ChapterGraphState> buildGraph(
            BaseCheckpointSaver saver,
            boolean interruptBeforeSecond
    ) throws GraphStateException {
        StateGraph<ChapterGraphState> graph = new StateGraph<>(
                ChapterGraphState.SCHEMA,
                new ChapterGraphStateSerializer(codec)
        )
                .addNode(FIRST, node_async(state -> {
                    firstExecutions.incrementAndGet();
                    return Map.of(
                            ChapterGraphKeys.DRAFT, "恢复后仍为正文草稿",
                            ChapterGraphKeys.COMPLETED_STAGES,
                            List.of(FIRST)
                    );
                }))
                .addNode(SECOND, node_async(state -> {
                    state.draft().orElseThrow();
                    secondExecutions.incrementAndGet();
                    return Map.of(
                            ChapterGraphKeys.COMPLETED_STAGES,
                            List.of(SECOND)
                    );
                }))
                .addEdge(START, FIRST)
                .addEdge(FIRST, SECOND)
                .addEdge(SECOND, END);

        CompileConfig.Builder compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(true);
        if (interruptBeforeSecond) {
            compileConfig.interruptBefore(SECOND);
        }
        return graph.compile(compileConfig.build());
    }
}
