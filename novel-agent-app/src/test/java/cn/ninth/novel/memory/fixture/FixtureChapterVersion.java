package cn.ninth.novel.memory.fixture;

import java.util.List;

/**
 * One immutable正文 version. A chapter can contain multiple versions for revise regression cases.
 */
public record FixtureChapterVersion(
        String versionId,
        String storyTime,
        String content,
        String contentHash,
        boolean accepted,
        List<FixtureMemoryItem> memoryItems,
        List<FixtureCandidate> candidates
) {

    public FixtureChapterVersion {
        versionId = requireText(versionId, "versionId");
        storyTime = requireText(storyTime, "storyTime");
        content = requireText(content, "content");
        contentHash = requireText(contentHash, "contentHash");
        memoryItems = immutable(memoryItems);
        candidates = immutable(candidates);
        for (FixtureCandidate candidate : candidates) {
            if (!versionId.equals(candidate.chapterVersion())) {
                throw new IllegalArgumentException(
                        "candidate " + candidate.id() + " is bound to another chapter version");
            }
            if (!contentHash.equals(candidate.contentHash())) {
                throw new IllegalArgumentException(
                        "candidate " + candidate.id() + " has a mismatched content hash");
            }
        }
        for (FixtureMemoryItem item : memoryItems) {
            if (!versionId.equals(item.sourceVersion())) {
                throw new IllegalArgumentException(
                        "memory item " + item.id() + " is bound to another chapter version");
            }
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
