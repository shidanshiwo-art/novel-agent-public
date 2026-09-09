package cn.ninth.novel.domain.chapter.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;

import java.util.List;

/** 章节模型 Prompt Trace 的独立调试数据持久化端口。 */
public interface IPromptTraceRepository {

    /** 保存一次模型尝试的完整 Prompt、响应摘要和结果。 */
    void save(PromptTraceRecord trace);

    /** 按工作流查询模型调用 Trace；开发调试场景不做分页。 */
    default List<PromptTraceRecord> findByWorkflowId(String workflowId) {
        return List.of();
    }
}
