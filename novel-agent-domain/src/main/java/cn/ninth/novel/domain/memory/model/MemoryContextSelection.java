package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/**
 * ContextProvider 一次选择的诊断结果。
 *
 * <p>{@code filteredItems} 是通过规则/信号过滤后仍可参与预算选择的候选；
 * {@code budgetTrimmedItems} 是其中因类别上限或总预算未被选中的候选。
 * 列表仍保留领域对象供旁路记录器提取元数据；持久化层不会序列化其中的正文。</p>
 */
public record MemoryContextSelection(
        MemoryContextPack pack,
        List<MemoryContextItem> filteredItems,
        List<MemoryContextItem> budgetTrimmedItems
) {

    public MemoryContextSelection {
        pack = Objects.requireNonNull(pack, "pack 不能为空");
        filteredItems = immutable(filteredItems);
        budgetTrimmedItems = immutable(budgetTrimmedItems);
    }

    private static List<MemoryContextItem> immutable(List<MemoryContextItem> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        values.forEach(item -> Objects.requireNonNull(item, "诊断列表不能包含 null"));
        return List.copyOf(values);
    }
}
