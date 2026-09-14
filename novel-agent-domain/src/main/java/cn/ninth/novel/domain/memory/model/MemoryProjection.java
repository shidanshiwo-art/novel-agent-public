package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/**
 * 对多个已接受信息的可重建压缩。
 *
 * <p>Projection 只是一层派生视图，必须保留来源 ID，不能替代来源 Event 或 Fact
 * 成为真相源；其生命周期也与来源 Fact 分开维护。</p>
 */
public final class MemoryProjection {

    private final String projectionId;
    private final String content;
    private final List<String> sourceIds;
    private final MemoryProjectionStatus status;

    public MemoryProjection(String projectionId, String content, List<String> sourceIds) {
        this(projectionId, content, sourceIds, MemoryProjectionStatus.PROJECTION_ACTIVE);
    }

    public MemoryProjection(
            String projectionId,
            String content,
            List<String> sourceIds,
            MemoryProjectionStatus status
    ) {
        this.projectionId = required(projectionId, "projectionId");
        this.content = required(content, "content");
        this.sourceIds = immutableSources(sourceIds);
        this.status = Objects.requireNonNull(status, "status 不能为空");
    }

    public MemoryProjection(
            String projectionId,
            String content,
            MemoryProjectionStatus status,
            List<String> sourceIds
    ) {
        this(projectionId, content, sourceIds, status);
    }

    public String projectionId() {
        return projectionId;
    }

    public String content() {
        return content;
    }

    public List<String> sourceIds() {
        return sourceIds;
    }

    public MemoryProjectionStatus status() {
        return status;
    }

    public String getProjectionId() {
        return projectionId;
    }

    public String getContent() {
        return content;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }

    public MemoryProjectionStatus getStatus() {
        return status;
    }

    /** 来源发生变化，当前压缩结果等待重建。 */
    public MemoryProjection markStale() {
        return withStatus(MemoryProjectionStatus.PROJECTION_STALE);
    }

    /** 投影退出默认召回，但来源仍然保留。 */
    public MemoryProjection archive() {
        return withStatus(MemoryProjectionStatus.PROJECTION_ARCHIVED);
    }

    /** 使用同一来源集合重建出可用投影。 */
    public MemoryProjection reactivate() {
        return withStatus(MemoryProjectionStatus.PROJECTION_ACTIVE);
    }

    private MemoryProjection withStatus(MemoryProjectionStatus nextStatus) {
        return new MemoryProjection(projectionId, content, sourceIds, nextStatus);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static List<String> immutableSources(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Projection 至少需要两个来源");
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
}
