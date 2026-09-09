package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionSnapshot;

import java.util.Optional;
import java.util.List;

/**
 * 章节生成领域服务入口。
 *
 * <p>接口只使用领域参数和领域结果，不依赖 HTTP 请求、响应 DTO。</p>
 */

public interface IChapterService {

    /**
     * 生成指定项目的一个章节。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 待生成章节号
     * @return 章节生成结果
     */
    ChapterGenerationResultVO generateChapter(String projectCode, int chapterNumber);

    /**
     * 创建章节生成会话并异步启动章节工作流。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 待生成章节号
     * @return 可用于后续查询或恢复工作流的标识
     */
    default String createGenerationSession(String projectCode, int chapterNumber) {
        throw new UnsupportedOperationException("当前章节服务未提供异步生成会话能力");
    }

    /**
     * 查询指定项目和章节可供页面恢复的生成会话。
     *
     * <p>活动会话优先；没有活动会话时，可返回短期保留的最近终态快照。</p>
     *
     * @return 活动或短期保留会话快照；没有可恢复会话时返回空
     */
    default Optional<ChapterGenerationSessionSnapshot> findActiveGenerationSession(
            String projectCode,
            int chapterNumber
    ) {
        throw new UnsupportedOperationException("当前章节服务未提供活动生成会话查询能力");
    }

    /**
     * 停止 DRAFT 流式生成，或结束 REVIEW_FAILED 检查点，并将会话标记为已取消。
     */
    default void stopGenerationSession(String workflowId) {
        throw new UnsupportedOperationException("当前章节服务未提供生成会话停止能力");
    }

    /**
     * 接受当前已完整生成的正文，跳过剩余自动审核和改稿并继续派生数据持久化。
     */
    default void acceptGenerationSession(String workflowId) {
        throw new UnsupportedOperationException("当前章节服务未提供生成会话接受能力");
    }

    /**
     * 重新同步人工修改章节的正文派生数据。
     *
     * <p>只执行 COMPRESSION 和派生数据持久化，不重新生成或审核正文。</p>
     */
    default ChapterGenerationResultVO resyncChapterDerivedData(
            String projectCode,
            int chapterNumber
    ) {
        throw new UnsupportedOperationException("当前章节服务未提供派生数据同步能力");
    }

    /** 查询一个章节的全部生成会话指标。 */
    default List<GenerationMetricsDO> findGenerationMetrics(
            String projectCode,
            int chapterNumber
    ) {
        return List.of();
    }

    /** 查询项目范围的生成指标汇总。 */
    default GenerationMetricsSummaryVO summarizeGenerationMetrics(
            String projectCode
    ) {
        return GenerationMetricsSummaryVO.empty();
    }

    /** 查询一个生成会话的指标。 */
    default GenerationMetricsDO findGenerationMetrics(
            String projectCode,
            int chapterNumber,
            String generationSessionId
    ) {
        return null;
    }

    /**
     * 使用一次人工决策恢复暂停的章节工作流；REVIEW_FAILED 状态支持 REVIEW、PASS、ABORT。
     *
     * @param workflowId 生成接口返回的工作流标识
     * @param decision 本次人工决策
     * @return 恢复后的工作流结果
     */
    ChapterGenerationResultVO resumeChapter(
            String workflowId,
            HumanDecisionEnum decision
    );

    /**
     * 使用人工决策和可选的具体修改意见恢复章节工作流。
     */
    default ChapterGenerationResultVO resumeChapter(
            String workflowId,
            HumanDecisionEnum decision,
            String revisionInstruction
    ) {
        return resumeChapter(workflowId, decision);
    }
}
