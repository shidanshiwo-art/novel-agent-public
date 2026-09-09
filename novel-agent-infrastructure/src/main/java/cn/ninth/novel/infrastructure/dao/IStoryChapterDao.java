package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IStoryChapterDao {
    int insertOrUpdate(StoryChapterPO chapter);
    int deleteByProjectIdAndChapterNumber(@Param("projectId") Long projectId, @Param("chapterNumber") Integer chapterNumber);

    StoryChapterPO queryByProjectIdAndChapterNumber(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);

    int updateStatusIfCurrent(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber,
            @Param("currentStatus") String currentStatus,
            @Param("nextStatus") String nextStatus);

    List<StoryChapterPO> queryByProjectId(
            @Param("projectId") Long projectId);

    List<StoryChapterPO> queryByProjectIdAndContentLike(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);

    int countByProjectId(@Param("projectId") Long projectId);

    int countByProjectIdAndChapterNumberGreaterThan(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);

    Integer queryMaxChapterNumber(@Param("projectId") Long projectId);
}
