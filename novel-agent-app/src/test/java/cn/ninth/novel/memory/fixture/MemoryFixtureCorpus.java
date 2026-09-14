package cn.ninth.novel.memory.fixture;

import java.util.List;
import java.util.Objects;

/**
 * Common immutable corpus view shared by continuity and scalability fixtures.
 */
public record MemoryFixtureCorpus(List<FixtureChapter> chapters) {

    public MemoryFixtureCorpus {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("fixture corpus must not be empty");
        }
    }

    public FixtureChapter chapter(int chapterNumber) {
        return chapters.stream()
                .filter(chapter -> chapter.chapterNumber() == chapterNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "fixture chapter not found: " + chapterNumber));
    }

    public List<FixtureMemoryItem> memoryItems() {
        return chapters.stream()
                .flatMap(chapter -> chapter.memoryItems().stream())
                .toList();
    }

    public List<FixtureCandidate> candidates() {
        return chapters.stream()
                .flatMap(chapter -> chapter.candidates().stream())
                .toList();
    }

    public FixtureMemoryItem memoryItem(String id) {
        return memoryItems().stream()
                .filter(item -> Objects.equals(item.id(), id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fixture memory item not found: " + id));
    }

    public FixtureCandidate candidate(String id) {
        return candidates().stream()
                .filter(candidate -> Objects.equals(candidate.id(), id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fixture candidate not found: " + id));
    }

    public List<FixtureMemoryItem> itemsOfKind(FixtureMemoryKind kind) {
        return memoryItems().stream()
                .filter(item -> item.kind() == kind)
                .toList();
    }
}
