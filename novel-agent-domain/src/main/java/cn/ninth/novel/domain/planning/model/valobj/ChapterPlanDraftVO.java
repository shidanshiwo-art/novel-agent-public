package cn.ninth.novel.domain.planning.model.valobj;

/**
 * ChapterPlan 模型响应的兼容结构；正式标题由 ARC.title 提供，规划服务不采信 title。
 */
public record ChapterPlanDraftVO(
        String title,
        String summary
) {
}
