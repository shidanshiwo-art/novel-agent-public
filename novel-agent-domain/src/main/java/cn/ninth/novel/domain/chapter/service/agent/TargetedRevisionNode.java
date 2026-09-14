package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlanItem;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 只针对 REQUIRED RepairPlanItem 做最小范围返修的节点。
 *
 * <p>Revision #1 使用原始计划中的 REQUIRED 项；Revision #2 只使用
 * Regression 产生的 remainingRepairPlan，不读取 ReviewContext、章纲或 Memory。</p>
 */
public class TargetedRevisionNode implements NodeAction<ChapterGraphState> {

    static final String SYSTEM_PROMPT = """
            你是章节局部返修器。
            只修改返修计划明确指出的最小正文范围，保持其他正常段落、剧情设计和叙事结构不变。
            禁止重写整章，禁止无理由修改正常段落，禁止主动寻找新的优化点，禁止重新设计剧情。
            输出完整修改后的正文，不附加解释。
            """;

    private final IChapterModelPort chapterModelPort;

    /** 离线 deterministic 入口：不调用模型，保留当前正文并建立回归前片段快照。 */
    public TargetedRevisionNode() {
        this.chapterModelPort = null;
    }

    public TargetedRevisionNode(IChapterModelPort chapterModelPort) {
        this.chapterModelPort = Objects.requireNonNull(chapterModelPort,
                "chapterModelPort 不能为空");
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        String currentDraft = state.draft().orElse("");
        RepairPlan plan = revisionPlan(state);
        List<RepairPlanItem> requiredItems = plan.requiredItems();
        Map<String, String> beforeAffectedText = affectedText(currentDraft, requiredItems);
        String revisedDraft = chapterModelPort == null
                ? currentDraft
                : revise(currentDraft, requiredItems);

        String node = state.revisionRound() <= 1
                ? "REVISION_1" : "REVISION_2";
        Map<String, Object> update = new HashMap<>();
        update.put(ChapterGraphKeys.DRAFT, revisedDraft);
        update.put(ChapterGraphKeys.REVISION_BEFORE_AFFECTED_TEXT, beforeAffectedText);
        update.put(ChapterGraphKeys.CURRENT_NODE, node);
        update.put(ChapterGraphKeys.COMPLETED_STAGES, List.of(node));
        if (!revisedDraft.equals(currentDraft)) {
            MemorySourceVersion sourceVersion = MemorySourceVersion.create(revisedDraft);
            update.put(ChapterGraphKeys.SOURCE_VERSION, sourceVersion);
            update.put(ChapterGraphKeys.MEMORY_CANDIDATES,
                    MemoryCandidate.revalidateAll(
                            state.memoryCandidates(), sourceVersion, revisedDraft));
        }
        return Map.copyOf(update);
    }

    /** Revision #2 没有 remainingRepairPlan 时也不得回退读取原始全量计划。 */
    RepairPlan revisionPlan(ChapterGraphState state) {
        if (state.revisionRound() >= 2) {
            return state.remainingRepairPlan().orElse(RepairPlan.builder().build());
        }
        return state.repairPlan().orElse(RepairPlan.builder().build());
    }

    private String revise(String currentDraft, List<RepairPlanItem> requiredItems) {
        ChapterModelResponse<String> response = chapterModelPort.callWithUsage(
                SYSTEM_PROMPT,
                buildUserPrompt(currentDraft, requiredItems),
                "REVISION",
                1);
        String revisedDraft = response == null ? null : response.value();
        if (revisedDraft == null || revisedDraft.isBlank()) {
            throw new IllegalStateException("Targeted Revision 返回正文为空");
        }
        return revisedDraft.trim();
    }

    String buildUserPrompt(String currentDraft, List<RepairPlanItem> requiredItems) {
        StringBuilder prompt = new StringBuilder(8192);
        prompt.append("## 当前正文\n").append(value(currentDraft)).append("\n\n")
                .append("## 仅允许处理的 REQUIRED 返修项\n");
        if (requiredItems == null || requiredItems.isEmpty()) {
            prompt.append("无\n");
            return prompt.toString();
        }
        for (int i = 0; i < requiredItems.size(); i++) {
            RepairPlanItem item = requiredItems.get(i);
            prompt.append("### 返修项 ").append(i + 1).append('\n')
                    .append("问题：").append(value(item.getProblem())).append('\n')
                    .append("证据：").append(value(item.getEvidence())).append('\n')
                    .append("受影响范围：").append(value(item.getAffectedRange())).append('\n')
                    .append("返修意图：").append(value(item.getRepairIntent())).append("\n\n");
        }
        prompt.append("只修改上述返修项所需的最小范围，保持其余正文原样。\n")
                .append("不要重写整章，不要寻找新问题，不要重新设计剧情。");
        return prompt.toString();
    }

    static Map<String, String> affectedText(
            String draft,
            List<RepairPlanItem> items
    ) {
        Map<String, String> result = new HashMap<>();
        if (items == null) {
            return Map.of();
        }
        for (RepairPlanItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            result.put(item.getId(), extractAffectedText(draft, item));
        }
        return Map.copyOf(result);
    }

    static String extractAffectedText(String draft, RepairPlanItem item) {
        String normalizedDraft = draft == null ? "" : draft;
        String range = value(item == null ? null : item.getAffectedRange());
        if (!range.isBlank() && normalizedDraft.contains(range)) {
            return range;
        }
        String evidence = value(item == null ? null : item.getEvidence());
        if (!evidence.isBlank() && normalizedDraft.contains(evidence)) {
            return evidence;
        }
        return "";
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }
}
