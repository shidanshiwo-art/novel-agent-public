package cn.ninth.novel.infrastructure.config;

/** 一个 stage 实际使用的模型调用配置；不包含 token 上限。 */
public record ModelStageConfig(
        String model,
        Double temperature,
        ReasoningLevel reasoning
) {

    public ModelStageConfig {
        reasoning = reasoning == null ? ReasoningLevel.OFF : reasoning;
    }
}
