package cn.ninth.novel.domain.memory.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 与具体章节正文版本绑定的临时记忆候选。
 *
 * <p>Candidate 只允许作为 checkpoint/staging 数据存在；本类型不提供
 * Canonical 提交或普通 PLAN/DRAFT Recall 能力。</p>
 */
public record MemoryCandidate(
        String candidateId,
        MemorySourceVersion sourceVersion,
        MemoryEvidenceRef evidenceRange,
        MemoryCandidateType candidateType,
        MemoryCandidateStatus candidateStatus
) {

    public MemoryCandidate {
        requireText(candidateId, "candidateId");
        requireNonNull(sourceVersion, "sourceVersion");
        requireNonNull(evidenceRange, "evidenceRange");
        requireNonNull(candidateType, "candidateType");
        requireNonNull(candidateStatus, "candidateStatus");
    }

    public MemoryCandidate(
            MemorySourceVersion sourceVersion,
            MemoryEvidenceRef evidenceRange,
            MemoryCandidateType candidateType,
            MemoryCandidateStatus candidateStatus
    ) {
        this(UUID.randomUUID().toString(), sourceVersion, evidenceRange, candidateType, candidateStatus);
    }

    public MemoryCandidate(
            String candidateId,
            String chapterVersion,
            String contentHash,
            MemoryEvidenceRef evidenceRange,
            MemoryCandidateType candidateType,
            MemoryCandidateStatus candidateStatus
    ) {
        this(
                candidateId,
                new MemorySourceVersion(chapterVersion, contentHash),
                evidenceRange,
                candidateType,
                candidateStatus
        );
    }

    /** 创建已完成版本和证据边界校验的 provisional Candidate。 */
    public static MemoryCandidate provisional(
            MemorySourceVersion sourceVersion,
            String content,
            MemoryEvidenceRef evidenceRange,
            MemoryCandidateType candidateType
    ) {
        requireNonNull(sourceVersion, "sourceVersion");
        sourceVersion.requireMatches(content);
        requireNonNull(evidenceRange, "evidenceRange");
        evidenceRange.requireWithin(content);
        requireNonNull(candidateType, "candidateType");
        return new MemoryCandidate(
                UUID.randomUUID().toString(),
                sourceVersion,
                evidenceRange,
                candidateType,
                MemoryCandidateStatus.PROVISIONAL
        );
    }

    public static MemoryCandidate provisional(
            MemorySourceVersion sourceVersion,
            String content,
            int startOffset,
            int endOffset,
            MemoryCandidateType candidateType
    ) {
        return provisional(
                sourceVersion,
                content,
                MemoryEvidenceRef.of(content, startOffset, endOffset),
                candidateType
        );
    }

    /**
     * 用当前正文版本重新验证候选。
     * 版本或正文指纹变化时返回 STALE；同一版本但证据越界则拒绝继续传播。
     */
    public MemoryCandidate revalidate(
            MemorySourceVersion currentSourceVersion,
            String currentContent
    ) {
        Objects.requireNonNull(currentSourceVersion, "currentSourceVersion 不能为空");
        if (!sourceVersion.chapterVersion().equals(currentSourceVersion.chapterVersion())
                || !sourceVersion.contentHash().equals(currentSourceVersion.contentHash())
                || !currentSourceVersion.matchesContent(currentContent)) {
            return withStatus(MemoryCandidateStatus.STALE);
        }
        evidenceRange.requireWithin(currentContent);
        return this;
    }

    public MemoryCandidate markStale() {
        return withStatus(MemoryCandidateStatus.STALE);
    }

    public MemoryCandidate withStatus(MemoryCandidateStatus status) {
        return new MemoryCandidate(
                candidateId, sourceVersion, evidenceRange, candidateType, status);
    }

    public String chapterVersion() {
        return sourceVersion.chapterVersion();
    }

    public String contentHash() {
        return sourceVersion.contentHash();
    }

    public boolean isStaleFor(
            MemorySourceVersion currentSourceVersion,
            String currentContent
    ) {
        return revalidate(currentSourceVersion, currentContent).candidateStatus()
                == MemoryCandidateStatus.STALE;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public MemorySourceVersion getSourceVersion() {
        return sourceVersion;
    }

    public MemoryEvidenceRef getEvidenceRange() {
        return evidenceRange;
    }

    public MemoryCandidateType getCandidateType() {
        return candidateType;
    }

    public MemoryCandidateStatus getCandidateStatus() {
        return candidateStatus;
    }

    public String getChapterVersion() {
        return chapterVersion();
    }

    public String getContentHash() {
        return contentHash();
    }

    public static List<MemoryCandidate> revalidateAll(
            List<MemoryCandidate> candidates,
            MemorySourceVersion currentSourceVersion,
            String currentContent
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> candidate.revalidate(currentSourceVersion, currentContent))
                .toList();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
