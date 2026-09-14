package cn.ninth.novel.domain.memory.model;

import java.util.Locale;

/**
 * 一次生成运行使用的 Memory 路由模式。
 *
 * <p>模式只影响 Memory 读取路径，不影响模型参数、Prompt 模板或工作流编排。</p>
 */
public enum MemoryMode {
    /** 只使用现有 ChapterMemory/StoryStateSnapshot Legacy bridge。 */
    LEGACY,
    /** 以 Canonical Memory 为主，按明确条件惰性回退到 Legacy bridge。 */
    V1,
    /** 兼容旧调用方的默认模式，沿用迁移期间的 Canonical-first fallback 策略。 */
    AUTO;

    public static MemoryMode defaultMode() {
        return AUTO;
    }

    /** 将 HTTP/配置中的字符串解析为模式；空值使用兼容默认值。 */
    public static MemoryMode parse(String value) {
        if (value == null || value.isBlank()) {
            return defaultMode();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "memoryMode 必须是 LEGACY、V1 或 AUTO", exception);
        }
    }
}
