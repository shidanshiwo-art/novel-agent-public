package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface INovelProjectDao {
    int insert(NovelProjectPO project);

    int update(NovelProjectPO project);

    int advanceProgress(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);

    int updateCurrentChapterNumber(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber);

    NovelProjectPO queryByProjectCode(@Param("projectCode") String projectCode);

    List<NovelProjectPO> queryAll();

    int deleteByProjectCode(@Param("projectCode") String projectCode);
}
