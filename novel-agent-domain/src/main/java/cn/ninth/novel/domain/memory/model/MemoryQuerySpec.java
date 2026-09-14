package cn.ninth.novel.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 当前一次工作集选择的确定性命中条件。
 *
 * <p>这里的 ID 是 staging item ID，不承担实体解析或 semantic validation。
 * QuerySpec 只表达当前请求已经明确知道的命中关系。</p>
 */
public record MemoryQuerySpec(
        MemoryProfile profile,
        Set<String> currentSceneItemIds,
        Set<String> currentEffectiveStateItemIds,
        Set<String> openOrProgressedOpenLoopIds,
        Set<String> hitWorldRuleIds,
        Set<String> explicitlyRequestedIds
) {

    public MemoryQuerySpec {
        profile = Objects.requireNonNull(profile, "profile 不能为空");
        currentSceneItemIds = immutableIds(currentSceneItemIds, "currentSceneItemIds");
        currentEffectiveStateItemIds = immutableIds(
                currentEffectiveStateItemIds, "currentEffectiveStateItemIds");
        openOrProgressedOpenLoopIds = immutableIds(
                openOrProgressedOpenLoopIds, "openOrProgressedOpenLoopIds");
        hitWorldRuleIds = immutableIds(hitWorldRuleIds, "hitWorldRuleIds");
        explicitlyRequestedIds = immutableIds(explicitlyRequestedIds, "explicitlyRequestedIds");
    }

    public MemoryQuerySpec(MemoryProfile profile) {
        this(profile, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public MemoryQuerySpec(MemoryQueryProfile profile) {
        this(toProfile(profile), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public MemoryQuerySpec(
            MemoryQueryProfile profile,
            Set<String> currentSceneItemIds,
            Set<String> currentEffectiveStateItemIds,
            Set<String> openOrProgressedOpenLoopIds,
            Set<String> hitWorldRuleIds,
            Set<String> explicitlyRequestedIds
    ) {
        this(
                toProfile(profile),
                currentSceneItemIds,
                currentEffectiveStateItemIds,
                openOrProgressedOpenLoopIds,
                hitWorldRuleIds,
                explicitlyRequestedIds);
    }

    public static MemoryQuerySpec draft() {
        return new MemoryQuerySpec(MemoryProfile.DRAFT);
    }

    public static MemoryQuerySpec plan() {
        return new MemoryQuerySpec(MemoryProfile.PLAN);
    }

    public static MemoryQuerySpec review() {
        return new MemoryQuerySpec(MemoryProfile.REVIEW);
    }

    public MemoryProfile memoryProfile() {
        return profile;
    }

    public MemoryProfile getProfile() {
        return memoryProfile();
    }

    public boolean explicitlyRequests(String itemId) {
        return explicitlyRequestedIds.contains(itemId);
    }

    public boolean hasCurrentAdmissionSignal(String itemId) {
        return currentSceneItemIds.contains(itemId)
                || currentEffectiveStateItemIds.contains(itemId)
                || openOrProgressedOpenLoopIds.contains(itemId)
                || hitWorldRuleIds.contains(itemId)
                || explicitlyRequestedIds.contains(itemId);
    }

    private static Set<String> immutableIds(Set<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能包含空 ID");
            }
            normalized.add(value.trim());
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static MemoryProfile toProfile(MemoryQueryProfile profile) {
        Objects.requireNonNull(profile, "profile 不能为空");
        return MemoryProfile.valueOf(profile.name());
    }
}
