package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/** 一次检索请求实际采用的 Memory 路由。 */
public record MemoryRetrievalRoute(
        String memoryMode,
        boolean canonicalRequested,
        boolean canonicalHit,
        boolean legacyFallbackRequested,
        boolean legacyFallbackHit
) {

    public MemoryRetrievalRoute {
        memoryMode = Objects.requireNonNull(memoryMode, "memoryMode 不能为空");
        if (memoryMode.isBlank()) {
            throw new IllegalArgumentException("memoryMode 不能为空");
        }
    }

    public static MemoryRetrievalRoute direct(
            boolean canonicalHit,
            boolean legacyHit
    ) {
        return new MemoryRetrievalRoute(
                mode(canonicalHit, legacyHit),
                true,
                canonicalHit,
                false,
                false
        );
    }

    public static String mode(boolean canonicalHit, boolean legacyHit) {
        if (canonicalHit && legacyHit) {
            return "CANONICAL_AND_LEGACY";
        }
        if (canonicalHit) {
            return "CANONICAL";
        }
        if (legacyHit) {
            return "LEGACY";
        }
        return "NONE";
    }
}
