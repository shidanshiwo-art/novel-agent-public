package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.CHAPTER_NUMBER;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.COMPLETED_STAGES;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.CONTEXT;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.CURRENT_NODE;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.DRAFT;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.FAILURE_MESSAGE;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.GENERATION_METRICS_DELTA;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.MEMORY;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.STORY_STATE_SNAPSHOT;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.HUMAN_DECISION;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.HUMAN_REVISE_ROUND;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.PROJECT_CODE;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.RETRY_COUNT;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.REVIEW_REPORT;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.REVISE_ROUND;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.REVISION_INSTRUCTION;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.WORKFLOW_STATUS;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.WORKFLOW_ID;

/**
 * 章节生成图的运行状态。
 * 该对象只负责在 LangGraph4j 节点之间传递领域产物与编排控制信息，
 * 属于章节生成领域工作流，不应直接暴露到 HTTP 契约中。
 */
public final class ChapterGraphState extends AgentState {

    /** LangGraph4j 创建 State 实例时使用的工厂。 */
    public static final AgentStateFactory<ChapterGraphState> FACTORY = ChapterGraphState::new;

    /**
     * State 合并规则。
     * 所有 State key 均在此显式注册，作为节点读写契约的单一事实源。
     * completedStages 使用去重 reducer，避免返修/人工往返时同一阶段重复堆积；
     * 计数器与快照字段使用 base 通道（last-write-wins），重试时整体覆盖。
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(PROJECT_CODE, baseWithoutDefault()),
            Map.entry(CHAPTER_NUMBER, baseWithoutDefault()),
            Map.entry(WORKFLOW_ID, baseWithoutDefault()),
            Map.entry(CONTEXT, baseWithoutDefault()),
            Map.entry(DRAFT, baseWithoutDefault()),
            Map.entry(REVIEW_REPORT, baseWithoutDefault()),
            Map.entry(MEMORY, baseWithoutDefault()),
            Map.entry(STORY_STATE_SNAPSHOT, baseWithoutDefault()),
            Map.entry(CURRENT_NODE, baseWithoutDefault()),
            Map.entry(HUMAN_DECISION, Channels.base(() -> "")),
            Map.entry(REVISION_INSTRUCTION, Channels.base(() -> "")),
            Map.entry(WORKFLOW_STATUS, baseWithoutDefault()),
            Map.entry(FAILURE_MESSAGE, baseWithoutDefault()),
            Map.entry(GENERATION_METRICS_DELTA, baseWithoutDefault()),
            Map.entry(REVISE_ROUND, Channels.base(() -> 0)),
            Map.entry(HUMAN_REVISE_ROUND, Channels.base(() -> 0)),
            Map.entry(RETRY_COUNT, Channels.base(() -> 0)),
            Map.entry(COMPLETED_STAGES, Channels.base(ChapterGraphState::mergeDistinctStages, ArrayList::new))
    );

    /** 可空字段不提供 null 默认值，避免 LangGraph4j 初始状态构造时丢失字段类型。 */
    private static <T> Channel<T> baseWithoutDefault() {
        return Channels.base((oldValue, newValue) -> newValue);
    }

    /**
     * completedStages 的合并规则：把新追加的阶段并入已有列表并按首次出现去重。
     * 节点每次返回 {@code List.of("阶段名")}，返修/人工往返会重复经过同一阶段，
     * 若直接追加会产生重复噪声，故在此按顺序去重。
     *
     * @param oldValue 已累积的阶段列表，可能为 null
     * @param newValue 本次节点返回的阶段列表，可能为 null
     * @return 去重后的新列表
     */
    private static List<String> mergeDistinctStages(List<String> oldValue, List<String> newValue) {
        List<String> merged = new ArrayList<>();
        if (oldValue != null) {
            merged.addAll(oldValue);
        }
        if (newValue != null) {
            for (String stage : newValue) {
                if (!merged.contains(stage)) {
                    merged.add(stage);
                }
            }
        }
        return merged;
    }

    public ChapterGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> projectCode() {
        return value(PROJECT_CODE);
    }

    public Optional<Integer> chapterNumber() {
        return value(CHAPTER_NUMBER);
    }

    public Optional<String> workflowId() {
        return value(WORKFLOW_ID);
    }

    public PromptTraceRecord promptTrace(
            String node,
            int attempt,
            String systemPrompt,
            String userPrompt
    ) {
        return PromptTraceRecord.now(
                workflowId().orElse(null),
                projectCode().orElse(null),
                chapterNumber().orElse(null),
                node,
                attempt,
                systemPrompt,
                userPrompt
        );
    }

    public Optional<ChapterContextAggregate> context() {
        return value(CONTEXT);
    }

    public Optional<String> draft() {
        return value(DRAFT);
    }

    public Optional<ReviewReportVO> reviewReport() {
        return value(REVIEW_REPORT);
    }

    public int reviseRound() {
        return this.<Integer>value(REVISE_ROUND).orElse(0);
    }

    public int retryCount() {
        return this.<Integer>value(RETRY_COUNT).orElse(0);
    }

    public Optional<ChapterMemoryVO> chapterMemory() {
        return value(MEMORY);
    }

    public Optional<StoryStateSnapshot> storyStateSnapshot() {
        return value(STORY_STATE_SNAPSHOT);
    }

    public List<String> completedStages() {
        return this.<List<String>>value(COMPLETED_STAGES)
                .map(List::copyOf)
                .orElseGet(List::of);
    }

    public Optional<String> currentNode() {
        return value(CURRENT_NODE);
    }

    public Optional<String> humanDecision() {
        return this.<String>value(HUMAN_DECISION)
                .filter(value -> !value.isBlank());
    }

    public Optional<String> revisionInstruction() {
        return this.<String>value(REVISION_INSTRUCTION)
                .filter(value -> !value.isBlank());
    }

    public int humanReviseRound() {
        return this.<Integer>value(HUMAN_REVISE_ROUND).orElse(0);
    }

    public Optional<String> workflowStatus() {
        return value(WORKFLOW_STATUS);
    }

    public Optional<String> failureMessage() {
        return value(FAILURE_MESSAGE);
    }
}
