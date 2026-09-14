package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/** 一次新旧检索可比较请求的 fallback 观测。 */
public record LegacyFallbackObservation(
        MemoryProfile profile,
        boolean comparable,
        boolean legacyFallbackHit,
        boolean legacyMiss
) {

    public LegacyFallbackObservation {
        profile = Objects.requireNonNull(profile, "profile 不能为空");
        if (legacyMiss && !comparable) {
            throw new IllegalArgumentException("legacyMiss 只能计入可比较请求");
        }
    }
}
