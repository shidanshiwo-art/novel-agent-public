package cn.ninth.novel.domain.chapter.model.valobj.enums;

/** 章节工作流对调用方可见的执行状态。 */
public enum ChapterWorkflowStatusEnum {
    WAITING_HUMAN,
    /** REVIEW 报告校验重试耗尽，保留当前正文并等待人工选择恢复动作。 */
    REVIEW_FAILED,
    COMPLETED,
    ABORTED,
    CANCELLED
}
