package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.MemoryRetrievalMetricsPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** memory_retrieval_metrics 表访问接口。 */
@Mapper
public interface IMemoryRetrievalMetricsDao {

    int insert(MemoryRetrievalMetricsPO metrics);

    List<MemoryRetrievalMetricsPO> query(
            @Param("projectCode") String projectCode,
            @Param("chapterNumber") Integer chapterNumber,
            @Param("profile") String profile,
            @Param("generationId") String generationId
    );
}
