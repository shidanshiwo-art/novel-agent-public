package cn.ninth.novel.domain.memory.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 根据已接受证据当前或历史成立的命题。
 *
 * <p>Fact 的生命周期独立于 Event 和 Projection：被后续状态取代时归档，
 * 被证据证明错误或撤回时失效，均保留原命题对象和来源。</p>
 */
public final class MemoryFact {

    private final String factId;
    private final String proposition;
    private final List<String> sourceIds;
    private final MemoryFactStatus status;

    public MemoryFact(String factId, String proposition, List<String> sourceIds) {
        this(factId, proposition, sourceIds, MemoryFactStatus.FACT_ACTIVE);
    }

    public MemoryFact(
            String factId,
            String proposition,
            List<String> sourceIds,
            MemoryFactStatus status
    ) {
        this.factId = required(factId, "factId");
        this.proposition = required(proposition, "proposition");
        this.sourceIds = immutableSources(sourceIds, "sourceIds");
        this.status = Objects.requireNonNull(status, "status 不能为空");
    }

    public MemoryFact(
            String factId,
            String proposition,
            MemoryFactStatus status,
            List<String> sourceIds
    ) {
        this(factId, proposition, sourceIds, status);
    }

    public String factId() {
        return factId;
    }

    public String proposition() {
        return proposition;
    }

    public List<String> sourceIds() {
        return sourceIds;
    }

    public MemoryFactStatus status() {
        return status;
    }

    public String getFactId() {
        return factId;
    }

    public String getProposition() {
        return proposition;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }

    public MemoryFactStatus getStatus() {
        return status;
    }

    /** 新状态取代当前命题，保留该命题作为历史事实。 */
    public MemoryFact supersede() {
        ensureNotInvalidated();
        return withStatus(MemoryFactStatus.FACT_ARCHIVED);
    }

    /** 证据证明该命题错误或来源被撤回。 */
    public MemoryFact invalidate() {
        return withStatus(MemoryFactStatus.FACT_INVALIDATED);
    }

    /**
     * 为同一 Fact 增加新的支持来源；重复来源不会产生重复 Fact 或重复 Evidence。
     */
    public MemoryFact reinforce(Collection<String> additionalSourceIds) {
        ensureActiveForReinforce();
        if (additionalSourceIds == null) {
            throw new IllegalArgumentException("additionalSourceIds 不能为空");
        }
        if (additionalSourceIds.isEmpty()) {
            return this;
        }

        LinkedHashSet<String> mergedSources = new LinkedHashSet<>(sourceIds);
        for (String sourceId : additionalSourceIds) {
            mergedSources.add(required(sourceId, "sourceId"));
        }
        if (mergedSources.size() == sourceIds.size()) {
            return this;
        }
        return new MemoryFact(factId, proposition, List.copyOf(mergedSources), status);
    }

    public MemoryFact reinforce(String additionalSourceId) {
        return reinforce(List.of(additionalSourceId));
    }

    private MemoryFact withStatus(MemoryFactStatus nextStatus) {
        return new MemoryFact(factId, proposition, sourceIds, nextStatus);
    }

    private void ensureNotInvalidated() {
        if (status == MemoryFactStatus.FACT_INVALIDATED) {
            throw new IllegalStateException("已失效的 Fact 不能再次被 SUPERSEDE");
        }
    }

    private void ensureActiveForReinforce() {
        if (status != MemoryFactStatus.FACT_ACTIVE) {
            throw new IllegalStateException("只有 FACT_ACTIVE 才能执行 REINFORCE");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static List<String> immutableSources(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " 至少需要一个来源");
        }
        List<String> sources = values.stream()
                .map(value -> required(value, "sourceId"))
                .distinct()
                .toList();
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(field + " 至少需要一个来源");
        }
        return sources;
    }
}
