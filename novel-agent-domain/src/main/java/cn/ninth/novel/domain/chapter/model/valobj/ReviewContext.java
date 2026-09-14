package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Review Pipeline 在各审核阶段之间共享的上下文。
 *
 * <p>continuityContext 保存已经完成实体筛选的 Memory V1 投影，
 * qualityContext 保存质量审核的最小上下文投影；二者都在准备阶段一次性构造。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReviewContext {

    /** 当前章节计划。 */
    private ChapterPlanEntity chapterPlan;

    /** 当前待审核正文。 */
    private String currentDraft;

    /** 连续性审核所需的实体相关 Memory V1 投影。 */
    private Object continuityContext;

    /** 质量审核所需的最小上下文投影，当前实现为 QualityContext。 */
    private Object qualityContext;
}
