package cn.ninth.novel.infrastructure.experiment;

import java.time.Instant;
import java.util.Objects;

/**
 * A/B 实验冻结清单。
 *
 * <p>清单只保存实验所需的身份和指纹，不保存正文、Prompt 或 Canonical
 * 内容，避免把实验数据再次变成另一份隐式数据源。</p>
 */
public record MemoryAbExperimentManifest(
        String experimentId,
        String baselineProjectCode,
        String legacyProjectCode,
        String v1ProjectCode,
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

    public MemoryAbExperimentManifest {
        experimentId = required(experimentId, "experimentId");
        baselineProjectCode = required(baselineProjectCode, "baselineProjectCode");
        legacyProjectCode = required(legacyProjectCode, "legacyProjectCode");
        v1ProjectCode = required(v1ProjectCode, "v1ProjectCode");
        baselineFingerprint = required(baselineFingerprint, "baselineFingerprint");
        frozenOutlineFingerprint = required(
                frozenOutlineFingerprint, "frozenOutlineFingerprint");
        configurationFingerprint = required(
                configurationFingerprint, "configurationFingerprint");
        waitingHumanPolicy = required(waitingHumanPolicy, "waitingHumanPolicy");
        if (baselineFromChapter < 1 || baselineFromChapter > baselineToChapter) {
            throw new IllegalArgumentException("baseline 章节范围无效");
        }
        if (experimentFromChapter < 1 || experimentFromChapter > experimentToChapter) {
            throw new IllegalArgumentException("experiment 章节范围无效");
        }
        if (automaticRevisionLimit < 0 || humanRevisionLimit < 0) {
            throw new IllegalArgumentException("revision limit 不能为负数");
        }
        frozenAt = frozenAt == null ? Instant.now() : frozenAt;
        if (baselineProjectCode.length() > 64
                || legacyProjectCode.length() > 64
                || v1ProjectCode.length() > 64) {
            throw new IllegalArgumentException("项目编码长度不能超过 64 个字符");
        }
        if (legacyProjectCode.equals(v1ProjectCode)
                || legacyProjectCode.equals(baselineProjectCode)
                || v1ProjectCode.equals(baselineProjectCode)) {
            throw new IllegalArgumentException("A/B 项目编码必须彼此隔离");
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
