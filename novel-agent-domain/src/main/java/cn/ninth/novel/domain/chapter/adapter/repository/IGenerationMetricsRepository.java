package cn.ninth.novel.domain.chapter.adapter.repository;

import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;

import java.util.List;

/** 章节生成指标持久化端口。 */
public interface IGenerationMetricsRepository {

    /**
     * 按项目、章节和生成会话保存指标；相同业务键重复保存时覆盖当前统计值。
     */
    void save(GenerationMetricsDO metrics);

    /** 按项目、章节和生成会话查询指标。 */
    GenerationMetricsDO find(
            Long projectId,
            Integer chapterNumber,
            String generationSessionId
    );

    /** 查询一个章节的全部生成会话指标。 */
    List<GenerationMetricsDO> findByChapter(Long projectId, Integer chapterNumber);

    /** 查询项目下全部生成会话的汇总指标。 */
    GenerationMetricsSummaryVO summarizeByProject(Long projectId);
}
