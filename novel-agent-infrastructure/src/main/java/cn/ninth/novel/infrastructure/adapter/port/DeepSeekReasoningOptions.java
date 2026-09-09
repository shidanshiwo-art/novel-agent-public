package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.infrastructure.config.ReasoningLevel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;

/**
 * 将统一思考强度转换为 DeepSeek OpenAI-compatible Chat Completions 字段。
 *
 * <p>DeepSeek Chat Completions 当前支持 low/high/max；medium 按接口兼容规则映射为 high。
 */
final class DeepSeekReasoningOptions {

    private DeepSeekReasoningOptions() {
    }

    static void apply(OpenAiChatOptions.Builder builder, ReasoningLevel level) {
        ReasoningLevel normalizedLevel = level == null ? ReasoningLevel.OFF : level;
        if (normalizedLevel == ReasoningLevel.OFF) {
            builder.extraBody(thinking("disabled"));
            return;
        }
        builder.reasoningEffort(normalizedLevel == ReasoningLevel.LOW ? "low" : "high");
        builder.extraBody(thinking("enabled"));
    }

    private static Map<String, Object> thinking(String type) {
        return Map.of("thinking", Map.of("type", type));
    }
}
