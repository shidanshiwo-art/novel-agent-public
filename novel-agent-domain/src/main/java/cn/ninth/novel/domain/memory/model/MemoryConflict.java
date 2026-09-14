package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/** Gate 的确定性或语义校验结果。 */
public record MemoryConflict(
        String code,
        String detail,
        MemoryConflictSeverity severity
) {

    public MemoryConflict {
        code = required(code, "code");
        detail = required(detail, "detail");
        severity = Objects.requireNonNull(severity, "severity 不能为空");
    }

    public static MemoryConflict blocking(String code, String detail) {
        return new MemoryConflict(code, detail, MemoryConflictSeverity.BLOCKING);
    }

    public static MemoryConflict nonBlocking(String code, String detail) {
        return new MemoryConflict(code, detail, MemoryConflictSeverity.NON_BLOCKING);
    }

    public boolean isBlocking() {
        return severity == MemoryConflictSeverity.BLOCKING;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
