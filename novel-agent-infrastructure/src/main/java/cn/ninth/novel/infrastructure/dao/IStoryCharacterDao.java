package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IStoryCharacterDao {
    int insert(StoryCharacterPO character);

    int update(StoryCharacterPO character);

    StoryCharacterPO queryByCharacterCode(
            @Param("projectId") Long projectId,
            @Param("characterCode") String characterCode);

    StoryCharacterPO queryByCharacterCodeForUpdate(
            @Param("projectId") Long projectId,
            @Param("characterCode") String characterCode);

    List<StoryCharacterPO> queryByProjectId(@Param("projectId") Long projectId);

    StoryCharacterPO queryMaleLead(@Param("projectId") Long projectId);

    int deleteByProjectIdAndCharacterCode(
            @Param("projectId") Long projectId,
            @Param("characterCode") String characterCode);

    int deleteByProjectId(@Param("projectId") Long projectId);
}
