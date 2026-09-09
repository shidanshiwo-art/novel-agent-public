package cn.ninth.novel.domain.planning.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.planning.model.valobj.*;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;

import java.util.List;

public interface IPlanningRepository {

    default NovelProjectVO findProject(String projectCode) {
        throw new UnsupportedOperationException("项目查询尚未迁移");
    }

    default StoryBibleVO findBible(String projectCode) {
        throw new UnsupportedOperationException("故事圣经查询尚未迁移");
    }

    default List<StoryCharacterVO> findCharacters(String projectCode) {
        throw new UnsupportedOperationException("人物查询尚未迁移");
    }

    /**
     * 查询上一章用于单章规划的摘要。
     *
     * <p>实现必须优先返回上一章实际正文产生的 story_summary，只有实际摘要不可用时，
     * 才能回退到上一章 ChapterPlan.summary。</p>
     */
    default String findPreviousChapterSummary(
            String projectCode,
            Integer chapterNumber
    ) {
        return null;
    }

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

    OutlineNodeVO findOutline(String projectCode, String nodeCode);

    default OutlineNodeVO findRootOutline(String projectCode) {
        throw new UnsupportedOperationException("根大纲查询尚未迁移");
    }

    List<OutlineNodeVO> listOutlines(String projectCode);

    /**
     * 修复历史上直接挂在 BOOK 下的 ARC。
     *
     * <p>迁移只调整 ARC 的父节点，不改变 ARC 编码、章节范围或 ChapterPlan 引用。</p>
     */
    default int migrateLegacyBookArcs(String projectCode) {
        return 0;
    }

    boolean hasChildren(String projectCode, String nodeCode);

    boolean hasChapterPlans(String projectCode, String nodeCode);

    default List<ChapterOutlineVO> findChapterPlanOutlines(String projectCode) {
        throw new UnsupportedOperationException("章节计划查询尚未迁移");
    }

    default ChapterOutlineVO updateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            ChapterOutlineVO chapterPlan
    ) {
        throw new UnsupportedOperationException("章节计划更新尚未迁移");
    }

    /**
     * 单章确认使用的幂等写入，不得执行整段章节计划替换或清理。
     */
    default ChapterOutlineVO upsertChapterPlan(
            String projectCode,
            ChapterOutlineVO chapterPlan
    ) {
        throw new UnsupportedOperationException("单章章节计划写入尚未迁移");
    }

    default List<OutlineNodeVO> listChildren(String projectCode, String parentNodeCode) {
        throw new UnsupportedOperationException("子大纲查询尚未迁移");
    }

    default OutlineNodeVO saveOutline(String projectCode, OutlineNodeVO outline) {
        throw new UnsupportedOperationException("大纲保存尚未迁移");
    }

    /**
     * 在一个事务内封口当前活动卷，并创建新的活动卷。
     *
     * <p>该操作仅供规划服务使用，不是普通用户编辑范围的入口。</p>
     */
    default OutlineNodeVO finalizeCurrentVolumeAndCreateNext(
            String projectCode,
            String currentVolumeNodeCode,
            OutlineNodeVO nextVolume
    ) {
        throw new UnsupportedOperationException("活动卷封口操作尚未迁移");
    }

    default OutlineNodeVO updateOutline(String projectCode, OutlineNodeVO outline) {
        throw new UnsupportedOperationException("大纲更新尚未迁移");
    }

    default OutlineNodeVO reorderOutline(
            String projectCode,
            String nodeCode,
            Integer targetSequence
    ) {
        throw new UnsupportedOperationException("大纲排序尚未迁移");
    }

    default void deleteOutline(String projectCode, String nodeCode) {
        throw new UnsupportedOperationException("大纲删除尚未迁移");
    }

    default int nextSequence(String projectCode, String parentNodeCode) {
        throw new UnsupportedOperationException("大纲顺序号查询尚未迁移");
    }

}
