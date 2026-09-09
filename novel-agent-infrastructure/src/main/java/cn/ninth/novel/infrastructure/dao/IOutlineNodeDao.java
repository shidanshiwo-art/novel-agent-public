package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IOutlineNodeDao {
    int insert(OutlineNodePO outlineNode);

    int update(OutlineNodePO outlineNode);

    int deleteOne(@Param("projectId") Long projectId, @Param("id") Long id);

    OutlineNodePO queryByCode(
            @Param("projectId") Long projectId,
            @Param("nodeCode") String nodeCode);

    OutlineNodePO queryByCodeForUpdate(
            @Param("projectId") Long projectId,
            @Param("nodeCode") String nodeCode);

    OutlineNodePO queryById(
            @Param("projectId") Long projectId,
            @Param("id") Long id);

    OutlineNodePO queryRoot(@Param("projectId") Long projectId);

    List<OutlineNodePO> queryChildren(
            @Param("projectId") Long projectId,
            @Param("parentId") Long parentId);

    List<OutlineNodePO> queryChildrenForUpdate(
            @Param("projectId") Long projectId,
            @Param("parentId") Long parentId);

    List<OutlineNodePO> querySiblings(
            @Param("projectId") Long projectId,
            @Param("parentId") Long parentId);

    List<OutlineNodePO> queryByProject(@Param("projectId") Long projectId);

    Integer queryMaxSequenceNo(
            @Param("projectId") Long projectId,
            @Param("parentId") Long parentId);
}
