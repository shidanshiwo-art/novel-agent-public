package cn.ninth.novel.domain.chapter.service.workflow;

/**
 * 章节生成图的 State key 集合。
 * 节点只能通过这里定义的键读写状态，避免编排代码中散落字符串字面量。
 */
public final class ChapterGraphKeys {

    public static final String PROJECT_CODE = "projectCode";
    public static final String CHAPTER_NUMBER = "chapterNumber";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String CONTEXT = "context";
    public static final String DRAFT = "draft";
    public static final String REVIEW_REPORT = "reviewReport";
    public static final String REVISE_ROUND = "reviseRound";
    public static final String RETRY_COUNT = "retryCount";
    public static final String MEMORY = "memory";
    public static final String STORY_STATE_SNAPSHOT = "storyStateSnapshot";
    public static final String COMPLETED_STAGES = "completedStages";
    public static final String CURRENT_NODE = "currentNode";
    public static final String HUMAN_DECISION = "humanDecision";
    public static final String REVISION_INSTRUCTION = "revisionInstruction";
    public static final String HUMAN_REVISE_ROUND = "humanReviseRound";
    public static final String WORKFLOW_STATUS = "workflowStatus";
    public static final String FAILURE_MESSAGE = "failureMessage";
    /** 节点到指标旁路采集器的单次执行增量。 */
    public static final String GENERATION_METRICS_DELTA = "generationMetricsDelta";

    private ChapterGraphKeys() {
    }
}
