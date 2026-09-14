package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/**
 * 一条供 P0 ContextProvider 选择的统一候选。
 *
 * <p>它只携带确定性召回和预算裁剪所需的最小信息；staging/archived/bridge
 * 是本次召回边界，不是对底层 Canonical 生命周期的写操作。</p>
 */
public record MemoryContextItem(
        String itemId,
        MemoryContextCategory category,
        String content,
        int sourceChapter,
        boolean staging,
        boolean archived,
        boolean bridgeOnly,
        int priority,
        String sourceType,
        String sourceId,
        String sourceVersion,
        String status,
        String storyTime,
        Integer order
) {

    public MemoryContextItem {
        itemId = required(itemId, "itemId");
        category = Objects.requireNonNull(category, "category 不能为空");
        content = required(content, "content");
        if (sourceChapter < 0) {
            throw new IllegalArgumentException("sourceChapter 不能小于 0");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority 不能小于 0");
        }
        sourceType = optional(sourceType);
        sourceId = sourceId == null || sourceId.isBlank() ? itemId : sourceId.trim();
        sourceVersion = optional(sourceVersion);
        status = optional(status);
        storyTime = optional(storyTime);
    }

    public MemoryContextItem(
            String itemId,
            MemoryContextCategory category,
            String content,
            int sourceChapter
    ) {
        this(itemId, category, content, sourceChapter, false, false, false, 0,
                null, itemId, null, null, null, null);
    }

    /** 保留已有调用方使用的完整基础构造器。 */
    public MemoryContextItem(
            String itemId,
            MemoryContextCategory category,
            String content,
            int sourceChapter,
            boolean staging,
            boolean archived,
            boolean bridgeOnly,
            int priority
    ) {
        this(itemId, category, content, sourceChapter, staging, archived,
                bridgeOnly, priority, null, itemId, null, null, null, null);
    }

    /**
     * 创建已经通过 Canonical 读取边界过滤的领域候选。
     *
     * <p>status 只表示对应 Canonical 对象的生命周期；Event 使用
     * {@code ACCEPTED} 表示来源版本已被接受，不复用 Fact 状态。</p>
     */
    public static MemoryContextItem canonical(
            String sourceType,
            String sourceId,
            MemoryContextCategory category,
            String content,
            int sourceChapter,
            String sourceVersion,
            String status,
            String storyTime,
            Integer order
    ) {
        boolean archived = "FACT_ARCHIVED".equalsIgnoreCase(status)
                || "PROJECTION_ARCHIVED".equalsIgnoreCase(status)
                || "PROJECTION_STALE".equalsIgnoreCase(status);
        return new MemoryContextItem(
                sourceId,
                category,
                content,
                sourceChapter,
                false,
                archived,
                false,
                0,
                sourceType,
                sourceId,
                sourceVersion,
                status,
                storyTime,
                order);
    }

    public static MemoryContextItem of(
            String itemId,
            MemoryContextCategory category,
            String content,
            int sourceChapter
    ) {
        return new MemoryContextItem(itemId, category, content, sourceChapter);
    }

    public static MemoryContextItem bridge(
            String itemId,
            MemoryContextCategory category,
            String content,
            int sourceChapter
    ) {
        return new MemoryContextItem(
                itemId, category, content, sourceChapter, false, false, true, 0,
                "LEGACY_BRIDGE", itemId, null, null, null, null);
    }

    public static MemoryContextItem staging(
            String itemId,
            MemoryContextCategory category,
            String content,
            int sourceChapter
    ) {
        return new MemoryContextItem(
                itemId, category, content, sourceChapter, true, false, false, 0,
                "STAGING", itemId, null, null, null, null);
    }

    public static MemoryContextItem archived(
            String itemId,
            MemoryContextCategory category,
            String content,
            int sourceChapter
    ) {
        return new MemoryContextItem(
                itemId, category, content, sourceChapter, false, true, false, 0,
                null, itemId, null, null, null, null);
    }

    /** P0 使用的稳定估算：每 4 个字符计一个 token，至少为 1。 */
    public int estimatedTokenCount() {
        return Math.max(1, (content.length() + 3) / 4);
    }

    /** 指标使用的来源分组；旧基础构造器按 bridgeOnly 推断。 */
    public String canonicalOrLegacy() {
        if (bridgeOnly || (sourceType != null && sourceType.toUpperCase().startsWith("LEGACY"))) {
            return "LEGACY";
        }
        return "CANONICAL";
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
