package cn.ninth.novel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Planning 两类模型配置；DeepSeek 的 OpenAI-compatible 参数由 PlanningModelPort 转换为 ChatOptions。 */
@ConfigurationProperties(prefix = "novel.planning")
public class PlanningModelProperties {

    private final Map<PlanningModelProfile, ModelConfig> models =
            new EnumMap<>(PlanningModelProfile.class);
    private final Map<String, ReasoningLevel> stageReasoning = new LinkedHashMap<>();

    public Map<PlanningModelProfile, ModelConfig> getModels() {
        return models;
    }

    public ModelConfig modelForStage(String stage) {
        return models.get(profileForStage(stage));
    }

    public ModelStageConfig forStage(String stage) {
        ModelConfig config = modelForStage(stage);
        return new ModelStageConfig(
                config == null ? null : config.getModel(),
                config == null ? null : config.getTemperature(),
                reasoningForStage(stage)
        );
    }

    public Map<String, ReasoningLevel> getStageReasoning() {
        return stageReasoning;
    }

    public ReasoningLevel reasoningForStage(String stage) {
        ReasoningLevel override = stageReasoning.get(normalizeStage(stage));
        if (override != null) {
            return override;
        }
        ModelConfig config = modelForStage(stage);
        return config == null || config.getReasoning() == null
                ? ReasoningLevel.OFF
                : config.getReasoning();
    }

    public static PlanningModelProfile profileForStage(String stage) {
        String normalizedStage = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedStage) {
            case "CHARACTER_GENERATION", "CHAPTER_PLAN" -> PlanningModelProfile.FAST_STRUCTURED;
            case "STORY_BIBLE_INIT", "STORY_BIBLE_REVISION", "ROOT_OUTLINE",
                    "NEXT_VOLUME", "VOLUME_OUTLINE", "NEXT_CHAPTER_OUTLINE", "ARC_REGENERATION" ->
                    PlanningModelProfile.CREATIVE_PLANNING;
            default -> PlanningModelProfile.FAST_STRUCTURED;
        };
    }

    public enum PlanningModelProfile {
        FAST_STRUCTURED,
        CREATIVE_PLANNING
    }

    public static class ModelConfig {
        private String model;
        private Double temperature;
        private ReasoningLevel reasoning;

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
    }

    private static String normalizeStage(String stage) {
        return stage == null || stage.isBlank()
                ? "UNKNOWN"
                : stage.trim().toUpperCase(Locale.ROOT);
    }
}
