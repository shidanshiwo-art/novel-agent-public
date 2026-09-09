package cn.ninth.novel.api.dto;

/** 开发用途的章节模型 Prompt Trace 查询结果。 */
public record PromptTraceResponseDTO(
        String node,
        int attempt,
        boolean success,
        long durationMs,
        String errorMessage,
        String systemPrompt,
        String userPrompt,
        String responseText
) {
}
