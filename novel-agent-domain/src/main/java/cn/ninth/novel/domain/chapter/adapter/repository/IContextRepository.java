package cn.ninth.novel.domain.chapter.adapter.repository;


import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;

import java.util.List;

/**
 * 章节结构化上下文仓储。
 * 各方法分项返回领域数据，最终由领域 Loader 统一组装为章节上下文聚合。
 */
public interface IContextRepository {

    /**
     * 加载小说项目。
     *
     * @param projectCode 项目业务编码
     * @return 小说项目实体，不存在时返回 {@code null}
     */
    NovelProjectEntity loadProject(String projectCode);

    /**
     * 加载项目的故事圣经。
     *
     * @param projectCode 项目业务编码
     * @return 故事圣经实体，不存在时返回 {@code null}
     */
    StoryBibleEntity loadStoryBible(String projectCode);

    /**
     * 加载指定章节的章节计划。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 章节号
     * @return 章节计划实体，不存在时返回 {@code null}
     */
    ChapterPlanEntity loadChapterPlan(
            String projectCode, int chapterNumber);

    /**
     * 加载覆盖指定章节的当前 ARC。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 章节号
     * @return 当前 ARC，不存在或章节不在 ARC 范围内时返回 {@code null}
     */
    default OutlineNodeVO loadCurrentArc(String projectCode, int chapterNumber) {
        return null;
    }

    /**
     * 加载项目下的人物数据。
     *
     * @param projectCode 项目业务编码
     * @return 人物实体列表，无数据时返回空列表
     */
    List<StoryCharacterEntity> loadCharacters(String projectCode);

    /**
     * 加载指定章节之前的历史数据。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 当前待生成章节号
     * @return 近期压缩记忆与直接上一章组成的历史值对象
     */
    ChapterHistoryVO loadHistory(
            String projectCode, int chapterNumber);

    /**
     * 查询当前章节之前最近的章节压缩记忆。
     *
     * <p>返回值只包含章节记忆业务字段，不暴露数据库 JSON 字符串。</p>
     */
    default List<ChapterMemoryVO> findRecentChapterMemories(
            String projectCode,
            Integer chapterNumber,
            int limit
    ) {
        return List.of();
    }

    /**
     * 查询供统一 MemoryContextProvider 使用的长期候选。
     * 旧的 ChapterHistoryVO/ChapterMemoryVO 查询仍保留，作为兼容 fallback。
     */
    default List<MemoryContextItem> findMemoryContextItems(
            String projectCode,
            Integer chapterNumber
    ) {
        return List.of();
    }

    /** 查询已完成 backfill 的 canonical candidates；迁移未完成时允许为空。 */
    default List<MemoryContextItem> findCanonicalMemoryContextItems(
            String projectCode,
            Integer chapterNumber
    ) {
        return List.of();
    }

    /** 明确读取旧 ChapterMemory/StoryStateSnapshot 产生的 legacy bridge。 */
    default List<MemoryContextItem> findLegacyMemoryContextItems(
            String projectCode,
            Integer chapterNumber
    ) {
        return findMemoryContextItems(projectCode, chapterNumber);
    }
}
