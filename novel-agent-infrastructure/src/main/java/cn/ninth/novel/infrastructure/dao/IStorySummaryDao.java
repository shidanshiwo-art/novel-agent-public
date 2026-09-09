package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IStorySummaryDao {
    int insertOrUpdate(StorySummaryPO summary);

    StorySummaryPO queryByProjectIdAndChapterNumber(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);

    int markStale(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);
    int deleteByProjectIdAndChapterNumber(@Param("projectId") Long projectId, @Param("chapterNumber") Integer chapterNumber);

    List<StorySummaryPO> queryRecent(
            @Param("projectId") Long projectId,
            @Param("beforeChapter") Integer beforeChapter,
            @Param("limit") Integer limit);

    List<StorySummaryPO> queryAllValidByProjectId(@Param("projectId") Long projectId);
}
