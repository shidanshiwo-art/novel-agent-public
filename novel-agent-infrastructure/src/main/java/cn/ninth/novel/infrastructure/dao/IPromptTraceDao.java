package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.PromptTracePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** chapter_model_trace 表访问接口。 */
@Mapper
public interface IPromptTraceDao {

    int insert(PromptTracePO trace);

    List<PromptTracePO> selectByWorkflowId(@Param("workflowId") String workflowId);
}
