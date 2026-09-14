package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryOperation;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reconcile 旁路统计契约测试。 */
class MemoryReconcileMetricsCollectorTest {

    @Test
    void shouldAggregateSuccessfulOperationsByProjectAndChapter() {
        MemoryReconcileMetricsCollector collector = new MemoryReconcileMetricsCollector();
        MemoryCandidate candidate = MemoryCandidate.provisional(
                MemorySourceVersion.create("chapter-11", "明确事件。"),
                "明确事件。", 0, 5, MemoryCandidateType.EVENT);
        collector.record("v1-improved", 11, List.of(
                new MemoryOperationDecision(MemoryOperation.ADD, candidate, "新增事件"),
                new MemoryOperationDecision(MemoryOperation.REINFORCE, candidate, "强化事实"),
                new MemoryOperationDecision(MemoryOperation.NOOP, candidate, "无变化")));
        collector.record("v1-improved", 12, List.of(
                new MemoryOperationDecision(MemoryOperation.SUPERSEDE, candidate, "状态变化")));

        MemoryReconcileMetricsCollector.Snapshot chapter =
                collector.snapshot("v1-improved", 11);
        MemoryReconcileMetricsCollector.Snapshot cumulative =
                collector.cumulative("v1-improved");

        System.out.printf(
                "Reconcile metrics：Ch11 total=%d, reinforce+noop ratio=%.3f, cumulative supersede=%d%n",
                chapter.totalCount(), chapter.reinforceNoopRatio(), cumulative.supersedeCount());
        assertThat(chapter.addCount()).isEqualTo(1);
        assertThat(chapter.reinforceCount()).isEqualTo(1);
        assertThat(chapter.noopCount()).isEqualTo(1);
        assertThat(chapter.reinforceNoopRatio()).isEqualTo(2.0d / 3.0d);
        assertThat(cumulative.supersedeCount()).isEqualTo(1);
        assertThat(collector.snapshot("v1-current", 11).totalCount()).isZero();
    }
}
