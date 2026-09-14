package cn.ninth.novel.domain.memory.adapter.repository;

import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsQuery;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;

import java.util.List;

/** 生产 Memory Retrieval 观测持久化端口。 */
public interface IMemoryRetrievalMetricsRepository {

    /** 保存一条不含正文的检索观测。 */
    void save(MemoryRetrievalObservation observation);

    /** 按项目、章节、Profile 和 generation/run 查询检索观测。 */
    List<MemoryRetrievalObservation> find(MemoryRetrievalMetricsQuery query);
}
