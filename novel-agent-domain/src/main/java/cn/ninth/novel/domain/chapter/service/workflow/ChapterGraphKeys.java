package cn.ninth.novel.domain.chapter.service.workflow;

/**
 * 章节生成图的 State key 集合。
 * 节点只能通过这里定义的键读写状态，避免编排代码中散落字符串字面量。
 */
public final class ChapterGraphKeys {

    public static final String PROJECT_CODE = "projectCode";
    public static final String CHAPTER_NUMBER = "chapterNumber";
    public static final String WORKFLOW_ID = "workflowId";
    /** 当前工作流的 Memory 路由模式；随 checkpoint 一起保留，供 DRAFT/REVIEW 共用。 */
    public static final String MEMORY_MODE = "memoryMode";
    /** 当前 V1 对照实验变体；不进入模型 Prompt。 */
    public static final String GENERATION_VARIANT = "generationVariant";
    public static final String CONTEXT = "context";
    public static final String DRAFT = "draft";
    /** 当前 DRAFT 正文的临时 Source Version，不代表 Canonical 版本。 */
    public static final String SOURCE_VERSION = "sourceVersion";
    /** 只供 checkpoint/staging 使用的 Candidate 列表，不进入普通 Recall。 */
    public static final String MEMORY_CANDIDATES = "memoryCandidates";
    /** Candidate 列表的兼容别名，避免编排层重新定义 State key。 */
    public static final String CANDIDATES = MEMORY_CANDIDATES;
    public static final String REVIEW_REPORT = "reviewReport";
    /** 新 Review Pipeline 的一次审核会话标识。 */
    public static final String REVIEW_SESSION_ID = "reviewSessionId";
    /** 新 Review Pipeline 在各阶段间共享的上下文。 */
    public static final String REVIEW_CONTEXT = "reviewContext";
    /** 连续性审核阶段的结构化发现列表。 */
    public static final String CONTINUITY_FINDINGS = "continuityFindings";
    /** Phase A 由结构化状态产生的连续性冲突候选列表。 */
    public static final String CONFLICT_CANDIDATES = "conflictCandidates";
    /** 质量审核阶段的结构化发现列表。 */
    public static final String QUALITY_FINDINGS = "qualityFindings";
    /** 连续性与质量发现汇总后的返修计划。 */
    public static final String REPAIR_PLAN = "repairPlan";
    /** Regression 后仍需进入下一轮的 REQUIRED 子计划。 */
    public static final String REMAINING_REPAIR_PLAN = "remainingRepairPlan";
    /** 新 Pipeline 的自动返修轮次，最大为 2。 */
    public static final String REVISION_ROUND = "revisionRound";
    /** 最近一轮 Revision 前各 REQUIRED 项的受影响正文片段。 */
    public static final String REVISION_BEFORE_AFFECTED_TEXT =
            "revisionBeforeAffectedText";
    /** 最近一次返修项的回归检查结果。 */
    public static final String REGRESSION_RESULT = "regressionResult";
    /** Pipeline 当前最终决策，例如 PASS 或 HUMAN。 */
    public static final String FINAL_DECISION = "finalDecision";
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
