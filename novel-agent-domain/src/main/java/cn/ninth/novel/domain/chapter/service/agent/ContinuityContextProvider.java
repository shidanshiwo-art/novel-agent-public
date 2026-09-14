package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Review 前已经准备好的 Memory V1 Pack 中构造连续性上下文。
 *
 * <p>Provider 是纯内存、确定性的投影器：它不依赖 Repository，不执行 Recall，
 * 只保留当前正文中出现的实体对应的记忆条目。Memory V1 的类别映射为：
 * CURRENT_STATES（当前事实/角色与物品状态）、CONSOLIDATED（历史事实）、
 * EPISODES（近期事件）、RULES（能力与世界规则）、OPEN_LOOPS（开放回路）。
 * 每条 MemoryContextItem 自带的 source 字段继续承担知识来源标识。</p>
 */
@Component
public class ContinuityContextProvider {

    private static final Pattern HAN_RUN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_-]{2,}");
    private static final Set<String> COMMON_TERMS = Set.of(
            "然后", "于是", "只是", "已经", "正在", "开始", "发现", "看见", "知道", "说道",
            "问道", "回答", "走到", "来到", "那里", "这里", "什么", "一个", "一种",
            "这个", "那个", "他们", "她们", "自己", "可以", "能够", "因为", "所以", "如果",
            "但是", "不过", "没有", "不是", "仍然", "似乎", "东西", "事情", "时候", "之后",
            "之前", "身上", "手中", "面前", "旁边", "里面", "外面", "声音", "目光", "身体",
            "章节", "正文", "当前", "历史", "记忆", "事件", "状态", "规则");

    /**
     * 构造连续性审核所需的实体相关 Memory Pack。
     *
     * @param currentDraft 当前正文
     * @param memoryV1 ReviewPreparation 阶段已经加载的 Memory V1 Pack
     * @return 只包含正文实体相关条目的 Pack；没有输入记忆时返回空 REVIEW Pack
     */
    public MemoryContextPack provide(String currentDraft, MemoryContextPack memoryV1) {
        return provide(null, currentDraft, memoryV1);
    }

    /**
     * 带章纲的重载用于固定 ReviewPreparation 的输入边界；章纲不参与 Memory 检索。
     */
    public MemoryContextPack provide(
            ChapterPlanEntity chapterPlan,
            String currentDraft,
            MemoryContextPack memoryV1
    ) {
        String draft = currentDraft == null ? "" : currentDraft;
        if (memoryV1 == null) {
            return emptyPack();
        }

        Set<String> draftTerms = extractTerms(draft);
        Predicate<MemoryContextItem> related = item -> isRelated(item, draft, draftTerms);
        List<MemoryContextItem> rules = select(memoryV1.rules(), related, draft, draftTerms);
        List<MemoryContextItem> openLoops = select(memoryV1.openLoops(), related, draft, draftTerms);
        List<MemoryContextItem> currentStates = select(
                memoryV1.currentStates(), related, draft, draftTerms);
        List<MemoryContextItem> consolidated = select(
                memoryV1.consolidated(), related, draft, draftTerms);
        List<MemoryContextItem> episodes = select(memoryV1.episodes(), related, draft, draftTerms);
        int estimatedTokens = totalEstimatedTokens(
                List.of(rules, openLoops, currentStates, consolidated, episodes));
        return new MemoryContextPack(
                memoryV1.profile(), rules, openLoops, currentStates, consolidated, episodes,
                estimatedTokens);
    }

    /** 语义别名，便于调用方表达“构造一次固定投影”。 */
    public MemoryContextPack build(String currentDraft, MemoryContextPack memoryV1) {
        return provide(currentDraft, memoryV1);
    }

    private List<MemoryContextItem> select(
            List<MemoryContextItem> items,
            Predicate<MemoryContextItem> related,
            String draft,
            Set<String> draftTerms
    ) {
        return items.stream()
                .filter(Objects::nonNull)
                .filter(related)
                .sorted(Comparator
                        .comparingInt((MemoryContextItem item) -> relevanceScore(item, draft, draftTerms))
                        .reversed()
                        .thenComparing(Comparator.comparingInt(MemoryContextItem::priority).reversed())
                        .thenComparing(Comparator.comparingInt(MemoryContextItem::sourceChapter).reversed())
                        .thenComparing(MemoryContextItem::itemId))
                .toList();
    }

    private boolean isRelated(
            MemoryContextItem item,
            String draft,
            Set<String> draftTerms
    ) {
        String content = normalize(item.content());
        if (content.isEmpty() || draft.isEmpty()) {
            return false;
        }
        if (containsMeaningfulPhrase(content, draft)) {
            return true;
        }
        return draftTerms.stream().anyMatch(content::contains);
    }

    private int relevanceScore(
            MemoryContextItem item,
            String draft,
            Set<String> draftTerms
    ) {
        int score = draftTerms.stream()
                .filter(item.content()::contains)
                .mapToInt(String::length)
                .max()
                .orElse(0);
        if (containsMeaningfulPhrase(normalize(item.content()), draft)) {
            score += 100;
        }
        return score;
    }

    private boolean containsMeaningfulPhrase(String content, String draft) {
        Set<String> itemTerms = extractTerms(content);
        return itemTerms.stream().anyMatch(draft::contains);
    }

    private Set<String> extractTerms(String text) {
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        String normalized = normalize(text);
        Matcher hanMatcher = HAN_RUN.matcher(normalized);
        while (hanMatcher.find()) {
            addHanTerms(terms, hanMatcher.group());
        }
        Matcher wordMatcher = WORD.matcher(normalized.toLowerCase(Locale.ROOT));
        while (wordMatcher.find()) {
            String term = wordMatcher.group();
            if (!COMMON_TERMS.contains(term)) {
                terms.add(term);
            }
        }
        return Set.copyOf(terms);
    }

    private void addHanTerms(Set<String> terms, String run) {
        int maxLength = Math.min(8, run.length());
        for (int length = maxLength; length >= 2; length--) {
            for (int start = 0; start + length <= run.length(); start++) {
                String term = run.substring(start, start + length);
                if (!COMMON_TERMS.contains(term)) {
                    terms.add(term);
                }
            }
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    private MemoryContextPack emptyPack() {
        return new MemoryContextPack(
                MemoryProfile.REVIEW, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    /** 避免把 token 汇总逻辑散落到各类别选择代码中。 */
    private int totalEstimatedTokens(List<List<MemoryContextItem>> groups) {
        int total = 0;
        for (List<MemoryContextItem> group : groups) {
            total += group.stream().mapToInt(MemoryContextItem::estimatedTokenCount).sum();
        }
        return total;
    }
}
