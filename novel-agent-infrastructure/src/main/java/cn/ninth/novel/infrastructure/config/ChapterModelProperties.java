package cn.ninth.novel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Chapter 模型的公共配置和少量 stage 思考强度覆盖。 */
@ConfigurationProperties(prefix = "novel.chapter")
public class ChapterModelProperties {

    private String profile = "CHAPTER";
    private String model;
    private Double temperature;
    private ReasoningLevel reasoning = ReasoningLevel.LOW;
    private final Map<String, ReasoningLevel> stageReasoning = new LinkedHashMap<>();

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String effectiveProfile() {
        return profile == null || profile.isBlank() ? "CHAPTER" : profile.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public ReasoningLevel getReasoning() {
        return reasoning;
    }

    public void setReasoning(ReasoningLevel reasoning) {
        this.reasoning = reasoning;
    }

    public Map<String, ReasoningLevel> getStageReasoning() {
        return stageReasoning;
    }

    public ModelStageConfig forStage(String stage) {
        return new ModelStageConfig(model, temperature, reasoningForStage(stage));
    }

    public ReasoningLevel reasoningForStage(String stage) {
        return stageReasoning.getOrDefault(
                normalizeStage(stage),
                reasoning == null ? ReasoningLevel.OFF : reasoning
        );
    }

    private String normalizeStage(String stage) {
        return stage == null || stage.isBlank()
                ? "UNKNOWN"
                : stage.trim().toUpperCase(Locale.ROOT);
    }
}
