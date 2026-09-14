package cn.ninth.novel.memory.fixture;

import java.util.Objects;

/**
 * A small immutable memory item used only as fixture input for future retrieval/lifecycle tests.
 */
public record FixtureMemoryItem(
        String id,
        FixtureMemoryKind kind,
        FixtureLifecycle lifecycle,
        int sourceChapter,
        String sourceVersion,
        String storyTime,
        String subject,
        String content,
        boolean longTerm
) {

    public FixtureMemoryItem {
        id = requireText(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        if (sourceChapter < 1) {
            throw new IllegalArgumentException("sourceChapter must be positive");
        }
        sourceVersion = requireText(sourceVersion, "sourceVersion");
        storyTime = requireText(storyTime, "storyTime");
        subject = requireText(subject, "subject");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
