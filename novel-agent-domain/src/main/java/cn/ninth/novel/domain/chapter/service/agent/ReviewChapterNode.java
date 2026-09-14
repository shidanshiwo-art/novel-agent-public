package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import cn.ninth.novel.domain.memory.service.MemoryContextProviderMetricsRecorder;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendLineIfPresent;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendList;
import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.value;
import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVIEW_SYSTEM_PROMPT;

/**
 * REVIEW 章节审稿节点。
 *
 * @author ninth
 * @date 2026/8/24
 * @description
 */
@Slf4j
@Service
public class ReviewChapterNode implements NodeAction<ChapterGraphState> {

    private static final int MAX_REVIEW_CHARACTERS = 4;
    private static final int MAX_EVIDENCE_ITEMS_PER_TOPIC = 4;
    private static final Pattern DAY_TIME_PATTERN = Pattern.compile(
            "第\\s*(\\d+)\\s*(?:日|天)(?:\\s*(\\d{1,2})(?::|点)(\\d{0,2}))?"
    );
    private static final Pattern CLOCK_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,2})(?::|点)(\\d{1,2})?"
    );
    private static final Pattern RULE_FORBIDDEN_PATTERN = Pattern.compile(
            "([^，。；;\\n]{1,24})(?:不能|不可|禁止|不得|不允许)([^，。；;\\n]{1,24})"
    );
    private static final Pattern NEGATED_PATTERN = Pattern.compile(
            "(?:不能|不可|禁止|不得|不允许)\\s*"
    );
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fffA-Za-z0-9]{1,24}?)(?:当前|仍然|仍|此刻)?"
                    + "(?:位于|处于|(?<!存)在|身在|待在|坐在|躺在)([^，。；;\\n]{1,30})"
    );
    private static final Pattern DESTINATION_LOCATION_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fffA-Za-z0-9]{1,24}?)(?:当前|仍然|仍|此刻)?"
                    + "(?:去了|前往|进入|到达|抵达|回到|赶到|来到|来到了)"
                    + "([^，。；;\\n]{1,30})"
    );
    private static final Pattern FROM_LOCATION_STATE_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fffA-Za-z0-9]{1,24}?)从([^，。；;\\n]{1,20})"
                    + "(?=(?:弹|醒|起|站|坐|躺|睡|蹲|趴|走|冲|跑|下|床))"
    );
    private static final Pattern POSSESSION_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fffA-Za-z0-9]{1,24}?)(?:仍然|仍|当前|手里|手中)?"
                    + "(?:持有|拿着|携带|保管|握着|掌握|手里有|手中有)"
                    + "([^，。；;\\n]{1,24})"
    );
    private static final Pattern REVERSE_POSSESSION_PATTERN = Pattern.compile(
            "([^，。；;\\n]{1,24}?)(?:由|被)([\\u4e00-\\u9fffA-Za-z0-9]{1,24})"
                    + "(?:持有|拿着|携带|保管|握着|掌握)"
    );
    private static final Pattern INJURY_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fffA-Za-z0-9]{1,24}?)([^，。；;\\n]{0,18})"
                    + "(伤势|伤口|骨折|重伤|昏迷|失明|断臂|断腿|负伤)"
    );
    private static final Pattern KNOWLEDGE_PROVENANCE_PATTERN = Pattern.compile(
            "(?:听说|听闻|亲眼看见|观察到|有人告诉|告知|读到|查阅|根据|依据|推断|推测|从)"
    );
    private static final Pattern ABILITY_USE_PATTERN = Pattern.compile(
            "(?:触发|发动|使用|施展|激活|启动|感知|释放|调用|运转|再次使用)"
    );
    private static final Pattern MOVE_LOCATION_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fffA-Za-z0-9]{1,24}?)从([^，。；;\\n]{1,20})"
                    + "(?:转移|带|移)到([^，。；;\\n]{1,24})"
    );
    private static final List<String> DEATH_WORDS = List.of(
            "死亡", "死了", "已死", "死去", "被杀", "杀死", "dead");
    private static final List<String> LIFE_WORDS = List.of(
            "活着", "存活", "还活着", "复活", "alive");
    private static final List<String> RECOVERY_WORDS = List.of(
            "复活", "救活", "恢复", "痊愈", "治愈", "治疗", "疗伤", "服药", "休养", "包扎");
    private static final List<String> ACTIVATION_WORDS = List.of(
            "主动激活", "激活", "启动", "发动", "施展", "使用");
    private static final List<String> COST_WORDS = List.of(
            "消耗", "代价", "灵息", "体力", "寿命", "精力");
    private static final List<String> TRANSFER_WORDS = List.of(
            "交给", "转交", "递给", "交到", "移交", "夺走", "接过", "取走", "转移", "带到");
    private static final Set<String> COMMON_REVIEW_TOKENS = Set.of(
            "当前", "仍然", "仍在", "位于", "存在", "确认", "知道", "得知", "来源", "正文", "章节");

    private static final String ISSUE_CHARACTER_PRESENCE = "CHARACTER_PRESENCE";
    private static final String ISSUE_ITEM_LOCATION = "ITEM_LOCATION";
    private static final String ISSUE_INJURY_STATE = "INJURY_PHYSICAL_STATE";
    private static final String ISSUE_ABILITY_RULE = "ABILITY_RULE";
    private static final String ISSUE_WORLD_RULE = "WORLD_RULE";
    private static final String ISSUE_KNOWLEDGE = "KNOWLEDGE_PROVENANCE";
    private static final String ISSUE_TIMELINE = "TIMELINE";
    private static final String ISSUE_SOURCE_VERSION = "SOURCE_VERSION";

    private final IChapterModelPort chapterModelPort;
    private final ChapterModelRetryExecutor retryExecutor;
    private final PromptTraceRecorder promptTraceRecorder;
    private final MemoryContextProviderMetricsRecorder metricsRecorder;

    public ReviewChapterNode(IChapterModelPort chapterModelPort) {
        this(
                chapterModelPort,
                new ChapterModelRetryExecutor(),
                new PromptTraceRecorder(),
                new MemoryContextProvider(),
                new MemoryContextProviderMetricsRecorder()
        );
    }

    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                new PromptTraceRecorder(),
                new MemoryContextProvider(),
                new MemoryContextProviderMetricsRecorder()
        );
    }

    @Autowired
    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder,
            MemoryContextProviderMetricsRecorder metricsRecorder
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                promptTraceRecorder,
                metricsRecorder.provider(),
                metricsRecorder
        );
    }

    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder,
            MemoryContextProvider memoryContextProvider
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                promptTraceRecorder,
                memoryContextProvider,
                new MemoryContextProviderMetricsRecorder(memoryContextProvider,
                        new cn.ninth.novel.domain.memory.service.MemoryRetrievalMetricsCollector())
        );
    }

    /** 保留既有测试与调用方使用的三参数构造入口。 */
    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder
    ) {
        this(
                chapterModelPort,
                retryExecutor,
                promptTraceRecorder,
                new MemoryContextProvider(),
                new MemoryContextProviderMetricsRecorder()
        );
    }

    public ReviewChapterNode(
            IChapterModelPort chapterModelPort,
            ChapterModelRetryExecutor retryExecutor,
            PromptTraceRecorder promptTraceRecorder,
            MemoryContextProvider memoryContextProvider,
            MemoryContextProviderMetricsRecorder metricsRecorder
    ) {
        this.chapterModelPort = chapterModelPort;
        this.retryExecutor = retryExecutor;
        this.promptTraceRecorder = promptTraceRecorder;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        ChapterContextAggregate context = state.context()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "REVIEW 节点缺少 context"
                ));
        requireChapterOutline(context);
        String draft = state.draft()
                .orElseThrow(() -> AppException.internal(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "REVIEW 节点缺少 draft"
                ));
        AtomicReference<GenerationMetricsDelta> metrics =
                new AtomicReference<>(GenerationMetricsDelta.empty());
        try {
            ReviewEvidenceBundle evidence = buildReviewEvidence(context, draft, state);
            ChapterModelRetryExecutor.RetryResult<ReviewReportVO> result = reviewWithRetry(
                    context,
                    draft,
                    state.retryCount(),
                    state,
                    evidence,
                    delta -> metrics.updateAndGet(current -> current.plus(delta))
            );
            ReviewReportVO report = result.value().withAdditionalIssues(evidence.deterministicIssues());
            return Map.of(
                    ChapterGraphKeys.REVIEW_REPORT, report,
                    ChapterGraphKeys.CURRENT_NODE, "REVIEW",
                    ChapterGraphKeys.COMPLETED_STAGES, List.of("REVIEW"),
                    ChapterGraphKeys.RETRY_COUNT, state.retryCount() + result.retryCount(),
                    ChapterGraphKeys.FAILURE_MESSAGE, "",
                    ChapterGraphKeys.GENERATION_METRICS_DELTA, metrics.get()
            );
        } catch (AppException exception) {
            if (!ResponseCode.E0004.getCode().equals(exception.getCode())) {
                throw exception;
            }
            log.warn(
                    "REVIEW 报告校验重试耗尽，保留当前正文并转人工恢复，detail={}",
                    exception.getInternalDetail()
            );
            return Map.of(
                    ChapterGraphKeys.CURRENT_NODE, "REVIEW",
                    ChapterGraphKeys.COMPLETED_STAGES, List.of("REVIEW"),
                    ChapterGraphKeys.RETRY_COUNT,
                    Math.max(state.retryCount(), ChapterModelRetryExecutor.MAX_RETRIES),
                    ChapterGraphKeys.WORKFLOW_STATUS,
                    ChapterWorkflowStatusEnum.REVIEW_FAILED.name(),
                    ChapterGraphKeys.FAILURE_MESSAGE,
                    readableFailureMessage(exception),
                    ChapterGraphKeys.GENERATION_METRICS_DELTA,
                    metrics.get()
            );
        }
    }

    private ReviewReportVO review(ChapterContextAggregate context, String draft) {
        ReviewEvidenceBundle evidence = buildReviewEvidence(context, draft, null);
        return reviewWithRetry(context, draft, 0, null, evidence, ignored -> { })
                .value()
                .withAdditionalIssues(evidence.deterministicIssues());
    }

    private ChapterModelRetryExecutor.RetryResult<ReviewReportVO> reviewWithRetry(
            ChapterContextAggregate context,
            String draft,
            int usedRetryCount
    ) {
        return reviewWithRetry(
                context,
                draft,
                usedRetryCount,
                null,
                buildReviewEvidence(context, draft, null),
                ignored -> { }
        );
    }

    private ChapterModelRetryExecutor.RetryResult<ReviewReportVO> reviewWithRetry(
            ChapterContextAggregate context,
            String draft,
            int usedRetryCount,
            ChapterGraphState state
    ) {
        return reviewWithRetry(
                context,
                draft,
                usedRetryCount,
                state,
                buildReviewEvidence(context, draft, state),
                ignored -> { }
        );
    }

    private ChapterModelRetryExecutor.RetryResult<ReviewReportVO> reviewWithRetry(
            ChapterContextAggregate context,
            String draft,
            int usedRetryCount,
            ChapterGraphState state,
            ReviewEvidenceBundle evidence,
            Consumer<GenerationMetricsDelta> metricsConsumer
    ) {
        String systemPrompt = REVIEW_SYSTEM_PROMPT;
        String userPrompt = buildReviewPrompt(context, draft, evidence, state);

        ChapterModelRetryExecutor.RetryResult<ReviewReportVO> result = retryExecutor.execute(
                usedRetryCount,
                attempt -> {
                    PromptTraceRecord trace = state == null
                            ? null
                            : state.promptTrace(
                                    "REVIEW",
                                    attempt,
                                    systemPrompt,
                                    userPrompt
                            );
                    long startedAt = System.nanoTime();
                    String responseText = null;
                    try {
                        ChapterModelResponse<ReviewReportVO> response =
                                chapterModelPort.callWithRawResponse(
                                        systemPrompt,
                                        userPrompt,
                                        ReviewReportVO.class,
                                        "REVIEW",
                                        attempt + 1,
                                        generationVariant(state).reviewReasoningEnabledOverride()
                                );
                        metricsConsumer.accept(GenerationMetricsDelta.tokens(
                                response == null ? null : response.usage()));
                        responseText = promptTraceRecorder.responseText(
                                response == null ? null : response.rawText(),
                                response == null ? null : response.value()
                        );
                        ReviewReportVO review = response == null ? null : response.value();
                        if (review == null) {
                            throw AppException.user(
                                    ResponseCode.E0004.getCode(),
                                    ResponseCode.E0004.getMessage()
                            );
                        }
                        validateReviewReport(review, draft);
                        metricsConsumer.accept(GenerationMetricsDelta.forCall("REVIEW", null));
                        promptTraceRecorder.recordSuccess(
                                trace,
                                responseText,
                                elapsedMillis(startedAt)
                        );
                        return review;
                    } catch (RuntimeException exception) {
                        if (exception instanceof ChapterModelResponseException responseException) {
                            metricsConsumer.accept(GenerationMetricsDelta.tokens(
                                    responseException.usage()));
                        }
                        promptTraceRecorder.recordFailure(
                                trace,
                                responseText,
                                exception,
                                elapsedMillis(startedAt)
                        );
                        throw exception;
                    }
                },
                exception -> ResponseCode.E0004.getCode().equals(exception.getCode())
        );

        log.info("review 节点:{}", result.value());
        return result;
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    String buildReviewPrompt(ChapterContextAggregate context, String draft) {
        return buildReviewPrompt(
                context,
                draft,
                buildReviewEvidence(context, draft, null)
        );
    }

    private String buildReviewPrompt(
            ChapterContextAggregate context,
            String draft,
            ReviewEvidenceBundle evidence
    ) {
        return buildReviewPrompt(context, draft, evidence, null);
    }

    private String buildReviewPrompt(
            ChapterContextAggregate context,
            String draft,
            ReviewEvidenceBundle evidence,
            ChapterGraphState state
    ) {
        StringBuilder prompt = new StringBuilder(65536);
        appendReviewConstraints(prompt, context);
        appendChapterPlan(prompt, context);
        MemoryMode memoryMode = memoryMode(context, state);
        ChapterMemoryVO previousMemory = memoryMode == MemoryMode.V1
                ? null : previousChapterMemory(context);
        appendCharacters(prompt, context, draft, previousMemory);
        appendHistory(prompt, context, previousMemory, memoryMode);
        appendEvidencePacks(prompt, evidence.packs(), generationVariant(state));
        appendDraft(prompt, draft);

        return prompt.toString();
    }

    private void appendEvidencePacks(
            StringBuilder prompt,
            Map<ReviewEvidencePackType, MemoryContextPack> packs,
            ChapterGenerationVariant generationVariant
    ) {
        prompt.append("## 分主题审稿证据\n")
                .append("## 分实体连续性审稿证据\n")
                .append("以下 evidence pack 只包含当前正文涉及实体的相关事实、必要的旧事实、事件历史和来源证据；无证据时不得把猜测当成问题。\n");
        for (ReviewEvidencePackType packType : ReviewEvidencePackType.values()) {
            prompt.append("### ").append(packType.label()).append('\n');
            MemoryContextPack pack = packs.get(packType);
            for (ReviewTopic topic : packType.topics()) {
                prompt.append("### ").append(topic.label()).append('\n');
            }
            if (pack == null || pack.items().isEmpty()) {
                prompt.append("无\n");
                continue;
            }
            for (MemoryContextItem item : pack.items()) {
                prompt.append("- [第")
                        .append(item.sourceChapter())
                        .append("章][")
                        .append(categoryLabel(item.category()))
                        .append("] ")
                        .append(item.content())
                        .append('\n');
            }
        }
        prompt.append("\n");
        prompt.append("## 确定性与语义检查边界\n")
                .append(generationVariant.reviewCheckerEnabled()
                        ? "代码已经检查明确的独占位置、物品持有、生死变化、伤势恢复、时间倒退、来源版本和明确禁止规则。"
                        : "本组不使用额外的代码确定性预检；模型只能依据证据包进行连续性判断。")
                .append("模型只对证据包支持的知识边界、隐含能力条件、未说明的返回/移动和因果不一致做语义复核。\n\n")
                .append("## 语义复核重点\n")
                .append(generationVariant.reviewCheckerEnabled()
                        ? "除上述确定性连续性检查外，仅对有正文证据的问题进行语义复核：猜测是否越级为确认、因果是否跳跃、能力语义是否违反设定、人物是否知道其不应知道的信息。\n\n"
                        : "仅对有正文证据的问题进行语义复核：猜测是否越级为确认、因果是否跳跃、能力语义是否违反设定、人物是否知道其不应知道的信息。\n\n");
    }

    private String categoryLabel(MemoryContextCategory category) {
        return switch (category) {
            case RULES -> "世界规则";
            case OPEN_LOOPS -> "未解决问题";
            case CURRENT_STATES -> "当前状态";
            case CONSOLIDATED -> "已确认摘要";
            case EPISODES -> "章节事件";
        };
    }

    private ReviewEvidenceBundle buildReviewEvidence(
            ChapterContextAggregate context,
            String draft,
            ChapterGraphState state
    ) {
        List<MemoryContextItem> candidates = state != null
                && state.memoryMode() == MemoryMode.V1
                ? canonicalMemoryItems(context)
                : reviewMemoryItems(context, memoryMode(context, state));
        List<MemoryContextItem> relevantCandidates = relevantMemoryItems(context, draft, candidates);
        Map<ReviewEvidencePackType, MemoryContextPack> packs = new LinkedHashMap<>();
        for (ReviewEvidencePackType packType : ReviewEvidencePackType.values()) {
            List<MemoryContextItem> relevant = relevantCandidates.stream()
                    .filter(item -> packType.relevant(item, draft))
                    .limit(MAX_EVIDENCE_ITEMS_PER_TOPIC * 3L)
                    .toList();
            packs.put(
                    packType,
                    metricsRecorder.provide(
                            MemoryQuerySpec.review(),
                            packType.budget(),
                            relevant,
                            currentChapterNumber(context),
                            projectCode(context, state),
                            generationId(state),
                            memoryMode(context, state)
                    )
            );
        }
        List<ReviewIssueVO> deterministicIssues = state == null
                || !generationVariant(state).reviewCheckerEnabled()
                ? List.of()
                : runDeterministicChecks(context, draft, state, packs);
        return new ReviewEvidenceBundle(deterministicIssues, packs);
    }

    private ChapterGenerationVariant generationVariant(ChapterGraphState state) {
        return state == null
                ? ChapterGenerationVariant.defaultVariant() : state.generationVariant();
    }

    /**
     * 将 REVIEW 所需的历史压缩成小候选集。
     * 这里只使用直接上一章压缩记忆和当前状态快照，不把整个 recent window 传给模型。
     */
    private List<MemoryContextItem> reviewMemoryItems(
            ChapterContextAggregate context,
            MemoryMode memoryMode
    ) {
        List<MemoryContextItem> items = new ArrayList<>();
        MemoryContextPack canonicalReviewPack = context.getReviewMemoryContextPack();
        if (memoryMode != MemoryMode.LEGACY
                && canonicalReviewPack != null
                && !canonicalReviewPack.items().isEmpty()) {
            items.addAll(canonicalReviewPack.items());
        }
        ChapterMemoryVO previousMemory = previousChapterMemory(context);
        int sourceChapter = previousMemory == null || previousMemory.getChapterNumber() == null
                ? previousChapterNumber(context)
                : previousMemory.getChapterNumber();
        if (previousMemory != null) {
            addMemoryItem(items, "review-episode-summary", MemoryContextCategory.EPISODES,
                    previousMemory.getShortSummary(), sourceChapter, memoryMode != MemoryMode.V1);
            addMemoryItems(items, "review-episode-event-", MemoryContextCategory.EPISODES,
                    previousMemory.getKeyEvents(), sourceChapter, memoryMode != MemoryMode.V1);
            addMemoryItem(items, "review-episode-hook", MemoryContextCategory.EPISODES,
                    previousMemory.getEndingHook(), sourceChapter, memoryMode != MemoryMode.V1);
            addMemoryItems(items, "review-open-loop-", MemoryContextCategory.OPEN_LOOPS,
                    previousMemory.getUnresolved(), sourceChapter, memoryMode != MemoryMode.V1);
        }

        ChapterHistoryVO history = context.getHistory();
        StoryStateSnapshot snapshot = history == null
                ? StoryStateSnapshot.empty() : history.getStoryStateSnapshot();
        addMemoryItems(items, "review-state-resource-", MemoryContextCategory.CURRENT_STATES,
                snapshot.resources(), sourceChapter, memoryMode != MemoryMode.V1);
        addMemoryItems(items, "review-state-ability-", MemoryContextCategory.CURRENT_STATES,
                snapshot.abilities(), sourceChapter, memoryMode != MemoryMode.V1);
        addMemoryItems(items, "review-state-knowledge-", MemoryContextCategory.CURRENT_STATES,
                snapshot.knowledge(), sourceChapter, memoryMode != MemoryMode.V1);
        addMemoryItems(items, "review-state-presence-", MemoryContextCategory.CURRENT_STATES,
                snapshot.presence(), sourceChapter, memoryMode != MemoryMode.V1);
        List<String> rules = ruleTexts(context.getStoryBible() == null
                ? null : context.getStoryBible().getHardRulesJson());
        addMemoryItems(items, "review-rule-", MemoryContextCategory.RULES, rules,
                currentChapterNumber(context), false);
        return List.copyOf(items);
    }

    private List<MemoryContextItem> canonicalMemoryItems(
            ChapterContextAggregate context
    ) {
        MemoryContextPack pack = context == null
                ? null : context.getReviewMemoryContextPack();
        if (pack == null && context != null) {
            pack = context.getMemoryContextPack();
        }
        // V1 的 ContextPack 已经由路由器决定是否发生过允许的 Legacy fallback；
        // Canonical 命中时这里自然只有 Canonical，fallback 命中时保留其结果供 REVIEW 使用。
        return pack == null ? List.of() : pack.items().stream()
                .sorted(java.util.Comparator
                        .comparingInt(MemoryContextItem::sourceChapter)
                        .thenComparing(item -> item.order() == null
                                ? Integer.MAX_VALUE : item.order())
                        .thenComparing(MemoryContextItem::itemId))
                .toList();
    }

    /**
     * 先按当前正文的实体和检查信号收窄候选，再交给 REVIEW 预算选择。
     * 这样旧事实只有在与当前人物、物品、能力或时间线发生关联时才会进入对应 pack。
     */
    private List<MemoryContextItem> relevantMemoryItems(
            ChapterContextAggregate context,
            String draft,
            List<MemoryContextItem> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> characterNames = characterNames(context);
        Set<String> draftTokens = meaningfulTokens(draft);
        return candidates.stream()
                .filter(item -> hasEntityOverlap(item.content(), draft, characterNames, draftTokens)
                        || item.category() == MemoryContextCategory.RULES
                        && !ruleSubject(item.content()).isBlank()
                        && containsNormalized(draft, ruleSubject(item.content())))
                .distinct()
                .toList();
    }

    private Set<String> characterNames(ChapterContextAggregate context) {
        Set<String> names = new LinkedHashSet<>();
        if (context == null || context.getCharacters() == null) {
            return names;
        }
        for (StoryCharacterEntity character : context.getCharacters()) {
            if (character != null && character.getName() != null
                    && !character.getName().isBlank()) {
                names.add(character.getName().trim());
            }
        }
        return names;
    }

    private boolean hasEntityOverlap(
            String historicalText,
            String draft,
            Set<String> characterNames,
            Set<String> draftTokens
    ) {
        if (historicalText == null || draft == null) {
            return false;
        }
        for (String characterName : characterNames) {
            if (containsNormalized(draft, characterName)
                    && containsNormalized(historicalText, characterName)) {
                return true;
            }
        }
        Set<String> historicalTokens = meaningfulTokens(historicalText);
        for (String token : historicalTokens) {
            if (token.length() >= 2 && draftTokens.contains(token)) {
                return true;
            }
        }
        return sharedChineseFragment(historicalText, draft);
    }

    private boolean sharedChineseFragment(String left, String right) {
        Set<String> leftFragments = chineseFragments(left);
        Set<String> rightFragments = chineseFragments(right);
        for (String fragment : leftFragments) {
            if (rightFragments.contains(fragment) && !COMMON_REVIEW_TOKENS.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> chineseFragments(String text) {
        Set<String> fragments = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return fragments;
        }
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fff]{2,}").matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            for (int length = 2; length <= Math.min(6, value.length()); length++) {
                for (int start = 0; start + length <= value.length(); start++) {
                    String fragment = value.substring(start, start + length);
                    if (!COMMON_REVIEW_TOKENS.contains(fragment)) {
                        fragments.add(fragment);
                    }
                }
            }
        }
        return fragments;
    }

    private Set<String> meaningfulTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fff]{2,12}|[A-Za-z][A-Za-z0-9_-]{1,}")
                .matcher(text);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isBlank() && !COMMON_REVIEW_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String projectCode(
            ChapterContextAggregate context,
            ChapterGraphState state
    ) {
        if (state != null) {
            String projectCode = state.projectCode().orElse(null);
            if (projectCode != null && !projectCode.isBlank()) {
                return projectCode;
            }
        }
        return context == null || context.getProject() == null
                ? null : context.getProject().getProjectCode();
    }

    private String generationId(ChapterGraphState state) {
        return state == null ? null : state.workflowId().orElse(null);
    }

    private MemoryMode memoryMode(
            ChapterContextAggregate context,
            ChapterGraphState state
    ) {
        return state == null ? MemoryMode.defaultMode() : state.memoryMode();
    }

    private void addMemoryItem(
            List<MemoryContextItem> items,
            String id,
            MemoryContextCategory category,
            String content,
            int sourceChapter,
            boolean legacySource
    ) {
        if (content != null && !content.isBlank()) {
            items.add(legacySource
                    ? MemoryContextItem.bridge(id, category, content.trim(), sourceChapter)
                    : MemoryContextItem.of(id, category, content.trim(), sourceChapter));
        }
    }

    private void addMemoryItems(
            List<MemoryContextItem> items,
            String idPrefix,
            MemoryContextCategory category,
            List<String> contents,
            int sourceChapter,
            boolean legacySource
    ) {
        if (contents == null) {
            return;
        }
        int index = 0;
        for (String content : contents) {
            addMemoryItem(items, idPrefix + index++, category, content, sourceChapter,
                    legacySource);
        }
    }

    private List<String> ruleTexts(String storedRules) {
        if (storedRules == null || storedRules.isBlank()) {
            return List.of();
        }
        String text = storedRules.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        List<String> rules = new ArrayList<>();
        for (String part : text.split("\\s*,\\s*|\\R")) {
            String rule = part.trim();
            if (rule.startsWith("\"") && rule.endsWith("\"")) {
                rule = rule.substring(1, rule.length() - 1);
            }
            rule = rule.replace("\\\"", "\"").trim();
            if (!rule.isBlank()) {
                rules.add(rule);
            }
        }
        return List.copyOf(rules);
    }

    private List<ReviewIssueVO> runDeterministicChecks(
            ChapterContextAggregate context,
            String draft,
            ChapterGraphState state,
            Map<ReviewEvidencePackType, MemoryContextPack> packs
    ) {
        List<ReviewIssueVO> issues = new ArrayList<>();
        MemoryMode memoryMode = state.memoryMode();
        List<MemoryContextItem> historicalItems = evidenceItems(packs);
        checkSourceAndVersion(context, draft, state, issues);
        checkTimeRegression(context, draft, issues, memoryMode, historicalItems);
        checkExclusiveLocation(context, draft, issues, memoryMode, historicalItems);
        checkItemPossession(context, draft, issues, memoryMode, historicalItems);
        checkInjuryPhysicalState(context, draft, issues, memoryMode, historicalItems);
        checkDeathToLife(context, draft, issues, memoryMode, historicalItems);
        checkWorldRuleViolation(context, draft, issues, historicalItems);
        checkImplicitAbilityRule(context, draft, issues, historicalItems);
        checkKnowledgeProvenance(context, draft, issues, memoryMode, historicalItems);
        return List.copyOf(issues);
    }

    private List<MemoryContextItem> evidenceItems(
            Map<ReviewEvidencePackType, MemoryContextPack> packs
    ) {
        if (packs == null || packs.isEmpty()) {
            return List.of();
        }
        return packs.values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(pack -> pack.items().stream())
                .distinct()
                .sorted(java.util.Comparator
                        .comparingInt(MemoryContextItem::sourceChapter)
                        .thenComparing(item -> item.order() == null
                                ? Integer.MAX_VALUE : item.order())
                        .thenComparing(MemoryContextItem::itemId))
                .toList();
    }

    private void checkSourceAndVersion(
            ChapterContextAggregate context,
            String draft,
            ChapterGraphState state,
            List<ReviewIssueVO> issues
    ) {
        List<MemoryCandidate> candidates = state.memoryCandidates();
        var sourceVersion = state.sourceVersion().orElse(null);
        if (sourceVersion == null && !candidates.isEmpty()) {
            addIssue(issues, blocker(
                    ISSUE_SOURCE_VERSION,
                    "来源/版本",
                    "存在未绑定当前正文版本的临时候选，不能作为本章审核依据。",
                    draft,
                    currentChapterNumber(context),
                    null,
                    null
            ));
            return;
        }
        if (sourceVersion != null && !sourceVersion.matchesContent(draft)) {
            addIssue(issues, blocker(
                    ISSUE_SOURCE_VERSION,
                    "来源/版本",
                    "当前正文与审核所绑定的正文版本指纹不一致。",
                    draft,
                    currentChapterNumber(context),
                    sourceVersion.chapterVersion(),
                    null
            ));
        }
        if (sourceVersion == null) {
            return;
        }
        for (MemoryCandidate candidate : candidates) {
            try {
                MemoryCandidate refreshed = candidate.revalidate(sourceVersion, draft);
                if (refreshed.isStaleFor(sourceVersion, draft)
                        || refreshed.candidateStatus()
                        == cn.ninth.novel.domain.memory.model.MemoryCandidateStatus.STALE) {
                    addIssue(issues, candidateMismatchIssue(context, draft, candidate,
                            "临时候选已过期，不能用于当前正文审核。"));
                }
            } catch (IllegalArgumentException exception) {
                addIssue(issues, candidateMismatchIssue(context, draft, candidate,
                        "临时候选的版本或 evidence range 无法定位到当前正文。"));
            }
        }
    }

    private ReviewIssueVO candidateMismatchIssue(
            ChapterContextAggregate context,
            String draft,
            MemoryCandidate candidate,
            String description
    ) {
        String sourceEvidence = candidate.evidenceRange().getExcerpt();
        return blocker(
                ISSUE_SOURCE_VERSION,
                "来源/版本",
                description,
                evidenceForDraft(draft, sourceEvidence),
                currentChapterNumber(context),
                candidate.sourceVersion().chapterVersion(),
                sourceEvidence
        );
    }

    private void checkTimeRegression(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            MemoryMode memoryMode,
            List<MemoryContextItem> historicalItems
    ) {
        String priorText = priorText(context, memoryMode);
        TimeMarker previous = extractTime(priorText, false);
        TimeMarker current = extractTime(draft, true);
        if (previous != null && current != null && current.order() < previous.order()) {
            addIssue(issues, blocker(
                    ISSUE_TIMELINE,
                    "时间线",
                    "当前正文的明确时间早于来源章节的明确时间，形成时间倒退。",
                    current.evidence(),
                    historicalChapter(context, historicalItems, previous.evidence(),
                            previousChapterNumber(context)),
                    null,
                    previous.evidence()
            ));
        }
    }

    private void checkExclusiveLocation(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            MemoryMode memoryMode,
            List<MemoryContextItem> historicalItems
    ) {
        String priorText = priorText(context, memoryMode);
        Set<String> entities = knownEntities(context, priorText, draft);
        Map<String, LocationAssertion> previous = locations(priorText, entities);
        Map<String, LocationAssertion> current = locations(draft, entities);
        for (Map.Entry<String, LocationAssertion> entry : current.entrySet()) {
            LocationAssertion oldLocation = previous.get(entry.getKey());
            LocationAssertion newLocation = entry.getValue();
            if (oldLocation == null
                    || sameLocation(oldLocation.location(), newLocation.location())
                    || hasExplicitMove(draft, entry.getKey())) {
                continue;
            }
            boolean character = characterNames(context).contains(entry.getKey());
            String issueType = character ? ISSUE_CHARACTER_PRESENCE : ISSUE_ITEM_LOCATION;
            String historicalEvidence = historicalEvidence(
                    historicalItems, entry.getKey(), oldLocation.location(), oldLocation.evidence());
            addIssue(issues, blocker(
                    issueType,
                    "独占位置",
                    entry.getKey() + " 在来源状态中位于“" + oldLocation.location()
                            + "”，当前正文却明确位于“" + newLocation.location() + "”。",
                    newLocation.evidence(),
                    historicalChapter(context, historicalItems, historicalEvidence,
                            previousChapterNumber(context)),
                    null,
                    historicalEvidence,
                    entry.getKey()
            ));
        }
    }

    private void checkItemPossession(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            MemoryMode memoryMode,
            List<MemoryContextItem> historicalItems
    ) {
        String priorText = priorText(context, memoryMode);
        Set<String> entities = knownEntities(context, priorText, draft);
        Map<String, PossessionAssertion> previous = possessions(priorText, characterNames(context));
        Map<String, PossessionAssertion> current = possessions(draft, characterNames(context));
        for (Map.Entry<String, PossessionAssertion> entry : current.entrySet()) {
            PossessionAssertion old = previous.get(entry.getKey());
            PossessionAssertion now = entry.getValue();
            if (old == null || sameOwner(old.owner(), now.owner())
                    || hasExplicitTransfer(draft, entry.getKey())) {
                continue;
            }
            String historicalEvidence = historicalEvidence(
                    historicalItems, entry.getKey(), old.owner(), old.evidence());
            addIssue(issues, blocker(
                    ISSUE_ITEM_LOCATION,
                    "物品位置/持有",
                    entry.getKey() + " 在来源状态中由“" + old.owner()
                            + "”持有，当前正文却由“" + now.owner() + "”持有，且没有合法转移事件。",
                    now.evidence(),
                    historicalChapter(context, historicalItems, historicalEvidence,
                            previousChapterNumber(context)),
                    null,
                    historicalEvidence,
                    entry.getKey()
            ));
        }
    }

    private void checkInjuryPhysicalState(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            MemoryMode memoryMode,
            List<MemoryContextItem> historicalItems
    ) {
        String priorText = priorText(context, memoryMode);
        Set<String> entities = knownEntities(context, priorText, draft);
        Map<String, InjuryAssertion> previous = injuries(priorText, entities);
        Map<String, InjuryAssertion> current = injuries(draft, entities);
        for (Map.Entry<String, InjuryAssertion> entry : previous.entrySet()) {
            InjuryAssertion old = entry.getValue();
            String currentText = around(draft, entry.getKey());
            if (currentText.isBlank()) {
                continue;
            }
            boolean explicitlyDisappeared = containsAny(currentText,
                    List.of("伤势消失", "伤口消失", "伤势不见", "伤口不见", "已经无伤", "完好无损"));
            if (!explicitlyDisappeared || hasRecoveryEvent(currentText)) {
                continue;
            }
            InjuryAssertion now = current.get(entry.getKey());
            String currentEvidence = now == null
                    ? evidenceContaining(draft, entry.getKey(), "伤") : now.evidence();
            String historicalEvidence = historicalEvidence(
                    historicalItems, entry.getKey(), old.evidence(), old.evidence());
            addIssue(issues, blocker(
                    ISSUE_INJURY_STATE,
                    "伤势/身体状态",
                    entry.getKey() + " 的既有伤势在当前正文中无恢复事件却明确消失。",
                    currentEvidence,
                    historicalChapter(context, historicalItems, historicalEvidence,
                            previousChapterNumber(context)),
                    null,
                    historicalEvidence,
                    entry.getKey()
            ));
        }
    }

    private Map<String, PossessionAssertion> possessions(
            String text,
            Set<String> characterNames
    ) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, PossessionAssertion> result = new LinkedHashMap<>();
        Matcher ownerMatcher = POSSESSION_PATTERN.matcher(text);
        while (ownerMatcher.find()) {
            String owner = cleanEntity(ownerMatcher.group(1));
            String item = cleanPossessionItem(ownerMatcher.group(2));
            if (isKnownOwner(owner, characterNames) && !item.isBlank()) {
                result.put(item, new PossessionAssertion(owner, ownerMatcher.group()));
            }
        }
        Matcher reverseMatcher = REVERSE_POSSESSION_PATTERN.matcher(text);
        while (reverseMatcher.find()) {
            String item = cleanPossessionItem(reverseMatcher.group(1));
            String owner = cleanEntity(reverseMatcher.group(2));
            if (isKnownOwner(owner, characterNames) && !item.isBlank()) {
                result.put(item, new PossessionAssertion(owner, reverseMatcher.group()));
            }
        }
        return result;
    }

    private String cleanPossessionItem(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("^(?:的|一枚|一件|那枚|那件)", "")
                .replaceAll("(?:当前|仍然|仍)$", "")
                .trim();
    }

    private boolean isKnownOwner(String owner, Set<String> characterNames) {
        if (owner == null || owner.isBlank()) {
            return false;
        }
        return characterNames.stream().anyMatch(name -> sameOwner(name, owner));
    }

    private boolean sameOwner(String left, String right) {
        return normalizeReviewText(left).equals(normalizeReviewText(right));
    }

    private boolean hasExplicitTransfer(String draft, String item) {
        if (draft == null || item == null || item.isBlank()) {
            return false;
        }
        String lowerDraft = draft.toLowerCase(Locale.ROOT);
        String lowerItem = item.toLowerCase(Locale.ROOT);
        int itemIndex = lowerDraft.indexOf(lowerItem);
        if (itemIndex < 0) {
            return false;
        }
        String nearby = lowerDraft.substring(
                Math.max(0, itemIndex - 24),
                Math.min(lowerDraft.length(), itemIndex + lowerItem.length() + 40));
        return TRANSFER_WORDS.stream().anyMatch(nearby::contains)
                || nearby.contains("从") && nearby.contains("手中");
    }

    private Map<String, InjuryAssertion> injuries(
            String text,
            Set<String> entities
    ) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, InjuryAssertion> result = new LinkedHashMap<>();
        for (String entity : entities) {
            Matcher matcher = Pattern.compile(
                    Pattern.quote(entity) + "[^，。；;\\n]{0,24}(?:伤势|伤口|骨折|重伤|昏迷|失明|断臂|断腿|负伤)"
            ).matcher(text);
            while (matcher.find()) {
                result.put(entity, new InjuryAssertion(matcher.group()));
            }
        }
        Matcher generic = INJURY_PATTERN.matcher(text);
        while (generic.find()) {
            String entity = cleanEntity(generic.group(1));
            if (!entity.isBlank()) {
                result.putIfAbsent(entity, new InjuryAssertion(generic.group()));
            }
        }
        return result;
    }

    private void checkDeathToLife(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            MemoryMode memoryMode,
            List<MemoryContextItem> historicalItems
    ) {
        String originalPreviousText = priorText(context, memoryMode);
        String previousText = originalPreviousText.toLowerCase(Locale.ROOT);
        String currentText = draft.toLowerCase(Locale.ROOT);
        for (String entity : knownEntities(context, originalPreviousText, draft)) {
            String previousEntityText = around(previousText, entity.toLowerCase(Locale.ROOT));
            String currentEntityText = around(currentText, entity.toLowerCase(Locale.ROOT));
            String death = firstContained(previousEntityText, DEATH_WORDS);
            String life = firstContained(currentEntityText, LIFE_WORDS);
            if (death == null || life == null
                    || isNegatedBefore(previousEntityText, death)
                    || isNegatedBefore(currentEntityText, life)
                    || hasRecoveryEvent(currentEntityText)) {
                continue;
            }
            String currentEvidence = evidenceContaining(draft, entity, life);
            String historicalEvidence = historicalEvidence(
                    historicalItems, entity, death,
                    sourceContaining(originalPreviousText, entity, death));
            addIssue(issues, blocker(
                    "LIFE_STATUS",
                    "生存状态",
                    entity + " 的来源状态已明确死亡，当前正文却明确写成存活。",
                    currentEvidence,
                    historicalChapter(context, historicalItems, historicalEvidence,
                            previousChapterNumber(context)),
                    null,
                    historicalEvidence,
                    entity
            ));
        }
    }

    private void checkWorldRuleViolation(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            List<MemoryContextItem> historicalItems
    ) {
        for (RuleEvidence ruleEvidence : ruleEvidence(context, historicalItems)) {
            String rule = ruleEvidence.text();
            Matcher matcher = RULE_FORBIDDEN_PATTERN.matcher(rule);
            while (matcher.find()) {
                String prefix = matcher.group(1).trim();
                String forbidden = matcher.group(2).trim();
                String action = firstMeaningfulFragment(forbidden);
                if (action.isBlank() || !containsNormalized(draft, action)
                        || isNegatedAround(draft, action)
                        || (!containsNormalized(draft, prefix)
                        && !sharesMeaningfulToken(draft, prefix))) {
                    continue;
                }
                addIssue(issues, blocker(
                        ISSUE_WORLD_RULE,
                        "世界规则",
                        "当前正文明确执行了世界规则禁止的行为：“" + action + "”。",
                        evidenceContaining(draft, null, action),
                        ruleEvidence.sourceChapter(),
                        null,
                        rule
                ));
                break;
            }
        }
    }

    private void checkImplicitAbilityRule(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            List<MemoryContextItem> historicalItems
    ) {
        for (RuleEvidence ruleEvidence : ruleEvidence(context, historicalItems)) {
            String rule = ruleEvidence.text();
            String subject = ruleSubject(rule);
            if (subject.isBlank() || !containsNormalized(draft, subject)) {
                continue;
            }
            String usage = sentenceContainingAbilityUse(draft, subject);
            if (usage.isBlank()) {
                continue;
            }
            boolean activationMissing = requiresActivation(rule)
                    && !containsAny(usage, ACTIVATION_WORDS);
            boolean costMissing = requiresCost(rule)
                    && !containsAny(usage, COST_WORDS);
            boolean conditionMissing = requiresCondition(rule)
                    && !hasRuleConditionInSentence(rule, usage);
            if (!activationMissing && !costMissing && !conditionMissing) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            if (activationMissing) {
                missing.add("主动激活");
            }
            if (costMissing) {
                missing.add("消耗/代价");
            }
            if (conditionMissing) {
                missing.add("使用条件");
            }
            String reason = subject + " 的已确认规则要求包含"
                    + String.join("、", missing) + "，当前正文未给出对应证据，需要语义复核。";
            addIssue(issues, semanticIssue(
                    ISSUE_ABILITY_RULE,
                    "能力规则",
                    reason,
                    usage,
                    ruleEvidence.text(),
                    subject,
                    ruleEvidence.sourceChapter()));
        }
    }

    private void checkKnowledgeProvenance(
            ChapterContextAggregate context,
            String draft,
            List<ReviewIssueVO> issues,
            MemoryMode memoryMode,
            List<MemoryContextItem> historicalItems
    ) {
        String priorText = priorText(context, memoryMode);
        for (String entity : knownEntities(context, priorText, draft)) {
            Matcher explicit = Pattern.compile(
                    Pattern.quote(entity) + "[^，。；;\\n]{0,12}(?:从|根据|通过|依据)"
                            + "([^，。；;\\n]{1,20})(?:得知|知道|确认|认出)"
                            + "([^，。；;\\n]{1,24})"
            ).matcher(draft);
            while (explicit.find()) {
                String source = explicit.group(1).trim();
                String claim = firstMeaningfulFragment(explicit.group(2));
                if (!hasCurrentKnowledgeProvenance(explicit.group())
                        && !hasKnowledgeEvidence(priorText, entity, source, claim)
                        || hasHistoricalSourceConflict(priorText, entity, source, claim)) {
                    String historicalEvidence = historicalEvidence(
                            historicalItems, entity, claim,
                            sourceContaining(priorText, entity, claim));
                    addIssue(issues, semanticIssue(
                            ISSUE_KNOWLEDGE,
                            "知识来源",
                            entity + " 在当前正文获得“" + claim + "”，但来源历史没有可定位的知识依据。",
                            explicit.group(),
                            historicalEvidence,
                            entity,
                            historicalChapter(context, historicalItems, historicalEvidence,
                                    previousChapterNumber(context))
                    ));
                }
            }

            Matcher simple = Pattern.compile(
                    Pattern.quote(entity) + "[^，。；;\\n]{0,8}(?:知道|得知|确认|认出|意识到|明白|清楚)"
                            + "([^，。；;\\n]{1,24})"
            ).matcher(draft);
            while (simple.find()) {
                String claim = firstMeaningfulFragment(simple.group(1));
                if (!claim.isBlank() && !containsNormalized(priorText, claim)) {
                    addIssue(issues, semanticIssue(
                            ISSUE_KNOWLEDGE,
                            "知识来源",
                            entity + " 突然确认“" + claim + "”，但来源历史没有可定位的知识依据。",
                            simple.group(),
                            null,
                            entity,
                            previousChapterNumber(context)
                    ));
                }
            }
        }
    }

    private List<RuleEvidence> ruleEvidence(
            ChapterContextAggregate context,
            List<MemoryContextItem> historicalItems
    ) {
        List<RuleEvidence> rules = new ArrayList<>();
        if (context != null && context.getStoryBible() != null) {
            for (String rule : ruleTexts(context.getStoryBible().getHardRulesJson())) {
                rules.add(new RuleEvidence(rule, null));
            }
        }
        if (historicalItems != null) {
            for (MemoryContextItem item : historicalItems) {
                if (item.category() == MemoryContextCategory.RULES) {
                    rules.add(new RuleEvidence(item.content(), item.sourceChapter()));
                }
            }
        }
        return rules.stream().distinct().toList();
    }

    private String ruleSubject(String rule) {
        if (rule == null || rule.isBlank()) {
            return "";
        }
        String subject = rule.trim()
                .replaceFirst("^(?:规则：|规则:)", "")
                .split("(?:不能|不可|禁止|不得|不允许|必须|需要|只能|每次|只有|仅在|在)", 2)[0]
                .trim();
        return subject.replaceAll("[，,。；;：:]$", "").trim();
    }

    private boolean requiresActivation(String rule) {
        return containsAny(rule, List.of("主动激活", "需要激活", "必须激活", "才能触发", "方可触发"));
    }

    private boolean requiresCost(String rule) {
        return containsAny(rule, List.of("消耗", "代价", "灵息", "体力", "寿命", "精力"));
    }

    private boolean requiresCondition(String rule) {
        return containsAny(rule, List.of("只有", "仅在", "必须在", "需要在", "条件", "才能", "方可"));
    }

    private boolean hasRuleConditionInSentence(String rule, String sentence) {
        Set<String> conditionTokens = meaningfulTokens(rule);
        return conditionTokens.stream().anyMatch(token -> token.length() >= 2
                && containsNormalized(sentence, token));
    }

    private String sentenceContainingAbilityUse(String draft, String subject) {
        if (draft == null || subject == null || subject.isBlank()) {
            return "";
        }
        for (String sentence : draft.split("(?<=[。！？!?\\n])")) {
            String candidate = sentence.trim();
            if (containsNormalized(candidate, subject)
                    && ABILITY_USE_PATTERN.matcher(candidate).find()) {
                return candidate;
            }
        }
        return "";
    }

    private boolean containsAny(String text, List<String> values) {
        if (text == null || text.isBlank() || values == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return values.stream().anyMatch(value -> value != null
                && normalized.contains(value.toLowerCase(Locale.ROOT)));
    }

    private boolean hasCurrentKnowledgeProvenance(String text) {
        return text != null && KNOWLEDGE_PROVENANCE_PATTERN.matcher(text).find();
    }

    private boolean hasHistoricalSourceConflict(
            String priorText,
            String entity,
            String source,
            String claim
    ) {
        if (priorText == null || priorText.isBlank() || claim == null || claim.isBlank()) {
            return false;
        }
        String claimEvidence = sourceContaining(priorText, entity, claim);
        if (claimEvidence == null || claimEvidence.isBlank()) {
            return false;
        }
        String sourceToken = firstMeaningfulFragment(source);
        if (sourceToken.isBlank()) {
            return false;
        }
        if (claimEvidence.contains("不是" + sourceToken)
                || claimEvidence.contains("并非" + sourceToken)
                || claimEvidence.contains("不是" + sourceToken.replace("口中", ""))) {
            return true;
        }
        String currentSourceCore = sourceToken.replaceAll("(?:口中|处|那里|之中)$", "");
        if (currentSourceCore.length() < 2) {
            return false;
        }
        boolean hasDifferentSource = claimEvidence.contains("来源是")
                || claimEvidence.contains("依据")
                || claimEvidence.contains("根据");
        if (hasDifferentSource && !claimEvidence.contains(currentSourceCore)) {
            return true;
        }
        return currentSourceCore.contains("沈夜")
                && containsAny(claimEvidence, List.of("古籍", "书中", "卷中"));
    }

    private boolean hasRecoveryEvent(String text) {
        return containsAny(text, RECOVERY_WORDS);
    }

    private String historicalEvidence(
            List<MemoryContextItem> historicalItems,
            String entity,
            String keyword,
            String fallback
    ) {
        if (historicalItems != null) {
            for (MemoryContextItem item : historicalItems) {
                if (containsNormalized(item.content(), entity)
                        && (keyword == null || keyword.isBlank()
                        || containsNormalized(item.content(), keyword))) {
                    return item.content();
                }
            }
        }
        return fallback;
    }

    private Integer historicalChapter(
            ChapterContextAggregate context,
            List<MemoryContextItem> historicalItems,
            String historicalEvidence,
            Integer fallback
    ) {
        if (historicalEvidence != null && historicalItems != null) {
            for (MemoryContextItem item : historicalItems) {
                if (containsNormalized(item.content(), historicalEvidence)
                        || containsNormalized(historicalEvidence, item.content())) {
                    return item.sourceChapter() > 0 ? item.sourceChapter() : fallback;
                }
            }
        }
        return fallback;
    }

    private ReviewIssueVO blocker(
            String issueType,
            String category,
            String description,
            String evidence,
            Integer sourceChapter,
            String sourceVersion,
            String sourceEvidence
    ) {
        return blocker(issueType, category, description, evidence, sourceChapter,
                sourceVersion, sourceEvidence, null);
    }

    private ReviewIssueVO blocker(
            String issueType,
            String category,
            String description,
            String evidence,
            Integer sourceChapter,
            String sourceVersion,
            String sourceEvidence,
            String relatedEntity
    ) {
        return ReviewIssueVO.builder()
                .issueType(issueType)
                .severity(cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum.BLOCKER)
                .category(category)
                .description(description)
                .evidence(evidence == null || evidence.isBlank() ? "当前正文" : evidence)
                .sourceChapter(sourceChapter)
                .sourceVersion(sourceVersion)
                .sourceEvidence(sourceEvidence)
                .currentEvidence(evidence == null || evidence.isBlank() ? "当前正文" : evidence)
                .historicalEvidence(sourceEvidence)
                .relatedEntity(relatedEntity)
                .reason(description)
                .checkerType("DETERMINISTIC")
                .build();
    }

    private ReviewIssueVO semanticIssue(
            String issueType,
            String category,
            String reason,
            String currentEvidence,
            String historicalEvidence,
            String relatedEntity,
            Integer sourceChapter
    ) {
        return ReviewIssueVO.builder()
                .issueType(issueType)
                .severity(cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum.MAJOR)
                .category(category)
                .description(reason)
                .evidence(currentEvidence == null || currentEvidence.isBlank()
                        ? "当前正文" : currentEvidence)
                .sourceChapter(sourceChapter)
                .sourceEvidence(historicalEvidence)
                .currentEvidence(currentEvidence == null || currentEvidence.isBlank()
                        ? "当前正文" : currentEvidence)
                .historicalEvidence(historicalEvidence)
                .relatedEntity(relatedEntity)
                .reason(reason)
                .checkerType("SEMANTIC")
                .build();
    }

    private void addIssue(List<ReviewIssueVO> issues, ReviewIssueVO issue) {
        if (issue == null) {
            return;
        }
        boolean duplicated = issues.stream().anyMatch(existing ->
                existing != null
                        && java.util.Objects.equals(existing.getCategory(), issue.getCategory())
                        && java.util.Objects.equals(existing.getDescription(), issue.getDescription())
        );
        if (!duplicated) {
            issues.add(issue);
        }
    }

    private String evidenceForDraft(String draft, String preferred) {
        if (preferred != null && !preferred.isBlank()) {
            int exactIndex = draft.indexOf(preferred);
            if (exactIndex >= 0) {
                return draft.substring(exactIndex, exactIndex + preferred.length());
            }
            String normalizedPreferred = normalizeReviewText(preferred);
            if (draft.contains(normalizedPreferred)) {
                return normalizedPreferred;
            }
        }
        return firstDraftSentence(draft);
    }

    private String firstDraftSentence(String draft) {
        if (draft == null || draft.isBlank()) {
            return "当前正文";
        }
        String trimmed = draft.trim();
        int end = trimmed.length();
        for (char punctuation : new char[]{'。', '！', '？', '!', '?', '\n'}) {
            int index = trimmed.indexOf(punctuation);
            if (index >= 0) {
                end = Math.min(end, index + 1);
            }
        }
        return trimmed.substring(0, Math.max(1, end));
    }

    private String evidenceContaining(String text, String anchor, String keyword) {
        if (text == null || text.isBlank()) {
            return "当前正文";
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int keywordIndex = lowerKeyword.isBlank() ? -1 : lowerText.indexOf(lowerKeyword);
        int anchorIndex = anchor == null || anchor.isBlank()
                ? keywordIndex
                : lowerText.indexOf(anchor.trim().toLowerCase(Locale.ROOT));
        int start = Math.min(
                keywordIndex < 0 ? text.length() : keywordIndex,
                anchorIndex < 0 ? text.length() : anchorIndex
        );
        if (start >= text.length()) {
            return firstDraftSentence(text);
        }
        int end = Math.min(text.length(), start + 80);
        for (int index = start; index < end; index++) {
            if ("。！？!?\n".indexOf(text.charAt(index)) >= 0) {
                end = index + 1;
                break;
            }
        }
        return text.substring(start, end).trim();
    }

    private String priorText(ChapterContextAggregate context) {
        return priorText(context, MemoryMode.defaultMode());
    }

    private String priorText(
            ChapterContextAggregate context,
            MemoryMode memoryMode
    ) {
        StringBuilder text = new StringBuilder();
        ChapterHistoryVO history = context.getHistory();
        if (history == null) {
            return "";
        }
        if (history.getPreviousChapter() != null) {
            appendText(text, history.getPreviousChapter().getContent());
        }
        if (memoryMode == MemoryMode.V1) {
            for (MemoryContextItem item : canonicalMemoryItems(context)) {
                appendText(text, item.content());
            }
            return text.toString();
        }
        ChapterMemoryVO previousMemory = previousChapterMemory(context);
        if (previousMemory != null) {
            appendText(text, previousMemory.getShortSummary());
            appendTexts(text, previousMemory.getKeyEvents());
            appendTexts(text, previousMemory.getUnresolved());
            appendText(text, previousMemory.getEndingHook());
        }
        StoryStateSnapshot snapshot = history.getStoryStateSnapshot();
        appendTexts(text, snapshot.resources());
        appendTexts(text, snapshot.abilities());
        appendTexts(text, snapshot.knowledge());
        appendTexts(text, snapshot.presence());
        return text.toString();
    }

    private void appendText(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) {
            target.append(value.trim()).append('\n');
        }
    }

    private void appendTexts(StringBuilder target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            appendText(target, value);
        }
    }

    private TimeMarker extractTime(String text, boolean first) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<TimeMarker> markers = new ArrayList<>();
        Matcher dayMatcher = DAY_TIME_PATTERN.matcher(text);
        while (dayMatcher.find()) {
            int day = parseInt(dayMatcher.group(1), 0);
            int hour = parseInt(dayMatcher.group(2), 0);
            int minute = parseInt(dayMatcher.group(3), 0);
            markers.add(new TimeMarker(
                    day * 24 * 60 + hour * 60 + minute,
                    dayMatcher.group()
            ));
        }
        if (!markers.isEmpty()) {
            return first ? markers.get(0) : markers.get(markers.size() - 1);
        }
        Matcher clockMatcher = CLOCK_PATTERN.matcher(text);
        while (clockMatcher.find()) {
            int hour = parseInt(clockMatcher.group(1), 0);
            int minute = parseInt(clockMatcher.group(2), 0);
            markers.add(new TimeMarker(hour * 60 + minute, clockMatcher.group()));
        }
        if (markers.isEmpty()) {
            return null;
        }
        return first ? markers.get(0) : markers.get(markers.size() - 1);
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Map<String, LocationAssertion> locations(String text) {
        return locations(text, Set.of());
    }

    private Map<String, LocationAssertion> locations(
            String text,
            Set<String> knownEntities
    ) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, LocationAssertion> locations = new LinkedHashMap<>();
        Matcher moveMatcher = MOVE_LOCATION_PATTERN.matcher(text);
        while (moveMatcher.find()) {
            String entity = cleanEntity(moveMatcher.group(1));
            if (!entity.isBlank()) {
                locations.put(entity, new LocationAssertion(
                        cleanLocation(moveMatcher.group(3)), moveMatcher.group()));
            }
        }
        Matcher destinationMatcher = DESTINATION_LOCATION_PATTERN.matcher(text);
        while (destinationMatcher.find()) {
            String entity = cleanEntity(destinationMatcher.group(1));
            String location = cleanLocation(destinationMatcher.group(2));
            if (!entity.isBlank() && !location.isBlank()) {
                locations.put(entity, new LocationAssertion(location, destinationMatcher.group()));
            }
        }
        Matcher locationMatcher = LOCATION_PATTERN.matcher(text);
        while (locationMatcher.find()) {
            String entity = cleanEntity(locationMatcher.group(1));
            String location = cleanLocation(locationMatcher.group(2));
            if (!entity.isBlank() && !location.isBlank()) {
                locations.put(entity, new LocationAssertion(location, locationMatcher.group()));
            }
        }
        Matcher fromMatcher = FROM_LOCATION_STATE_PATTERN.matcher(text);
        while (fromMatcher.find()) {
            String entity = cleanEntity(fromMatcher.group(1));
            String location = cleanLocation(fromMatcher.group(2));
            if (!entity.isBlank() && !location.isBlank()) {
                locations.put(entity, new LocationAssertion(location, fromMatcher.group()));
            }
        }
        for (String entity : knownEntities) {
            if (entity == null || entity.isBlank()) {
                continue;
            }
            String quotedEntity = Pattern.quote(entity);
            Matcher knownDestination = Pattern.compile(
                    quotedEntity + "[^，。；;\\n]{0,10}?(?:去了|前往|进入|到达|抵达|回到|赶到|来到|来到了)"
                            + "([^，。；;\\n]{1,30})").matcher(text);
            while (knownDestination.find()) {
                locations.put(entity, new LocationAssertion(
                        cleanLocation(knownDestination.group(1)), knownDestination.group()));
            }
            Matcher knownFrom = Pattern.compile(
                    quotedEntity + "从([^，。；;\\n]{1,20})"
                            + "(?=(?:弹|醒|起|站|坐|躺|睡|蹲|趴|走|冲|跑|下|床))").matcher(text);
            while (knownFrom.find()) {
                locations.put(entity, new LocationAssertion(
                        cleanLocation(knownFrom.group(1)), knownFrom.group()));
            }
        }
        return locations;
    }

    private String cleanEntity(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("^.*(?:写成|改成|确认|说明|声称|指出|表明)", "")
                .replaceAll("^(?:第\\d+章|清晨|午后|傍晚|深夜|夜里|次日|此刻)+", "")
                .replaceAll("(?:当前|现在|仍然|仍|此刻)$", "")
                .replaceAll("^(?:他|她|其|人物)", "")
                .trim();
    }

    private String cleanLocation(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("(?:找到|发现|看见|拿到|追上).*$", "")
                .replaceAll("[。！？!?]$", "")
                .trim();
    }

    private boolean sameLocation(String left, String right) {
        return normalizeReviewText(left)
                .replaceAll("[的中里上内处]$", "")
                .equals(normalizeReviewText(right).replaceAll("[的中里上内处]$", ""));
    }

    private boolean hasExplicitMove(String draft, String entity) {
        Matcher matcher = MOVE_LOCATION_PATTERN.matcher(draft);
        while (matcher.find()) {
            if (entity.equals(cleanEntity(matcher.group(1)))) {
                return true;
            }
        }
        String nearby = around(draft, entity);
        return containsAny(nearby, List.of(
                "去了", "前往", "进入", "到达", "抵达", "回到", "赶到", "来到", "来到了"));
    }

    private Set<String> knownEntities(
            ChapterContextAggregate context,
            String previousText,
            String currentText
    ) {
        Set<String> entities = new LinkedHashSet<>();
        if (context.getCharacters() != null) {
            context.getCharacters().stream()
                    .filter(character -> character != null
                            && character.getName() != null
                            && !character.getName().isBlank())
                    .map(StoryCharacterEntity::getName)
                    .map(String::trim)
                    .forEach(entities::add);
        }
        entities.addAll(locations(previousText).keySet());
        entities.addAll(locations(currentText).keySet());
        return entities;
    }

    private String around(String text, String entity) {
        int index = text.indexOf(entity);
        if (index < 0) {
            return "";
        }
        int start = index;
        while (start > 0 && "，,；;。！？!?\n".indexOf(text.charAt(start - 1)) < 0) {
            start--;
        }
        int end = index + entity.length();
        while (end < text.length() && "，,；;。！？!?\n".indexOf(text.charAt(end)) < 0) {
            end++;
        }
        return text.substring(start, end);
    }

    private String firstContained(String text, List<String> values) {
        for (String value : values) {
            if (text.contains(value.toLowerCase(Locale.ROOT))) {
                return value;
            }
        }
        return null;
    }

    private String sourceContaining(String text, String anchor, String keyword) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String lowerAnchor = anchor == null ? "" : anchor.trim().toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (String sentence : text.split("(?<=[。！？!?\\n])")) {
            String candidate = sentence.trim();
            if (candidate.isBlank()) {
                continue;
            }
            String lowerSentence = candidate.toLowerCase(Locale.ROOT);
            boolean anchorFound = lowerAnchor.isBlank() || lowerSentence.contains(lowerAnchor);
            boolean keywordFound = lowerKeyword.isBlank() || lowerSentence.contains(lowerKeyword);
            if (anchorFound && keywordFound) {
                return candidate;
            }
        }
        if ((!lowerAnchor.isBlank() && lower.contains(lowerAnchor))
                || (!lowerKeyword.isBlank() && lower.contains(lowerKeyword))) {
            return firstDraftSentence(text);
        }
        return firstDraftSentence(text);
    }

    private boolean isNegatedBefore(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(lowerKeyword);
        if (index < 0) {
            return false;
        }
        String prefix = lowerText.substring(Math.max(0, index - 8), index);
        return prefix.matches(".*(?:不|未|没|没有|并非|并未|尚未)\\s*$");
    }

    private boolean isNegatedAround(String text, String action) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerAction = action.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lowerText.length()) {
            int index = lowerText.indexOf(lowerAction, from);
            if (index < 0) {
                return false;
            }
            String prefix = lowerText.substring(Math.max(0, index - 10), index);
            if (!NEGATED_PATTERN.matcher(prefix).find()) {
                return false;
            }
            from = index + lowerAction.length();
        }
        return true;
    }

    private String firstMeaningfulFragment(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replaceFirst("^(?:时|后|才|方|的)", "")
                .replaceAll("(?:时|后|才能|方可).*$", "")
                .trim();
    }

    private boolean sharesMeaningfulToken(String text, String expected) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String token : expected.split("[^\\u4e00-\\u9fffA-Za-z0-9]+")) {
            if (token.length() >= 2 && lowerText.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKnowledgeEvidence(
            String priorText,
            String entity,
            String source,
            String claim
    ) {
        String lower = priorText.toLowerCase(Locale.ROOT);
        String sourceToken = firstMeaningfulFragment(source).toLowerCase(Locale.ROOT);
        String claimToken = firstMeaningfulFragment(claim).toLowerCase(Locale.ROOT);
        if (!claimToken.isBlank() && !lower.contains(claimToken)) {
            return false;
        }
        if (sourceToken.isBlank()) {
            return true;
        }
        if (lower.contains("不是" + sourceToken)
                || lower.contains("并非" + sourceToken)) {
            return false;
        }
        return lower.contains(sourceToken)
                && (entity == null || entity.isBlank()
                || lower.contains(entity.toLowerCase(Locale.ROOT)));
    }

    private int previousChapterNumber(ChapterContextAggregate context) {
        if (context.getHistory() != null && context.getHistory().getPreviousChapter() != null
                && context.getHistory().getPreviousChapter().getChapterNumber() != null) {
            return context.getHistory().getPreviousChapter().getChapterNumber();
        }
        ChapterMemoryVO memory = previousChapterMemory(context);
        if (memory != null && memory.getChapterNumber() != null) {
            return memory.getChapterNumber();
        }
        return Math.max(0, currentChapterNumber(context) - 1);
    }

    private int currentChapterNumber(ChapterContextAggregate context) {
        return context.getChapterPlan() == null || context.getChapterPlan().getChapterNumber() == null
                ? 0 : context.getChapterPlan().getChapterNumber();
    }

    private void appendReviewConstraints(StringBuilder prompt, ChapterContextAggregate context) {
        StoryBibleEntity storyBible = context.getStoryBible();

        prompt.append("## 故事设定与审稿约束\n");
        ChapterPromptFormatter.appendHardRules(prompt, "不可违反的硬规则", storyBible.getHardRulesJson());
        ChapterPromptFormatter.appendPowerSystem(prompt, "力量体系", storyBible.getPowerSystemJson());
        appendLineIfPresent(prompt, "世界背景", storyBible.getWorldBackground());
        appendLineIfPresent(prompt, "文风指南", storyBible.getStyleGuide());
        NovelProjectEntity project = context.getProject();
        if (project != null) {
            appendLineIfPresent(prompt, "目标字数", project.getWordsPerChapter());
        }
        prompt.append('\n');
    }

    private void validateReviewReport(ReviewReportVO review, String draft) {
        StructuredModelOutputValidator.validate(review);
        List<ReviewIssueVO> issues = review.getReviewIssueVOList();
        if (issues == null) {
            throw invalidReview("reviewIssueVOList 必须返回数组");
        }
        String normalizedDraft = normalizeReviewText(draft);
        for (int index = 0; index < issues.size(); index++) {
            ReviewIssueVO issue = issues.get(index);
            String fieldPrefix = "reviewIssueVOList[" + index + "]";
            if (issue == null) {
                throw invalidReview(fieldPrefix + " 不能为 null");
            }
            if (issue.getSeverity() == null) {
                throw invalidReview(fieldPrefix + ".severity 不能为空");
            }
            requireReviewText(issue.getCategory(), fieldPrefix + ".category");
            requireReviewText(issue.getDescription(), fieldPrefix + ".description");
            requireReviewText(issue.getEvidence(), fieldPrefix + ".evidence");
            if (!normalizedDraft.contains(normalizeReviewText(issue.getEvidence()))) {
                throw invalidReview(fieldPrefix + ".evidence 必须是当前正文的连续原文");
            }
        }
    }

    private String normalizeReviewText(String text) {
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void requireReviewText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidReview(fieldName + " 不能为空");
        }
    }

    private AppException invalidReview(String detail) {
        return AppException.internal(
                ResponseCode.E0004.getCode(),
                "SCHEMA_VALIDATION_FAILED: " + ResponseCode.E0004.getMessage() + "：" + detail
        );
    }

    private String readableFailureMessage(AppException exception) {
        return ResponseCode.messageFor(
                exception.getCode(),
                exception.getUserMessage()
        );
    }

    private void appendChapterPlan(
            StringBuilder prompt,
            ChapterContextAggregate context
    ) {
        prompt.append("## 当前章节章纲\n");
        ChapterPlanEntity chapterPlan = context.getChapterPlan();
        if (chapterPlan == null) {
            prompt.append("无\n\n");
            return;
        }
        if (context.getArc() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "REVIEW 上下文缺少当前 ARC"
            );
        }

        appendLineIfPresent(prompt, "章节标题", context.getArc().title());
        appendLineIfPresent(prompt, "章节摘要", chapterPlan.getSummary());
        prompt.append('\n');
    }

    private void requireChapterOutline(ChapterContextAggregate context) {
        if (context.getChapterPlan() == null) {
            throw AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "REVIEW 节点缺少 chapterPlan"
            );
        }
    }

    private void appendCharacters(
            StringBuilder prompt,
            ChapterContextAggregate context,
            String draft,
            ChapterMemoryVO previousMemory
    ) {
        prompt.append("## 相关人物静态设定\n");
        List<StoryCharacterEntity> characters = context.getCharacters();
        if (characters == null || characters.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }

        String relevanceText = reviewRelevanceText(context, draft, previousMemory);
        List<StoryCharacterEntity> selectedCharacters = new ArrayList<>(MAX_REVIEW_CHARACTERS);
        for (StoryCharacterEntity character : characters) {
            if (character == null || !containsCharacterName(relevanceText, character.getName())) {
                continue;
            }
            selectedCharacters.add(character);
            if (selectedCharacters.size() == MAX_REVIEW_CHARACTERS) {
                break;
            }
        }
        if (selectedCharacters.isEmpty()) {
            for (StoryCharacterEntity character : characters) {
                if (character != null && isMainCharacter(character)) {
                    selectedCharacters.add(character);
                    break;
                }
            }
        }
        if (selectedCharacters.isEmpty()) {
            prompt.append("无\n\n");
            return;
        }

        for (StoryCharacterEntity character : selectedCharacters) {
            prompt.append("### ")
                    .append(value(character.getName()))
                    .append('\n');
            ChapterPromptFormatter.appendCharacterStaticSettings(prompt, character);
            prompt.append('\n');
        }
    }

    private void appendHistory(
            StringBuilder prompt,
            ChapterContextAggregate context,
            ChapterMemoryVO previousMemory,
            MemoryMode memoryMode
    ) {
        prompt.append("## 上一章压缩记忆\n");
        if (memoryMode == MemoryMode.V1) {
            prompt.append("无\n\n")
                    .append("## 当前有效状态\n")
                    .append("无\n");
            return;
        }
        if (context.getHistory() == null || previousMemory == null) {
            prompt.append("无，这是第一章。\n\n");
        } else {
            appendLineIfPresent(prompt, "摘要", previousMemory.getShortSummary());
            appendList(prompt, "关键事件", previousMemory.getKeyEvents());
            appendList(prompt, "未解决问题", previousMemory.getUnresolved());
            appendLineIfPresent(prompt, "结尾钩子", previousMemory.getEndingHook());
            prompt.append('\n');
        }
        prompt.append("## 当前有效状态\n");
        ChapterPromptFormatter.appendStoryStateSnapshot(
                prompt,
                context.getHistory() == null
                        ? null : context.getHistory().getStoryStateSnapshot()
        );
    }

    private ChapterMemoryVO previousChapterMemory(ChapterContextAggregate context) {
        ChapterHistoryVO history = context.getHistory();
        if (history == null || history.getRecentMemories() == null) {
            return null;
        }
        Integer previousChapterNumber = history.getPreviousChapter() == null
                ? null
                : history.getPreviousChapter().getChapterNumber();
        if (previousChapterNumber == null && context.getChapterPlan() != null
                && context.getChapterPlan().getChapterNumber() != null) {
            previousChapterNumber = context.getChapterPlan().getChapterNumber() - 1;
        }
        for (int index = history.getRecentMemories().size() - 1; index >= 0; index--) {
            ChapterMemoryVO memory = history.getRecentMemories().get(index);
            if (memory == null || memory.getShortSummary() == null) {
                continue;
            }
            if (previousChapterNumber == null
                    || previousChapterNumber.equals(memory.getChapterNumber())) {
                return memory;
            }
        }
        return null;
    }

    private boolean containsCharacterName(String text, String name) {
        return name != null && !name.isBlank() && containsNormalized(text, name);
    }

    private boolean containsNormalized(String text, String candidate) {
        return candidate != null && !candidate.isBlank()
                && text != null
                && text.toLowerCase(Locale.ROOT)
                .contains(candidate.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isMainCharacter(StoryCharacterEntity character) {
        return character.getRoleType() != null
                && (character.getRoleType().contains("主角")
                || character.getRoleType().equalsIgnoreCase("PROTAGONIST"));
    }

    private String reviewRelevanceText(
            ChapterContextAggregate context,
            String draft,
            ChapterMemoryVO previousMemory
    ) {
        StringBuilder text = new StringBuilder();
        ChapterPlanEntity plan = context.getChapterPlan();
        if (plan != null) {
            appendRelevancePart(
                    text,
                    context.getArc() == null ? null : context.getArc().title()
            );
            appendRelevancePart(text, plan.getSummary());
        }
        appendRelevancePart(text, draft);
        if (previousMemory != null) {
            appendRelevancePart(text, previousMemory.getShortSummary());
            appendRelevanceParts(text, previousMemory.getKeyEvents());
            appendRelevanceParts(text, previousMemory.getUnresolved());
            appendRelevancePart(text, previousMemory.getEndingHook());
        }
        StoryBibleEntity storyBible = context.getStoryBible();
        if (storyBible != null) {
            appendRelevancePart(text, storyBible.getWorldBackground());
            appendRelevancePart(text, storyBible.getHardRulesJson());
            appendRelevancePart(text, storyBible.getPowerSystemJson());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private void appendRelevancePart(StringBuilder text, String part) {
        if (part != null && !part.isBlank()) {
            text.append(part).append('\n');
        }
    }

    private void appendRelevanceParts(StringBuilder text, List<String> parts) {
        if (parts == null) {
            return;
        }
        for (String part : parts) {
            appendRelevancePart(text, part);
        }
    }

    private void appendDraft(StringBuilder prompt, String draft) {
        prompt.append("## 当前待审稿正文\n")
                .append(value(draft))
                .append('\n');
    }

    private enum ReviewTopic {
        TEMPORAL("时间线", "时间", "第", "日", "天", "次日", "昨日", "清晨", "午后", "夜"),
        POSITION("位置连续性", "位于", "在", "位置", "地点", "转移", "带到", "马车", "地下室"),
        LIFE_STATUS("生存状态", "死亡", "死了", "已死", "被杀", "活着", "存活", "复活"),
        WORLD_RULE("世界规则", "不能", "不可", "禁止", "不得", "规则"),
        KNOWLEDGE("知识来源", "知道", "得知", "确认", "依据", "古籍", "来源"),
        CONFIRMATION("确认边界", "确认", "确实", "证明", "认定", "第一次", "再次"),
        CAUSALITY("因果关系", "因为", "因此", "于是", "导致", "所以", "突然"),
        ABILITY("能力语义", "能力", "灵息", "激活", "消耗", "伤势", "恢复");

        private final String label;
        private final List<String> keywords;

        ReviewTopic(String label, String... keywords) {
            this.label = label;
            this.keywords = List.of(keywords);
        }

        private String label() {
            return label;
        }

        private boolean relevant(MemoryContextItem item) {
            if (this == WORLD_RULE && item.category() == MemoryContextCategory.RULES) {
                return true;
            }
            if (this == KNOWLEDGE && item.itemId().contains("knowledge")) {
                return true;
            }
            if (this == ABILITY && item.itemId().contains("ability")) {
                return true;
            }
            String content = item.content().toLowerCase(Locale.ROOT);
            return keywords.stream().anyMatch(content::contains);
        }

        private MemoryBudgetSpec budget() {
            return new MemoryBudgetSpec(
                    768,
                    4,
                    4,
                    6,
                    2,
                    MAX_EVIDENCE_ITEMS_PER_TOPIC
            );
        }
    }

    /** Review 专用的五类 evidence pack；不改变 Memory V1 的五类 Context category。 */
    private enum ReviewEvidencePackType {
        CHARACTER_PRESENCE("Character Presence Pack", List.of(ReviewTopic.POSITION)),
        ITEM_STATE("Item State Pack", List.of(ReviewTopic.POSITION, ReviewTopic.LIFE_STATUS)),
        ABILITY_RULE("Ability Rule Pack", List.of(ReviewTopic.ABILITY, ReviewTopic.WORLD_RULE)),
        KNOWLEDGE("Knowledge Pack", List.of(ReviewTopic.KNOWLEDGE)),
        TIMELINE("Timeline Pack", List.of(ReviewTopic.TEMPORAL, ReviewTopic.CAUSALITY,
                ReviewTopic.CONFIRMATION));

        private final String label;
        private final List<ReviewTopic> topics;

        ReviewEvidencePackType(String label, List<ReviewTopic> topics) {
            this.label = label;
            this.topics = topics;
        }

        private String label() {
            return label;
        }

        private List<ReviewTopic> topics() {
            return topics;
        }

        private boolean relevant(MemoryContextItem item, String draft) {
            String content = item.content().toLowerCase(Locale.ROOT);
            return switch (this) {
                case CHARACTER_PRESENCE -> containsAnyText(content,
                        "位于", "位置", "地点", "去了", "前往", "进入", "离场", "出场", "行政楼", "宿舍");
                case ITEM_STATE -> containsAnyText(content,
                        "位于", "位置", "持有", "携带", "保管", "转移", "伤势", "伤口", "资源");
                case ABILITY_RULE -> item.category() == MemoryContextCategory.RULES
                        || containsAnyText(content, "能力", "激活", "消耗", "代价", "条件", "触发", "灵息");
                case KNOWLEDGE -> item.itemId().toLowerCase(Locale.ROOT).contains("knowledge")
                        || containsAnyText(content, "知道", "得知", "确认", "依据", "古籍", "来源", "观察");
                case TIMELINE -> item.storyTime() != null
                        || containsAnyText(content, "第", "日", "天", "次日", "清晨", "午后", "夜", "之后");
            };
        }

        private static boolean containsAnyText(String text, String... values) {
            for (String value : values) {
                if (text.contains(value.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        private MemoryBudgetSpec budget() {
            return new MemoryBudgetSpec(
                    768,
                    4,
                    4,
                    6,
                    2,
                    MAX_EVIDENCE_ITEMS_PER_TOPIC
            );
        }
    }

    private record ReviewEvidenceBundle(
            List<ReviewIssueVO> deterministicIssues,
            Map<ReviewEvidencePackType, MemoryContextPack> packs
    ) {
        private ReviewEvidenceBundle {
            deterministicIssues = deterministicIssues == null
                    ? List.of() : List.copyOf(deterministicIssues);
            packs = packs == null ? Map.of() : Map.copyOf(packs);
        }
    }

    private record TimeMarker(int order, String evidence) {
    }

    private record LocationAssertion(String location, String evidence) {
    }

    private record PossessionAssertion(String owner, String evidence) {
    }

    private record InjuryAssertion(String evidence) {
    }

    private record RuleEvidence(String text, Integer sourceChapter) {
    }

}
