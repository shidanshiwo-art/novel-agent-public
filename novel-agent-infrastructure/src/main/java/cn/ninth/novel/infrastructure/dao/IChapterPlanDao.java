package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IChapterPlanDao {
    int insert(ChapterPlanPO chapterPlan);

    ChapterPlanPO queryByChapterNumber(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);

    List<ChapterPlanPO> queryByOutlineNode(
            @Param("projectId") Long projectId,
            @Param("outlineNodeId") Long outlineNodeId);

    List<ChapterPlanPO> queryByProject(@Param("projectId") Long projectId);

    int update(ChapterPlanPO chapterPlan);

    int updateStatus(
            @Param("projectId") Long projectId,
            @Param("id") Long id,
            @Param("status") String status);

    int updateStatusIfCurrent(
            @Param("projectId") Long projectId,
            @Param("id") Long id,
            @Param("currentStatus") String currentStatus,
            @Param("status") String status);

    int delete(
            @Param("projectId") Long projectId,
            @Param("id") Long id);
}
