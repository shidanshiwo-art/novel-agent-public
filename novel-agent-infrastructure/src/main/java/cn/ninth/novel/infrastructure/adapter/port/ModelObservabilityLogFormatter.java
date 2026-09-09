package cn.ninth.novel.infrastructure.adapter.port;

final class ModelObservabilityLogFormatter {

    private ModelObservabilityLogFormatter() {
    }

    static String format(
            String status,
            String mode,
            String stage,
            int attempt,
            String failureType,
            String reason,
            ModelObservabilityMetadata metadata,
            String responseType,
            Integer systemChars,
            Integer userChars,
            Long requestCostMs,
            Long readCostMs,
            Long contentReadMs,
            Long parseCostMs,
            Long ttftMs,
            Long totalCostMs,
            Integer chunkCount,
            Integer contentChars,
            String finishReason,
            Object usage
    ) {
        return format(
                status,
                mode,
                stage,
                attempt,
                failureType,
                reason,
                metadata,
                responseType,
                systemChars,
                userChars,
                requestCostMs,
                readCostMs,
                contentReadMs,
                parseCostMs,
                ttftMs,
                null,
                totalCostMs,
                chunkCount,
                contentChars,
                finishReason,
                usage
        );
    }

    static String format(
            String status,
            String mode,
            String stage,
            int attempt,
            String failureType,
            String reason,
            ModelObservabilityMetadata metadata,
            String responseType,
            Integer systemChars,
            Integer userChars,
            Long requestCostMs,
            Long readCostMs,
            Long contentReadMs,
            Long parseCostMs,
            Long ttftMs,
            Long generationMs,
            Long totalCostMs,
            Integer chunkCount,
            Integer contentChars,
            String finishReason,
            Object usage
    ) {
        StringBuilder message = new StringBuilder("[MODEL]");
        append(message, "status", status);
        append(message, "mode", mode);
        append(message, "stage", stage);
        append(message, "attempt", attempt);
        append(message, "failureType", failureType);
        append(message, "reason", reason);
        append(message, "model", metadata == null ? null : metadata.modelName());
        append(message, "provider", metadata == null ? null : metadata.provider());
        append(message, "baseUrlHost", metadata == null ? null : metadata.baseUrlHost());
        append(message, "responseType", responseType);
        append(message, "systemChars", systemChars);
        append(message, "userChars", userChars);
        append(message, "requestCostMs", requestCostMs);
        append(message, "readCostMs", readCostMs);
        append(message, "contentReadMs", contentReadMs);
        append(message, "parseCostMs", parseCostMs);
        append(message, "ttftMs", ttftMs);
        append(message, "generationMs", generationMs);
        append(message, "totalCostMs", totalCostMs);
        append(message, "chunkCount", chunkCount);
        append(message, "contentChars", contentChars);
        append(message, "finishReason", finishReason);
        append(message, "usage", usage);
        return message.toString();
    }

    static String format(
            String status,
            String mode,
            String stage,
            int attempt,
            String failureType,
            String reason,
            ModelObservabilityMetadata metadata,
            String responseType,
            Integer systemChars,
            Integer userChars,
            Long requestCostMs,
            Long readCostMs,
            Long parseCostMs,
            Long ttftMs,
            Long totalCostMs,
            Integer chunkCount,
            Integer contentChars,
            String finishReason,
            Object usage
    ) {
        return format(
                status,
                mode,
                stage,
                attempt,
                failureType,
                reason,
                metadata,
                responseType,
                systemChars,
                userChars,
                requestCostMs,
                readCostMs,
                null,
                parseCostMs,
                ttftMs,
                totalCostMs,
                chunkCount,
                contentChars,
                finishReason,
                usage
        );
    }

    static String withLocation(String message, Integer line, Integer column) {
        if (line == null && column == null) {
            return message;
        }
        StringBuilder located = new StringBuilder(message);
        append(located, "line", line);
        append(located, "column", column);
        return located.toString();
    }

    static String withPlanningConfiguration(
            String message,
            String profile,
            Double temperature,
            String reasoning
    ) {
        return withEffectiveConfiguration(message, profile, null, reasoning, temperature);
    }

    static String withEffectiveConfiguration(
            String message,
            String profile,
            Long timeoutMs,
            String reasoning,
            Double temperature
    ) {
        StringBuilder configured = new StringBuilder(message);
        append(configured, "profile", profile);
        append(configured, "timeoutMs", timeoutMs);
        append(configured, "reasoning", reasoning);
        append(configured, "temperature", temperature);
        return configured.toString();
    }

    static String withStreamMetrics(
            String message,
            Long firstResponseMs,
            Long firstContentMs,
            Integer completionTokens
    ) {
        StringBuilder metrics = new StringBuilder(message);
        append(metrics, "firstResponseMs", firstResponseMs);
        append(metrics, "firstContentMs", firstContentMs);
        append(metrics, "completionTokens", completionTokens);
        return metrics.toString();
    }

    private static void append(StringBuilder message, String name, Object value) {
        if (value != null) {
            message.append(' ').append(name).append('=').append(value);
        }
    }
}
