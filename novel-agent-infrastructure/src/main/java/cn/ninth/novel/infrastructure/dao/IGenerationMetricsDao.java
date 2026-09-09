package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.infrastructure.dao.po.GenerationMetricsPO;
import cn.ninth.novel.infrastructure.dao.po.GenerationMetricsSummaryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** generation_metrics 表访问接口。 */
@Mapper
public interface IGenerationMetricsDao {

    int insertOrUpdate(GenerationMetricsPO metrics);

    GenerationMetricsPO queryByIdentity(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber,
            @Param("generationSessionId") String generationSessionId
    );

    List<GenerationMetricsPO> queryByChapter(
            @Param("projectId") Long projectId,
            @Param("chapterNumber") Integer chapterNumber
    );

    GenerationMetricsSummaryPO queryProjectSummary(@Param("projectId") Long projectId);
}
