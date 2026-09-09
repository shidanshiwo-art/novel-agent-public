package cn.ninth.novel.config;

import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.ChapterGraphStateSerializer;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.CheckpointStateCodec;
import cn.ninth.novel.infrastructure.checkpoint.ChapterMysqlCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/** 装配章节工作流使用的 MySQL Checkpointer。 */
@Configuration
public class CheckpointerConfiguration {

    @Bean
    public StateSerializer<ChapterGraphState> chapterGraphStateSerializer(
            CheckpointStateCodec stateCodec
    ) {
        return new ChapterGraphStateSerializer(stateCodec);
    }

    @Bean
    public BaseCheckpointSaver chapterCheckpointSaver(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            CheckpointStateCodec stateCodec
    ) {
        return new ChapterMysqlCheckpointSaver(
                dataSource,
                transactionManager,
                stateCodec
        );
    }
}
