package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/** P0 上下文预算：总 token 上限和五类 item 上限。 */
public record MemoryBudgetSpec(
        int totalTokenBudget,
        int rulesMaxItems,
        int openLoopsMaxItems,
        int currentStatesMaxItems,
        int consolidatedMaxItems,
        int episodesMaxItems
) {

    public MemoryBudgetSpec {
        if (totalTokenBudget <= 0) {
            throw new IllegalArgumentException("totalTokenBudget 必须大于 0");
        }
        requireNonNegative(rulesMaxItems, "rulesMaxItems");
        requireNonNegative(openLoopsMaxItems, "openLoopsMaxItems");
        requireNonNegative(currentStatesMaxItems, "currentStatesMaxItems");
        requireNonNegative(consolidatedMaxItems, "consolidatedMaxItems");
        requireNonNegative(episodesMaxItems, "episodesMaxItems");
    }

    public static MemoryBudgetSpec defaults() {
        return new MemoryBudgetSpec(2048, 5, 5, 10, 6, 3);
    }

    public static MemoryBudgetSpec defaultP0() {
        return defaults();
    }

    public int maxItems(MemoryContextCategory category) {
        Objects.requireNonNull(category, "category 不能为空");
        return switch (category) {
            case RULES -> rulesMaxItems;
            case OPEN_LOOPS -> openLoopsMaxItems;
            case CURRENT_STATES -> currentStatesMaxItems;
            case CONSOLIDATED -> consolidatedMaxItems;
            case EPISODES -> episodesMaxItems;
        };
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能小于 0");
        }
    }
}
