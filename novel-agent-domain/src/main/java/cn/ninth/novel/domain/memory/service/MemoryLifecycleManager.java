package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryAdmissionItem;
import cn.ninth.novel.domain.memory.model.MemoryAdmissionKind;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryOpenLoop;
import cn.ninth.novel.domain.memory.model.MemoryProjection;
import cn.ninth.novel.domain.memory.model.MemoryProjectionStatus;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * P0.1 staging lifecycle and current working-set admission.
 *
 * <p>生命周期转换返回新的不可变对象；admission 只计算本次工作集，不会因 recall 改写 Fact/Projection
 * 状态，也没有 Canonical 或持久化操作。</p>
 */
public final class MemoryLifecycleManager {

    /** 把被新状态取代的 Fact 移到历史视图。 */
    public MemoryFact supersede(MemoryFact fact) {
        return Objects.requireNonNull(fact, "fact 不能为空").supersede();
    }

    /** 把错误或撤回的 Fact 移出有效视图。 */
    public MemoryFact invalidate(MemoryFact fact) {
        return Objects.requireNonNull(fact, "fact 不能为空").invalidate();
    }

    /** 标记来源变化后等待重建的 Projection。 */
    public MemoryProjection markProjectionStale(MemoryProjection projection) {
        return Objects.requireNonNull(projection, "projection 不能为空").markStale();
    }

    /** 让 Projection 退出默认工作集，但保留其来源。 */
    public MemoryProjection archiveProjection(MemoryProjection projection) {
        return Objects.requireNonNull(projection, "projection 不能为空").archive();
    }

    /** 使用同一来源集合重建 Projection 的 staging 状态。 */
    public MemoryProjection reactivateProjection(MemoryProjection projection) {
        return Objects.requireNonNull(projection, "projection 不能为空").reactivate();
    }

    /** 计算一次 QuerySpec 允许进入当前工作集的 staging 项目。 */
    public List<MemoryAdmissionItem> admit(
            Collection<MemoryAdmissionItem> items,
            MemoryQuerySpec querySpec) {
        Objects.requireNonNull(items, "items 不能为空");
        Objects.requireNonNull(querySpec, "querySpec 不能为空");
        return items.stream()
                .peek(item -> Objects.requireNonNull(item, "items 不能包含 null"))
                .filter(item -> isAdmitted(item, querySpec))
                .toList();
    }

    /** 判断一条 staging 项目是否允许进入本次工作集。 */
    public boolean isAdmitted(MemoryAdmissionItem item, MemoryQuerySpec querySpec) {
        Objects.requireNonNull(item, "item 不能为空");
        Objects.requireNonNull(querySpec, "querySpec 不能为空");

        if (isFactRejected(item, querySpec) || isProjectionRejected(item, querySpec)) {
            return false;
        }
        if (item.resolved() && querySpec.profile() != MemoryProfile.REVIEW) {
            return false;
        }
        return querySpec.hasCurrentAdmissionSignal(item.itemId());
    }

    /** 别名：表达 admission 决策的布尔形式。 */
    public boolean shouldAdmit(MemoryAdmissionItem item, MemoryQuerySpec querySpec) {
        return isAdmitted(item, querySpec);
    }

    /** 别名：供调用方直接表达“是否可进入工作集”。 */
    public boolean isAdmissible(MemoryAdmissionItem item, MemoryQuerySpec querySpec) {
        return isAdmitted(item, querySpec);
    }

    /** Fact 进入 DRAFT 当前状态；Archived/Invalidated 永远不能作为当前状态。 */
    public boolean canEnterDraft(MemoryFact fact, MemoryQuerySpec querySpec) {
        return isAdmitted(
                MemoryAdmissionItem.fact(
                        Objects.requireNonNull(fact, "fact 不能为空").factId(), fact.status()),
                requireProfile(querySpec, MemoryProfile.DRAFT));
    }

    /** Fact 进入 REVIEW 历史工作集；Archived 只能由明确的 Review 查询临时读取。 */
    public boolean canEnterReview(MemoryFact fact, MemoryQuerySpec querySpec) {
        return isAdmitted(
                MemoryAdmissionItem.fact(
                        Objects.requireNonNull(fact, "fact 不能为空").factId(), fact.status()),
                requireProfile(querySpec, MemoryProfile.REVIEW));
    }

    public boolean canEnterDraft(MemoryOpenLoop openLoop, MemoryQuerySpec querySpec) {
        return isAdmitted(
                MemoryAdmissionItem.openLoop(Objects.requireNonNull(openLoop, "openLoop 不能为空").openLoopId()),
                requireProfile(querySpec, MemoryProfile.DRAFT));
    }

    public boolean canEnterReview(MemoryOpenLoop openLoop, MemoryQuerySpec querySpec) {
        return isAdmitted(
                MemoryAdmissionItem.openLoop(Objects.requireNonNull(openLoop, "openLoop 不能为空").openLoopId()),
                requireProfile(querySpec, MemoryProfile.REVIEW));
    }

    public boolean canEnterDraft(MemoryProjection projection, MemoryQuerySpec querySpec) {
        return isAdmitted(
                MemoryAdmissionItem.projection(
                        Objects.requireNonNull(projection, "projection 不能为空").projectionId(),
                        projection.status()),
                requireProfile(querySpec, MemoryProfile.DRAFT));
    }

    public boolean canEnterReview(MemoryProjection projection, MemoryQuerySpec querySpec) {
        return isAdmitted(
                MemoryAdmissionItem.projection(
                        Objects.requireNonNull(projection, "projection 不能为空").projectionId(),
                        projection.status()),
                requireProfile(querySpec, MemoryProfile.REVIEW));
    }

    private boolean isFactRejected(MemoryAdmissionItem item, MemoryQuerySpec querySpec) {
        if (item.kind() != MemoryAdmissionKind.FACT) {
            return false;
        }
        if (item.factStatus() == MemoryFactStatus.FACT_INVALIDATED) {
            return true;
        }
        return item.factStatus() == MemoryFactStatus.FACT_ARCHIVED
                && (querySpec.profile() != MemoryProfile.REVIEW
                || !querySpec.explicitlyRequests(item.itemId()));
    }

    private boolean isProjectionRejected(MemoryAdmissionItem item, MemoryQuerySpec querySpec) {
        if (item.kind() != MemoryAdmissionKind.PROJECTION) {
            return false;
        }
        if (item.projectionStatus() == MemoryProjectionStatus.PROJECTION_ACTIVE) {
            return false;
        }
        return querySpec.profile() != MemoryProfile.REVIEW
                || !querySpec.explicitlyRequests(item.itemId());
    }

    private static MemoryQuerySpec requireProfile(
            MemoryQuerySpec querySpec,
            MemoryProfile expectedProfile) {
        Objects.requireNonNull(querySpec, "querySpec 不能为空");
        if (querySpec.profile() != expectedProfile) {
            throw new IllegalArgumentException("需要 " + expectedProfile + " QuerySpec");
        }
        return querySpec;
    }
}
