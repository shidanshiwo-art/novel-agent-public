package cn.ninth.novel.infrastructure.checkpoint;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterMysqlCheckpointSaverTest {

    private final String threadId = "checkpoint-saver-test-" + UUID.randomUUID();

    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BaseCheckpointSaver configuredCheckpointSaver;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE checkpoint
                FROM LANGRAPH4J_CHECKPOINT checkpoint
                JOIN LANGRAPH4J_THREAD thread
                  ON checkpoint.thread_id = thread.thread_id
                WHERE thread.thread_name = ?
                """, threadId);
        jdbcTemplate.update(
                "DELETE FROM LANGRAPH4J_THREAD WHERE thread_name = ?",
                threadId
        );
    }

    @Test
    void shouldRestoreOrderedTypedCheckpointsAfterSaverRecreationAndReleaseThread() throws Exception {
        assertThat(configuredCheckpointSaver)
                .isInstanceOf(ChapterMysqlCheckpointSaver.class);
        ChapterCheckpointStateCodec codec = new ChapterCheckpointStateCodec(objectMapper);
        ChapterMysqlCheckpointSaver saver =
                new ChapterMysqlCheckpointSaver(dataSource, transactionManager, codec);
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        Checkpoint first = checkpoint("LOAD_CONTEXT", "DRAFT", "第一版上下文");
        Checkpoint second = checkpoint("DRAFT", "REVIEW", "第二版上下文");
        saver.put(config, first);
        saver.put(config, second);

        ChapterMysqlCheckpointSaver restartedSaver =
                new ChapterMysqlCheckpointSaver(dataSource, transactionManager, codec);

        assertThat(restartedSaver.get(config))
                .get()
                .extracting(Checkpoint::getId)
                .isEqualTo(second.getId());
        assertThat(restartedSaver.list(config))
                .extracting(Checkpoint::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(restartedSaver.get(config).orElseThrow().getState()
                .get(ChapterGraphKeys.CONTEXT))
                .isInstanceOf(ChapterContextAggregate.class)
                .extracting(value -> ((ChapterContextAggregate) value).getProject().getTitle())
                .isEqualTo("第二版上下文");

        restartedSaver.release(config);
        restartedSaver.release(config);

        assertThat(restartedSaver.get(config)).isEmpty();
        System.out.println("checkpoint thread 释放幂等通过：首次释放后重复释放不影响读取结果");
    }

    private Checkpoint checkpoint(String nodeId, String nextNodeId, String goal) {
        return Checkpoint.builder()
                .nodeId(nodeId)
                .nextNodeId(nextNodeId)
                .state(Map.of(
                        ChapterGraphKeys.PROJECT_CODE, "checkpoint-project",
                        ChapterGraphKeys.CHAPTER_NUMBER, 3,
                        ChapterGraphKeys.CONTEXT, ChapterContextAggregate.builder()
                                .project(NovelProjectEntity.builder().title(goal).build())
                                .build()
                ))
                .build();
    }
}
