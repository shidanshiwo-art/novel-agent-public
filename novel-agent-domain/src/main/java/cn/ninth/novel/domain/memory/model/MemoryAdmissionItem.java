package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/**
 * 一条待判断的 staging 工作集项目。
 *
 * <p>该对象只携带 admission 所需的生命周期状态，不是 Canonical 记录，也不提供持久化入口。</p>
 */
public record MemoryAdmissionItem(
        String itemId,
        MemoryAdmissionKind kind,
        MemoryFactStatus factStatus,
        MemoryProjectionStatus projectionStatus,
        boolean resolved,
        boolean ended
) {

    public MemoryAdmissionItem {
        itemId = required(itemId, "itemId");
        kind = Objects.requireNonNull(kind, "kind 不能为空");
        if (kind == MemoryAdmissionKind.FACT && factStatus == null) {
            throw new IllegalArgumentException("FACT 必须提供 factStatus");
        }
        if (kind != MemoryAdmissionKind.FACT && factStatus != null) {
            throw new IllegalArgumentException("非 FACT 项目不能提供 factStatus");
        }
        if (kind == MemoryAdmissionKind.PROJECTION && projectionStatus == null) {
            throw new IllegalArgumentException("PROJECTION 必须提供 projectionStatus");
        }
        if (kind != MemoryAdmissionKind.PROJECTION && projectionStatus != null) {
            throw new IllegalArgumentException("非 PROJECTION 项目不能提供 projectionStatus");
        }
    }

    public static MemoryAdmissionItem of(String itemId, MemoryAdmissionKind kind) {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (kind == MemoryAdmissionKind.FACT) {
            return fact(itemId, MemoryFactStatus.FACT_ACTIVE);
        }
        if (kind == MemoryAdmissionKind.PROJECTION) {
            return projection(itemId, MemoryProjectionStatus.PROJECTION_ACTIVE);
        }
        return new MemoryAdmissionItem(itemId, kind, null, null, false, false);
    }

    public static MemoryAdmissionItem fact(String itemId, MemoryFactStatus status) {
        return fact(itemId, status, false, false);
    }

    public static MemoryAdmissionItem fact(
            String itemId,
            MemoryFactStatus status,
            boolean resolved,
            boolean ended) {
        return new MemoryAdmissionItem(itemId, MemoryAdmissionKind.FACT, status, null, resolved, ended);
    }

    public static MemoryAdmissionItem projection(String itemId, MemoryProjectionStatus status) {
        return new MemoryAdmissionItem(
                itemId, MemoryAdmissionKind.PROJECTION, null, status, false, false);
    }

    public static MemoryAdmissionItem openLoop(String itemId) {
        return openLoop(itemId, false);
    }

    public static MemoryAdmissionItem openLoop(String itemId, boolean resolved) {
        return new MemoryAdmissionItem(
                itemId, MemoryAdmissionKind.OPEN_LOOP, null, null, resolved, resolved);
    }

    public static MemoryAdmissionItem event(String itemId, boolean ended) {
        return new MemoryAdmissionItem(
                itemId, MemoryAdmissionKind.EVENT, null, null, false, ended);
    }

    public static MemoryAdmissionItem currentState(String itemId) {
        return of(itemId, MemoryAdmissionKind.CURRENT_STATE);
    }

    public static MemoryAdmissionItem worldRule(String itemId) {
        return of(itemId, MemoryAdmissionKind.WORLD_RULE);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
