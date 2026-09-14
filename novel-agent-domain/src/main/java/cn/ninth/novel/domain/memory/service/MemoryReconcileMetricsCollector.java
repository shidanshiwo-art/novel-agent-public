package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryOperation;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical Reconcile 操作的旁路观测器。
 *
 * <p>只在 Gate 成功提交后记录操作，不参与 Reconcile、Gate 或 Recall 决策；
 * 因此它不会改变正式 Memory 的生命周期。按项目和章节隔离，供实验报告读取。</p>
 */
@Component
public final class MemoryReconcileMetricsCollector {

    private final Map<Key, EnumMap<MemoryOperation, Long>> counts = new java.util.HashMap<>();

    public synchronized void record(
            String projectCode,
            int chapterNumber,
            List<MemoryOperationDecision> decisions
    ) {
        if (projectCode == null || projectCode.isBlank() || decisions == null) {
            return;
        }
        EnumMap<MemoryOperation, Long> chapterCounts = counts.computeIfAbsent(
                new Key(projectCode, chapterNumber), ignored -> emptyCounts());
        for (MemoryOperationDecision decision : decisions) {
            if (decision != null && decision.operation() != null) {
                chapterCounts.merge(decision.operation(), 1L, Long::sum);
            }
        }
    }

    public synchronized Snapshot snapshot(String projectCode, int chapterNumber) {
        return snapshot(counts.get(new Key(projectCode, chapterNumber)));
    }

    public synchronized Snapshot cumulative(String projectCode) {
        EnumMap<MemoryOperation, Long> total = emptyCounts();
        counts.forEach((key, values) -> {
            if (key.projectCode().equals(projectCode)) {
                values.forEach((operation, count) -> total.merge(operation, count, Long::sum));
            }
        });
        return snapshot(total);
    }

    public synchronized void clear(String projectCode) {
        counts.keySet().removeIf(key -> key.projectCode().equals(projectCode));
    }

    private Snapshot snapshot(EnumMap<MemoryOperation, Long> values) {
        EnumMap<MemoryOperation, Long> source = values == null ? emptyCounts() : values;
        return new Snapshot(
                source.getOrDefault(MemoryOperation.ADD, 0L),
                source.getOrDefault(MemoryOperation.REINFORCE, 0L),
                source.getOrDefault(MemoryOperation.SUPERSEDE, 0L),
                source.getOrDefault(MemoryOperation.INVALIDATE, 0L),
                source.getOrDefault(MemoryOperation.NOOP, 0L));
    }

    private EnumMap<MemoryOperation, Long> emptyCounts() {
        EnumMap<MemoryOperation, Long> result = new EnumMap<>(MemoryOperation.class);
        for (MemoryOperation operation : MemoryOperation.values()) {
            result.put(operation, 0L);
        }
        return result;
    }

    public record Snapshot(
            long addCount,
            long reinforceCount,
            long supersedeCount,
            long invalidateCount,
            long noopCount
    ) {

        public Snapshot {
            if (addCount < 0 || reinforceCount < 0 || supersedeCount < 0
                    || invalidateCount < 0 || noopCount < 0) {
                throw new IllegalArgumentException("Reconcile 指标不能为负数");
            }
        }

        public long totalCount() {
            return addCount + reinforceCount + supersedeCount + invalidateCount + noopCount;
        }

        /** REINFORCE 与 NOOP 在全部 Reconcile 操作中的占比。 */
        public double reinforceNoopRatio() {
            return totalCount() == 0
                    ? 0.0d
                    : (double) (reinforceCount + noopCount) / totalCount();
        }

        public static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0);
        }
    }

    private record Key(String projectCode, int chapterNumber) {
    }
}
