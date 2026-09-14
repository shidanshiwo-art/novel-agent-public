package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryContextSelection;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * P0.4 统一记忆上下文提供器。
 *
 * <p>Provider 只做确定性的 profile 优先级、类别上限和总 token 裁剪。
 * 它不写入生命周期、不扩大 recent window、不访问图数据库，也不要求 PGVector。</p>
 */
public final class MemoryContextProvider {

    private static final List<MemoryContextCategory> PLAN_ORDER = List.of(
            MemoryContextCategory.OPEN_LOOPS,
            MemoryContextCategory.CONSOLIDATED,
            MemoryContextCategory.RULES,
            MemoryContextCategory.CURRENT_STATES,
            MemoryContextCategory.EPISODES);
    private static final List<MemoryContextCategory> DRAFT_ORDER = List.of(
            MemoryContextCategory.CURRENT_STATES,
            MemoryContextCategory.RULES,
            MemoryContextCategory.OPEN_LOOPS,
            MemoryContextCategory.CONSOLIDATED,
            MemoryContextCategory.EPISODES);
    private static final List<MemoryContextCategory> REVIEW_ORDER = List.of(
            MemoryContextCategory.EPISODES,
            MemoryContextCategory.CURRENT_STATES,
            MemoryContextCategory.RULES,
            MemoryContextCategory.OPEN_LOOPS,
            MemoryContextCategory.CONSOLIDATED);

    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates
    ) {
        return select(querySpec, budget, candidates).pack();
    }

    /** 在保留原选择行为的同时返回筛选与预算裁剪诊断。 */
    public MemoryContextSelection select(
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget,
            Collection<MemoryContextItem> candidates
    ) {
        Objects.requireNonNull(querySpec, "querySpec 不能为空");
        Objects.requireNonNull(budget, "budget 不能为空");
        List<MemoryContextItem> items = immutableCandidates(candidates);
        MemoryProfile profile = querySpec.memoryProfile();

        Map<MemoryContextCategory, List<MemoryContextItem>> eligible = new EnumMap<>(
                MemoryContextCategory.class);
        for (MemoryContextCategory category : MemoryContextCategory.values()) {
            Predicate<MemoryContextItem> filter = item ->
                    item.category() == category && isEligible(item, querySpec);
            eligible.put(category, items.stream().filter(filter).toList());
        }

        List<MemoryContextCategory> order = orderFor(profile);
        EnumMap<MemoryContextCategory, List<MemoryContextItem>> selected = new EnumMap<>(
                MemoryContextCategory.class);
        int usedTokens = 0;
        for (MemoryContextCategory category : order) {
            List<MemoryContextItem> categoryItems = eligible.get(category).stream()
                    .sorted(comparator(querySpec, profile))
                    .limit(budget.maxItems(category))
                    .toList();
            List<MemoryContextItem> accepted = List.of();
            for (MemoryContextItem item : categoryItems) {
                int itemTokens = item.estimatedTokenCount();
                if (usedTokens + itemTokens > budget.totalTokenBudget()) {
                    continue;
                }
                accepted = append(accepted, item);
                usedTokens += itemTokens;
            }
            selected.put(category, accepted);
        }

        MemoryContextPack pack = new MemoryContextPack(
                profile,
                selected.getOrDefault(MemoryContextCategory.RULES, List.of()),
                selected.getOrDefault(MemoryContextCategory.OPEN_LOOPS, List.of()),
                selected.getOrDefault(MemoryContextCategory.CURRENT_STATES, List.of()),
                selected.getOrDefault(MemoryContextCategory.CONSOLIDATED, List.of()),
                selected.getOrDefault(MemoryContextCategory.EPISODES, List.of()),
                usedTokens);
        // filteredItems 表示通过规则/信号过滤、继续参与预算选择的候选。
        List<MemoryContextItem> filtered = items.stream()
                .filter(item -> eligible.values().stream().anyMatch(values -> values.contains(item)))
                .toList();
        List<MemoryContextItem> selectedItems = pack.items();
        List<MemoryContextItem> budgetTrimmed = filtered.stream()
                .filter(item -> !selectedItems.contains(item))
                .toList();
        return new MemoryContextSelection(pack, filtered, budgetTrimmed);
    }

    public MemoryContextPack provide(
            Collection<MemoryContextItem> candidates,
            MemoryQuerySpec querySpec,
            MemoryBudgetSpec budget
    ) {
        return provide(querySpec, budget, candidates);
    }

    public MemoryContextPack provide(
            MemoryQuerySpec querySpec,
            Collection<MemoryContextItem> candidates,
            MemoryBudgetSpec budget
    ) {
        return provide(querySpec, budget, candidates);
    }

    private static boolean isEligible(
            MemoryContextItem item,
            MemoryQuerySpec querySpec
    ) {
        MemoryProfile profile = querySpec.memoryProfile();
        boolean explicit = querySpec.explicitlyRequests(item.itemId());

        // Staging 只能由 REVIEW 的明确检查临时读取，绝不进入普通 PLAN/DRAFT Recall。
        if (item.staging() && !(profile == MemoryProfile.REVIEW && explicit)) {
            return false;
        }
        // Archived evidence 只开放给 REVIEW；这不会改变底层生命周期。
        if (item.archived() && profile != MemoryProfile.REVIEW) {
            return false;
        }
        // Episode 只有作为 bridge 才能进入普通 DRAFT，避免 recent window 变成长期记忆。
        if (item.category() == MemoryContextCategory.EPISODES
                && (profile == MemoryProfile.DRAFT || profile == MemoryProfile.PLAN)
                && !item.bridgeOnly()) {
            return false;
        }

        return switch (profile) {
            case PLAN -> planSignal(item, querySpec, explicit);
            case DRAFT -> draftSignal(item, querySpec, explicit);
            case REVIEW -> true;
        };
    }

    private static boolean planSignal(
            MemoryContextItem item,
            MemoryQuerySpec querySpec,
            boolean explicit
    ) {
        if (explicit) {
            return true;
        }
        return switch (item.category()) {
            case OPEN_LOOPS -> querySpec.openOrProgressedOpenLoopIds().contains(item.itemId());
            case RULES -> querySpec.hitWorldRuleIds().contains(item.itemId());
            case CONSOLIDATED -> true;
            case CURRENT_STATES -> querySpec.currentSceneItemIds().contains(item.itemId())
                    || querySpec.currentEffectiveStateItemIds().contains(item.itemId())
                    || item.bridgeOnly();
            case EPISODES -> item.bridgeOnly();
        };
    }

    private static boolean draftSignal(
            MemoryContextItem item,
            MemoryQuerySpec querySpec,
            boolean explicit
    ) {
        if (explicit) {
            return true;
        }
        return switch (item.category()) {
            case CURRENT_STATES -> querySpec.currentSceneItemIds().contains(item.itemId())
                    || querySpec.currentEffectiveStateItemIds().contains(item.itemId());
            case RULES -> querySpec.hitWorldRuleIds().contains(item.itemId());
            case OPEN_LOOPS -> querySpec.openOrProgressedOpenLoopIds().contains(item.itemId());
            case CONSOLIDATED -> false;
            case EPISODES -> item.bridgeOnly();
        };
    }

    private static Comparator<MemoryContextItem> comparator(
            MemoryQuerySpec querySpec,
            MemoryProfile profile
    ) {
        return Comparator
                .comparingInt((MemoryContextItem item) -> signalRank(item, querySpec, profile))
                .thenComparingInt(MemoryContextItem::priority)
                .thenComparing(Comparator.comparingInt(MemoryContextItem::sourceChapter).reversed())
                .thenComparing(MemoryContextItem::itemId);
    }

    private static int signalRank(
            MemoryContextItem item,
            MemoryQuerySpec querySpec,
            MemoryProfile profile
    ) {
        if (querySpec.explicitlyRequests(item.itemId())) {
            return 0;
        }
        if (profile == MemoryProfile.DRAFT
                && (querySpec.currentSceneItemIds().contains(item.itemId())
                || querySpec.currentEffectiveStateItemIds().contains(item.itemId()))) {
            return 1;
        }
        if (profile == MemoryProfile.PLAN
                && querySpec.openOrProgressedOpenLoopIds().contains(item.itemId())) {
            return 1;
        }
        if (profile == MemoryProfile.REVIEW
                && item.archived()) {
            return 1;
        }
        return 2;
    }

    private static List<MemoryContextCategory> orderFor(MemoryProfile profile) {
        return switch (profile) {
            case PLAN -> PLAN_ORDER;
            case DRAFT -> DRAFT_ORDER;
            case REVIEW -> REVIEW_ORDER;
        };
    }

    private static List<MemoryContextItem> append(
            List<MemoryContextItem> items,
            MemoryContextItem item
    ) {
        java.util.ArrayList<MemoryContextItem> copy = new java.util.ArrayList<>(items);
        copy.add(item);
        return List.copyOf(copy);
    }

    private static List<MemoryContextItem> immutableCandidates(
            Collection<MemoryContextItem> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates 不能包含 null");
        }
        return List.copyOf(candidates);
    }
}
