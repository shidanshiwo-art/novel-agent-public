package cn.ninth.novel.domain.chapter.model.valobj;

import java.time.Instant;

/**
 * 单次章节模型调用的 Prompt Trace。
 *
 * <p>Trace 在模型请求开始前创建元数据，在请求完成后补齐响应、结果和耗时。
 * attempt 从 0 开始计数，首次调用为 0，因此同一节点的每次模型重试都会产生独立记录。</p>
 */
public record PromptTraceRecord(
        String workflowId,
        String projectCode,
        Integer chapterNumber,
        String node,
        int attempt,
        String systemPrompt,
        String userPrompt,
        Instant createdAt,
        String responseText,
        boolean success,
        String errorCode,
        String errorMessage,
        long durationMs
) {

    public static PromptTraceRecord now(
            String workflowId,
            String projectCode,
            Integer chapterNumber,
            String node,
            int attempt,
            String systemPrompt,
            String userPrompt
    ) {
        return new PromptTraceRecord(
                workflowId,
                projectCode,
                chapterNumber,
                node,
                attempt,
                systemPrompt,
                userPrompt,
                Instant.now(),
                null,
                false,
                null,
                null,
                0L
        );
    }

    public PromptTraceRecord completed(
            String responseText,
            boolean success,
            String errorCode,
            String errorMessage,
            long durationMs
    ) {
        return new PromptTraceRecord(
                workflowId,
                projectCode,
                chapterNumber,
                node,
                attempt,
                systemPrompt,
                userPrompt,
                createdAt,
                responseText,
                success,
                errorCode,
                errorMessage,
                Math.max(0L, durationMs)
        );
    }
}
