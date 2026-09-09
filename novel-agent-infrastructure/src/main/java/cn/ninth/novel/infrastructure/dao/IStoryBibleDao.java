package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.StoryBiblePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IStoryBibleDao {
    int insert(StoryBiblePO storyBible);

    int updateByProjectId(StoryBiblePO storyBible);

    StoryBiblePO queryByProjectId(@Param("projectId") Long projectId);

    int deleteByProjectId(@Param("projectId") Long projectId);
}
