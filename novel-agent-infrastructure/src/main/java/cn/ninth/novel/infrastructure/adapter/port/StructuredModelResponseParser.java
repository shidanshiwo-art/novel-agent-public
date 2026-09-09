package cn.ninth.novel.infrastructure.adapter.port;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;

/** 统一清洗外壳并解析结构化响应，不调用模型或校验业务字段。 */
final class StructuredModelResponseParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredModelResponseParser() { }

    static <T> T parse(String raw, Class<T> type) {
        String stage = "FORMAT_CLEANUP_FAILED";
        String normalized = null;
        try {
            normalized = normalize(raw);
            stage = "JSON_PARSE_FAILED";
            var tree = MAPPER.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(normalized);
            stage = "SCHEMA_VALIDATION_FAILED";
            return MAPPER.treeToValue(tree, type);
        } catch (Exception exception) {
            throw new ParseException(stage, exception, normalized);
        }
    }

    static final class ParseException extends RuntimeException {
        private final String failureType;
        private final String normalizedResponse;
        private final Integer line;
        private final Integer column;

        ParseException(String failureType, Throwable cause, String normalizedResponse) {
            // Jackson 的 message/cause 可能带模型原文，异常跨层传播时也不得泄露。
            super(failureType + ": " + cause.getClass().getSimpleName());
            this.failureType = failureType;
            this.normalizedResponse = normalizedResponse;
            TokenStreamLocation location = cause instanceof JacksonException jackson
                    ? jackson.getLocation() : null;
            this.line = location == null || location == TokenStreamLocation.NA
                    ? null : location.getLineNr();
            this.column = location == null || location == TokenStreamLocation.NA
                    ? null : location.getColumnNr();
        }

        String failureType() { return failureType; }

        String normalizedResponse() { return normalizedResponse; }

        Integer line() { return line; }

        Integer column() { return column; }
    }

    static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("模型响应为空");
        String text = raw.strip();
        if (text.startsWith("\uFEFF")) text = text.substring(1).strip();
        if (text.isEmpty()) throw new IllegalArgumentException("模型响应为空");
        int fence = text.indexOf("```");
        // JSON 字符串中的反引号属于正文。
        if (fence >= 0 && (text.indexOf('{') < 0 || fence < text.indexOf('{'))
                && (text.indexOf('[') < 0 || fence < text.indexOf('['))) {
            requireExplanation(text.substring(0, fence));
            int lineEnd = text.indexOf('\n', fence);
            if (lineEnd < 0) throw new IllegalArgumentException("Markdown fence 不完整");
            String language = text.substring(fence + 3, lineEnd).strip();
            if (!language.isEmpty() && !language.equalsIgnoreCase("json")) {
                throw new IllegalArgumentException("Markdown fence 不是 JSON");
            }
            int closing = text.lastIndexOf("```");
            if (closing <= lineEnd || !text.substring(text.lastIndexOf('\n', closing) + 1, closing).isBlank()) {
                throw new IllegalArgumentException("Markdown fence 不完整");
            }
            requireExplanation(text.substring(closing + 3));
            text = text.substring(lineEnd + 1, closing).strip();
            if (text.startsWith("\uFEFF")) text = text.substring(1).strip();
            if (text.isEmpty()) throw new IllegalArgumentException("Markdown fence 内容为空");
            return text;
        }
        int start = 0;
        while (start < text.length() && text.charAt(start) != '{' && text.charAt(start) != '[') start++;
        // null 等 JSON 标量交给解析器及 Schema 校验处理。
        if (start == text.length()) return text;
        requireExplanation(text.substring(0, start));
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                if (--depth == 0) {
                    requireExplanation(text.substring(i + 1));
                    return text.substring(start, i + 1);
                }
            }
        }
        return text.substring(start);
    }

    private static void requireExplanation(String text) {
        if (text.isBlank()) return;
        if (!Character.isLetter(text.strip().codePointAt(0)) || text.matches("(?s).*[{}\\[\\]\"`].*")
                || text.strip().matches("(?s)(null|true|false|[-+0-9]).*")) {
            throw new IllegalArgumentException("JSON 外壳包含歧义内容");
        }
    }
}
