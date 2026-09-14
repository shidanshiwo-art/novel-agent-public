package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;

/** P0.5 正式事实，状态独立于 Event 和 Projection。 */
public record CanonicalFact(
        String factId,
        String projectCode,
        String proposition,
        List<String> sourceIds,
        MemoryFactStatus status,
        String candidateId
) {

    public CanonicalFact {
        factId = required(factId, "factId");
        projectCode = required(projectCode, "projectCode");
        proposition = required(proposition, "proposition");
        sourceIds = immutableSources(sourceIds);
        status = Objects.requireNonNull(status, "status 不能为空");
        candidateId = required(candidateId, "candidateId");
    }

    public CanonicalFact(
            String factId,
            String projectCode,
            String proposition,
            List<String> sourceIds,
            MemoryFactStatus status
    ) {
        this(factId, projectCode, proposition, sourceIds, status, factId);
    }

    private static List<String> immutableSources(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("sourceIds 至少需要一个来源");
        }
        List<String> sources = values.stream()
                .map(value -> required(value, "sourceId"))
                .distinct()
                .toList();
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sourceIds 至少需要一个来源");
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
