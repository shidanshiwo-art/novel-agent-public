package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/** P0.5 正式投影；投影永远保留可重建的来源集合。 */
public record CanonicalProjection(
        String projectionId,
        String projectCode,
        String content,
        List<String> sourceIds,
        MemoryProjectionStatus status
) {

    public CanonicalProjection {
        projectionId = required(projectionId, "projectionId");
        projectCode = required(projectCode, "projectCode");
        content = required(content, "content");
        sourceIds = immutableSources(sourceIds);
        status = Objects.requireNonNull(status, "status 不能为空");
    }

    public CanonicalProjection(
            String projectionId,
            String projectCode,
            String content,
            List<String> sourceIds
    ) {
        this(projectionId, projectCode, content, sourceIds,
                MemoryProjectionStatus.PROJECTION_ACTIVE);
    }

    private static List<String> immutableSources(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("sourceIds 至少需要一个来源");
        }
        List<String> sources = values.stream()
                .map(value -> required(value, "sourceId"))
                .distinct()
                .toList();
        if (sources.size() < 2) {
            throw new IllegalArgumentException("Projection 至少需要两个不同来源");
        }
        return sources;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
