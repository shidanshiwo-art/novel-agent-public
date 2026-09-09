package cn.ninth.novel.domain.planning.adapter.port;

public interface IPlanningModelPort {

    <T> T call(String systemPrompt, String userPrompt, Class<T> responseType);

    /** 带观测上下文的结构化模型调用。 */
    default <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt
    ) {
        return call(systemPrompt, userPrompt, responseType);
    }
}
