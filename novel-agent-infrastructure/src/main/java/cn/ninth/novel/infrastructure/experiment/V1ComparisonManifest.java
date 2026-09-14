package cn.ninth.novel.infrastructure.experiment;

import java.time.Instant;
import java.util.Objects;

/**
 * V1-current 与 V1-improved 对照实验冻结清单。
 *
 * <p>两组都使用 MemoryMode.V1；清单不再把 Legacy 作为实验变量。</p>
 */
public record V1ComparisonManifest(
        String experimentId,
        String baselineProjectCode,
        String currentProjectCode,
        String improvedProjectCode,
        int baselineFromChapter,
        int baselineToChapter,
        int experimentFromChapter,
        int experimentToChapter,
        String baselineFingerprint,
        String frozenOutlineFingerprint,
        String configurationFingerprint,
        String waitingHumanPolicy,
        int automaticRevisionLimit,
        int humanRevisionLimit,
        Instant frozenAt
) {

    public V1ComparisonManifest {
        experimentId = required(experimentId, "experimentId");
        baselineProjectCode = required(baselineProjectCode, "baselineProjectCode");
        currentProjectCode = required(currentProjectCode, "currentProjectCode");
        improvedProjectCode = required(improvedProjectCode, "improvedProjectCode");
        baselineFingerprint = required(baselineFingerprint, "baselineFingerprint");
        frozenOutlineFingerprint = required(
                frozenOutlineFingerprint, "frozenOutlineFingerprint");
        configurationFingerprint = required(
                configurationFingerprint, "configurationFingerprint");
        waitingHumanPolicy = required(waitingHumanPolicy, "waitingHumanPolicy");
        validateRange(baselineFromChapter, baselineToChapter, "baseline");
        validateRange(experimentFromChapter, experimentToChapter, "experiment");
        if (automaticRevisionLimit < 0 || humanRevisionLimit < 0) {
            throw new IllegalArgumentException("revision limit 不能为负数");
        }
        frozenAt = frozenAt == null ? Instant.now() : frozenAt;
        if (currentProjectCode.length() > 64 || improvedProjectCode.length() > 64) {
            throw new IllegalArgumentException("实验项目编码长度不能超过 64 个字符");
        }
        if (currentProjectCode.equals(improvedProjectCode)
                || currentProjectCode.equals(baselineProjectCode)
                || improvedProjectCode.equals(baselineProjectCode)) {
            throw new IllegalArgumentException("V1 对照项目编码必须彼此隔离");
        }
    }

    private static void validateRange(int from, int to, String name) {
        if (from < 1 || from > to) {
            throw new IllegalArgumentException(name + " 章节范围无效");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
