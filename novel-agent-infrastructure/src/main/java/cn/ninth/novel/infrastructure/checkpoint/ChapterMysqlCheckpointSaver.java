package cn.ninth.novel.infrastructure.checkpoint;

import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.CheckpointStateCodec;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.LinkedList;
import java.util.UUID;

/**
 * 使用项目 Jackson 3 基线持久化章节图检查点。
 *
 * <p>表结构由 {@code docs/sql/schema.sql} 手动初始化；
 * 本服务不创建、删除或迁移数据库表。</p>
 */
public final class ChapterMysqlCheckpointSaver extends AbstractCheckpointSaver {

    private static final String SELECT_CHECKPOINTS = """
            SELECT checkpoint.checkpoint_id,
                   checkpoint.node_id,
                   checkpoint.next_node_id,
                   checkpoint.state_data
            FROM LANGRAPH4J_CHECKPOINT checkpoint
            JOIN LANGRAPH4J_THREAD thread
              ON checkpoint.thread_id = thread.thread_id
            WHERE thread.thread_name = ?
              AND thread.is_released = FALSE
            ORDER BY checkpoint.saved_at DESC, checkpoint.id DESC
            """;

    private static final String UPSERT_ACTIVE_THREAD = """
            INSERT INTO LANGRAPH4J_THREAD (thread_id, thread_name, is_released)
            VALUES (?, ?, FALSE)
            ON DUPLICATE KEY UPDATE thread_id = thread_id
            """;

    private static final String SELECT_ACTIVE_THREAD_ID = """
            SELECT thread_id
            FROM LANGRAPH4J_THREAD
            WHERE thread_name = ?
              AND is_released = FALSE
            """;

    private static final String INSERT_CHECKPOINT = """
            INSERT INTO LANGRAPH4J_CHECKPOINT
                (checkpoint_id, thread_id, node_id, next_node_id, state_data)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_CHECKPOINT = """
            UPDATE LANGRAPH4J_CHECKPOINT
            SET checkpoint_id = ?,
                node_id = ?,
                next_node_id = ?,
                state_data = ?
            WHERE checkpoint_id = ?
            """;

    private static final String RELEASE_THREAD = """
            UPDATE LANGRAPH4J_THREAD
            SET is_released = TRUE
            WHERE thread_name = ?
              AND is_released = FALSE
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CheckpointStateCodec codec;

    public ChapterMysqlCheckpointSaver(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            CheckpointStateCodec codec
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.codec = codec;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) {
        return new LinkedList<>(jdbcTemplate.query(
                SELECT_CHECKPOINTS,
                (resultSet, rowNumber) -> Checkpoint.builder()
                        .id(resultSet.getString("checkpoint_id"))
                        .nodeId(resultSet.getString("node_id"))
                        .nextNodeId(resultSet.getString("next_node_id"))
                        .state(codec.decode(resultSet.getString("state_data")))
                        .build(),
                threadId(config)
        ));
    }

    @Override
    protected void insertedCheckpoint(
            RunnableConfig config,
            LinkedList<Checkpoint> checkpoints,
            Checkpoint checkpoint
    ) {
        String workflowId = threadId(config);
        String stateJson = codec.encode(checkpoint.getState());

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    UPSERT_ACTIVE_THREAD,
                    UUID.randomUUID().toString(),
                    workflowId
            );
            String databaseThreadId = jdbcTemplate.queryForObject(
                    SELECT_ACTIVE_THREAD_ID,
                    String.class,
                    workflowId
            );
            int inserted = jdbcTemplate.update(
                    INSERT_CHECKPOINT,
                    checkpoint.getId(),
                    databaseThreadId,
                    checkpoint.getNodeId(),
                    checkpoint.getNextNodeId(),
                    stateJson
            );
            requireSingleRow(inserted, "保存 checkpoint");
        });
    }

    @Override
    protected void updatedCheckpoint(
            RunnableConfig config,
            LinkedList<Checkpoint> checkpoints,
            Checkpoint checkpoint
    ) {
        String previousCheckpointId = config.checkPointId()
                .orElseThrow(() -> new IllegalArgumentException("缺少待更新的 checkpointId"));
        int updated = jdbcTemplate.update(
                UPDATE_CHECKPOINT,
                checkpoint.getId(),
                checkpoint.getNodeId(),
                checkpoint.getNextNodeId(),
                codec.encode(checkpoint.getState()),
                previousCheckpointId
        );
        requireSingleRow(updated, "更新 checkpoint");
    }

    @Override
    protected Tag releaseCheckpoints(
            RunnableConfig config,
            LinkedList<Checkpoint> checkpoints
    ) {
        // 工作流正常结束时框架可能已经释放过一次；异常/取消路径会再次兜底调用。
        // 释放本身必须幂等，0 行表示 thread 已释放或从未创建，不应阻断原始结果。
        jdbcTemplate.update(RELEASE_THREAD, threadId(config));
        return new Tag(threadId(config), checkpoints);
    }

    private void requireSingleRow(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new IllegalStateException(operation + "影响行数异常: " + affectedRows);
        }
    }
}
