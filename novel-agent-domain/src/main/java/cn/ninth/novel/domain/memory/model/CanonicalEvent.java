package cn.ninth.novel.domain.memory.model;

import java.util.Objects;

/**
 * P0.5 正式事件。事件记录历史发生，不携带 Fact 的生命周期状态。
 */
public record CanonicalEvent(
        String eventId,
        String projectCode,
        int chapterNumber,
        String description,
        MemorySourceVersion sourceVersion,
        MemoryEvidenceRef evidenceRange,
        String storyTime,
        String candidateId
) {

    public CanonicalEvent {
        eventId = required(eventId, "eventId");
        projectCode = required(projectCode, "projectCode");
        if (chapterNumber < 0) {
            throw new IllegalArgumentException("chapterNumber 不能小于 0");
        }
        description = required(description, "description");
        sourceVersion = Objects.requireNonNull(sourceVersion, "sourceVersion 不能为空");
        evidenceRange = Objects.requireNonNull(evidenceRange, "evidenceRange 不能为空");
        storyTime = optional(storyTime);
        candidateId = required(candidateId, "candidateId");
    }

    public CanonicalEvent(
            String eventId,
            String projectCode,
            int chapterNumber,
            String description,
            MemorySourceVersion sourceVersion,
            MemoryEvidenceRef evidenceRange
    ) {
        this(eventId, projectCode, chapterNumber, description,
                sourceVersion, evidenceRange, null, eventId);
    }

    public String chapterVersion() {
        return sourceVersion.chapterVersion();
    }

    public String contentHash() {
        return sourceVersion.contentHash();
    }

    public String evidenceBinding() {
        return sourceVersion.chapterVersion()
                + "#" + sourceVersion.contentHash()
                + "#" + evidenceRange.startOffset()
                + ":" + evidenceRange.endOffset();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
