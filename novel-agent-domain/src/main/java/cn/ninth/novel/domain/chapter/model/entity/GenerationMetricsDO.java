package cn.ninth.novel.domain.chapter.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一次章节生成会话的指标。
 *
 * <p>该对象只描述生成过程的业务统计，不参与章节正文和章节记忆的状态流转。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenerationMetricsDO {

    /** 所属小说项目主键。 */
    private Long projectId;
    /** 项目内章节号。 */
    private Integer chapterNumber;
    /** 章节生成会话编号。 */
    private String generationSessionId;
    /** DRAFT 模型调用次数。 */
    private Integer draftCalls;
    /** REVIEW 模型调用次数。 */
    private Integer reviewCalls;
    /** REVISE 模型调用次数。 */
    private Integer reviseCalls;
    /** COMPRESSION 模型调用次数。 */
    private Integer compressionCalls;
    /** REVISE 修订轮数。 */
    private Integer reviseRounds;
    /** 生成流程消耗的重试次数。 */
    private Integer retryCount;
    /** 是否发生人工介入。 */
    private Boolean humanIntervened;
    /** 整个生成流程耗时，单位为毫秒。 */
    private Long generationDurationMs;
    /** 生成生命周期开始时间。 */
    private LocalDateTime startedAt;
    /** 生成生命周期结束时间；等待人工时为空。 */
    private LocalDateTime endedAt;
    /** 模型输入 Token 数。 */
    private Long inputTokens;
    /** 模型输出 Token 数。 */
    private Long outputTokens;
    /** 模型总 Token 数。 */
    private Long totalTokens;
    /** 最终正文词数。 */
    private Integer finalWordCount;
    /** 指标创建时间。 */
    private LocalDateTime createdAt;
    /** 指标最后更新时间。 */
    private LocalDateTime updatedAt;
}
