package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** 一次确定性预算裁剪后的五类 ContextPack。 */
public record MemoryContextPack(
        MemoryProfile profile,
        List<MemoryContextItem> rules,
        List<MemoryContextItem> openLoops,
        List<MemoryContextItem> currentStates,
        List<MemoryContextItem> consolidated,
        List<MemoryContextItem> episodes,
        int estimatedTokenCount
) {

    public MemoryContextPack {
        profile = Objects.requireNonNull(profile, "profile 不能为空");
        rules = immutable(rules, MemoryContextCategory.RULES);
        openLoops = immutable(openLoops, MemoryContextCategory.OPEN_LOOPS);
        currentStates = immutable(currentStates, MemoryContextCategory.CURRENT_STATES);
        consolidated = immutable(consolidated, MemoryContextCategory.CONSOLIDATED);
        episodes = immutable(episodes, MemoryContextCategory.EPISODES);
        if (estimatedTokenCount < 0) {
            throw new IllegalArgumentException("estimatedTokenCount 不能小于 0");
        }
    }

    public int totalItemCount() {
        return rules.size()
                + openLoops.size()
                + currentStates.size()
                + consolidated.size()
                + episodes.size();
    }

    public int tokenCount() {
        return estimatedTokenCount;
    }

    public List<MemoryContextItem> items() {
        return Stream.of(rules, openLoops, currentStates, consolidated, episodes)
                .flatMap(List::stream)
                .toList();
    }

    private static List<MemoryContextItem> immutable(
            List<MemoryContextItem> values,
            MemoryContextCategory expectedCategory
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        for (MemoryContextItem item : values) {
            Objects.requireNonNull(item, "ContextPack 不能包含 null");
            if (item.category() != expectedCategory) {
                throw new IllegalArgumentException(
                        "ContextPack 类别不匹配: expected=" + expectedCategory);
            }
        }
        return List.copyOf(values);
    }
}
