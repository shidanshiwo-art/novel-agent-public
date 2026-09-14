package cn.ninth.novel.memory.fixture;

import java.util.Objects;

/**
 * A source-bound provisional candidate used to exercise candidate/revision boundaries.
 */
public record FixtureCandidate(
        String id,
        int chapterNumber,
        String chapterVersion,
        String contentHash,
        String evidenceRange,
        FixtureCandidateType candidateType,
        FixtureCandidateStatus candidateStatus,
        String statement,
        String evidenceText,
        String supersedesCandidateId
) {

    public FixtureCandidate {
        id = requireText(id, "id");
        if (chapterNumber < 1) {
            throw new IllegalArgumentException("chapterNumber must be positive");
        }
        chapterVersion = requireText(chapterVersion, "chapterVersion");
        contentHash = requireText(contentHash, "contentHash");
        evidenceRange = requireText(evidenceRange, "evidenceRange");
        candidateType = Objects.requireNonNull(candidateType, "candidateType");
        candidateStatus = Objects.requireNonNull(candidateStatus, "candidateStatus");
        statement = requireText(statement, "statement");
        evidenceText = requireText(evidenceText, "evidenceText");
        if (supersedesCandidateId != null && supersedesCandidateId.isBlank()) {
            throw new IllegalArgumentException("supersedesCandidateId must be null or non-blank");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
