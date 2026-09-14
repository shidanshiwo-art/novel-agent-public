package cn.ninth.novel.domain.memory.model;

import java.util.List;

/** 尚未解决的剧情目标、伏笔或承诺。 */
public record MemoryOpenLoop(
        String openLoopId,
        String description,
        List<String> sourceIds
) {

    public MemoryOpenLoop {
        openLoopId = required(openLoopId, "openLoopId");
        description = required(description, "description");
        sourceIds = immutableSources(sourceIds);
    }

    public MemoryOpenLoop(String openLoopId, String description) {
        this(openLoopId, description, List.of());
    }

    public String getOpenLoopId() {
        return openLoopId;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static List<String> immutableSources(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> required(value, "sourceId"))
                .distinct()
                .toList();
    }
}
