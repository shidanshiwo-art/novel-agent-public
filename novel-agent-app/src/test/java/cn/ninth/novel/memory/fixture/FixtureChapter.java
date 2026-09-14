package cn.ninth.novel.memory.fixture;

import java.util.List;

/**
 * One chapter and all source versions attached to it.
 */
public record FixtureChapter(
        int chapterNumber,
        String title,
        List<FixtureChapterVersion> versions
) {

    public FixtureChapter {
        if (chapterNumber < 1) {
            throw new IllegalArgumentException("chapterNumber must be positive");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        versions = versions == null ? List.of() : List.copyOf(versions);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("a fixture chapter must have at least one version");
        }
    }

    public FixtureChapterVersion acceptedVersion() {
        return versions.stream()
                .filter(FixtureChapterVersion::accepted)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "fixture chapter " + chapterNumber + " has no accepted version"));
    }

    public List<FixtureCandidate> candidates() {
        return versions.stream()
                .flatMap(version -> version.candidates().stream())
                .toList();
    }

    public List<FixtureMemoryItem> memoryItems() {
        return versions.stream()
                .flatMap(version -> version.memoryItems().stream())
                .toList();
    }
}
