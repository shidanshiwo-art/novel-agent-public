package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryConsolidationResult;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryOpenLoop;
import cn.ninth.novel.domain.memory.model.MemoryOpenLoopStatus;
import cn.ninth.novel.domain.memory.model.MemoryProjection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * P0.3 限定 Consolidation。
 *
 * <p>只支持同一 Fact 的 REINFORCE 和已解决 Open Loop 的结果摘要。返回值全部是
 * staging 对象；本服务没有 Canonical、持久化、Generalize、Retire 或复杂 MERGE 入口。</p>
 */
public final class ConsolidationService {

    /** 同一 Fact 只保留一个语义对象，并去重累积支持来源。 */
    public MemoryFact reinforceSameFact(
            MemoryFact fact,
            Collection<String> additionalEvidence) {
        return Objects.requireNonNull(fact, "fact 不能为空").reinforce(additionalEvidence);
    }

    public MemoryFact reinforceSameFact(MemoryFact fact, String additionalEvidence) {
        return Objects.requireNonNull(fact, "fact 不能为空").reinforce(additionalEvidence);
    }

    /**
     * 对 OPEN/PROGRESSED 线程生成结果摘要；传入的文本必须来自上游已确认结果，
     * 本方法只封装文本和来源，不从多个事实自动推导结论。
     */
    public MemoryConsolidationResult summarizeResolvedThread(
            MemoryOpenLoop openLoop,
            MemoryOpenLoopStatus fromStatus,
            String result,
            String directImpact,
            Collection<String> sourceEvidence) {
        Objects.requireNonNull(openLoop, "openLoop 不能为空");
        Objects.requireNonNull(fromStatus, "fromStatus 不能为空");
        if (fromStatus != MemoryOpenLoopStatus.OPEN
                && fromStatus != MemoryOpenLoopStatus.PROGRESSED) {
            throw new IllegalArgumentException(
                    "只有 OPEN / PROGRESSED Open Loop 才能转为 RESOLVED 摘要");
        }
        List<String> immutableEvidence = sourceEvidence == null
                ? null
                : List.copyOf(sourceEvidence);
        return new MemoryConsolidationResult(result, directImpact, immutableEvidence);
    }

    /** 创建 staging Projection；内容和来源均由摘要确定性提供。 */
    public MemoryProjection createProjection(
            String projectionId,
            MemoryConsolidationResult summary) {
        Objects.requireNonNull(summary, "summary 不能为空");
        requireProjectionSources(summary);
        return new MemoryProjection(
                projectionId,
                summary.projectionContent(),
                summary.sourceEvidence());
    }

    /**
     * Projection 被删除或丢弃后，从保留的 staging 摘要重新创建同一投影。
     */
    public MemoryProjection rebuildProjection(
            String projectionId,
            MemoryConsolidationResult summary) {
        return createProjection(projectionId, summary);
    }

    public MemoryProjection rebuildProjection(
            MemoryProjection deletedProjection,
            MemoryConsolidationResult summary) {
        Objects.requireNonNull(deletedProjection, "deletedProjection 不能为空");
        return rebuildProjection(deletedProjection.projectionId(), summary);
    }

    private static void requireProjectionSources(MemoryConsolidationResult summary) {
        if (summary.sourceEvidence().size() < 2) {
            throw new IllegalArgumentException(
                    "Projection 重建至少需要两个不同的 sourceEvidence");
        }
    }
}
