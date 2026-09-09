package cn.ninth.novel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 结构化模型调用的 stage 级超时配置。 */
@ConfigurationProperties(prefix = "novel.model")
public class ModelTimeoutProperties {

    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HTTP_TIMEOUT_BUFFER = Duration.ofSeconds(30);

    private final Map<String, Duration> structuredTimeouts = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("CHARACTER_GENERATION", Duration.ofSeconds(180)),
            Map.entry("CHAPTER_PLAN", Duration.ofSeconds(180)),
            Map.entry("ROOT_OUTLINE", Duration.ofSeconds(240)),
            Map.entry("NEXT_VOLUME", Duration.ofSeconds(240)),
            Map.entry("NEXT_CHAPTER_OUTLINE", Duration.ofSeconds(240)),
            Map.entry("REVIEW", Duration.ofSeconds(300)),
            Map.entry("COMPRESSION", Duration.ofSeconds(180)),
            Map.entry("STORY_BIBLE_INIT", Duration.ofSeconds(180)),
            Map.entry("STORY_BIBLE_REVISION", Duration.ofSeconds(180)),
            Map.entry("VOLUME_OUTLINE", Duration.ofSeconds(240)),
            Map.entry("ARC_REGENERATION", Duration.ofSeconds(240))
    ));

    private Duration defaultStructuredTimeout = Duration.ofSeconds(180);

    public Map<String, Duration> getStructuredTimeouts() {
        return structuredTimeouts;
    }

    public Duration timeoutForStage(String stage) {
        return structuredTimeouts.getOrDefault(normalizeStage(stage), defaultStructuredTimeout);
    }

    public Duration maximumStructuredTimeout() {
        return structuredTimeouts.values().stream()
                .max(Duration::compareTo)
                .orElse(defaultStructuredTimeout);
    }

    public Duration httpConnectTimeout() {
        return HTTP_CONNECT_TIMEOUT;
    }

    public Duration httpReadAndRequestTimeout() {
        return maximumStructuredTimeout().plus(HTTP_TIMEOUT_BUFFER);
    }

    public static ModelTimeoutProperties withDefaultTimeout(Duration timeout) {
        ModelTimeoutProperties properties = new ModelTimeoutProperties();
        properties.defaultStructuredTimeout = timeout;
        properties.structuredTimeouts.replaceAll((stage, ignored) -> timeout);
        return properties;
    }

    private String normalizeStage(String stage) {
        return stage == null || stage.isBlank()
                ? "UNKNOWN"
                : stage.trim().toUpperCase(Locale.ROOT);
    }
}
