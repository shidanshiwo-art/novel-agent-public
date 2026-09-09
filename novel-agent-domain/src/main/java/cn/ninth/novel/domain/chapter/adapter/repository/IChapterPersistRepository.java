package cn.ninth.novel.domain.chapter.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;

/**
 * 章节生成结果持久化端口。
 * 负责将定稿正文、章节摘要和项目进度作为一个事务写入业务数据库。
 */
public interface IChapterPersistRepository {

    /**
     * 持久化指定章节的最终生成结果。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 章节号
     * @param content 定稿正文
     * @param chapterMemory 章节记忆；当前 MVP 持久化其中的章节摘要
     */
    void persist(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory
    );

    /**
     * 持久化章节记忆和当前故事状态。旧调用方仍可只提交 ChapterMemory，
     * 新的 COMPRESSION 链路通过该重载保存独立状态快照。
     */
    default void persist(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot
    ) {
        persist(projectCode, chapterNumber, content, chapterMemory);
    }

    /**
     * 重新同步人工修改章节的派生数据。
     *
     * <p>该入口不改写正文和章节计划，只替换章节记忆摘要并更新人物 runtime state。</p>
     */
    default void persistDerivedData(
            String projectCode,
            int chapterNumber,
            ChapterMemoryVO chapterMemory
    ) {
        throw new UnsupportedOperationException("当前仓储未提供正文派生数据同步能力");
    }

    default void persistDerivedData(
            String projectCode,
            int chapterNumber,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot
    ) {
        persistDerivedData(projectCode, chapterNumber, chapterMemory);
    }
}
