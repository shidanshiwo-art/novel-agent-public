package cn.ninth.novel.infrastructure.adapter.port;

import org.springframework.core.env.Environment;

import java.net.URI;

final class ModelObservabilityMetadata {

    private static final String UNKNOWN = "unknown";

    private final String provider;
    private final String modelName;
    private final String baseUrlHost;

    private ModelObservabilityMetadata(
            String provider,
            String modelName,
            String baseUrlHost
    ) {
        this.provider = provider;
        this.modelName = modelName;
        this.baseUrlHost = baseUrlHost;
    }

    static ModelObservabilityMetadata from(Environment environment) {
        String provider = valueOrUnknown(
                environment.getProperty("spring.ai.model.chat")
        );
        String providerPrefix = "spring.ai." + provider;
        String modelName = firstValue(
                environment.getProperty(providerPrefix + ".chat.model"),
                environment.getProperty(providerPrefix + ".chat.options.model")
        );
        String baseUrl = environment.getProperty(providerPrefix + ".base-url");
        return new ModelObservabilityMetadata(
                provider,
                valueOrUnknown(modelName),
                hostOnly(baseUrl)
        );
    }

    static ModelObservabilityMetadata unknown() {
        return new ModelObservabilityMetadata(UNKNOWN, UNKNOWN, null);
    }

    String provider() {
        return provider;
    }

    String modelName() {
        return modelName;
    }

    String baseUrlHost() {
        return baseUrlHost;
    }

    ModelObservabilityMetadata withModel(String modelName) {
        return new ModelObservabilityMetadata(
                provider,
                valueOrUnknown(modelName),
                baseUrlHost
        );
    }

    private static String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value.trim();
    }

    private static String hostOnly(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(baseUrl.trim()).getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
