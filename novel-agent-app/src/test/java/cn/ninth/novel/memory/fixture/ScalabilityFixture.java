package cn.ninth.novel.memory.fixture;

import java.util.List;

/**
 * One deterministic scalability corpus at a requested chapter count.
 */
public record ScalabilityFixture(int chapterCount, MemoryFixtureCorpus corpus) {

    public ScalabilityFixture {
        if (chapterCount < 1) {
            throw new IllegalArgumentException("chapterCount must be positive");
        }
        if (corpus == null) {
            throw new IllegalArgumentException("corpus must not be null");
        }
        if (corpus.chapters().size() != chapterCount) {
            throw new IllegalArgumentException("chapter count does not match corpus size");
        }
    }

    public List<FixtureChapter> chapters() {
        return corpus.chapters();
    }

    public FixtureChapter chapter(int chapterNumber) {
        return corpus.chapter(chapterNumber);
    }

    public List<FixtureMemoryItem> memoryItems() {
        return corpus.memoryItems();
    }

    public List<FixtureCandidate> candidates() {
        return corpus.candidates();
    }

    public List<FixtureMemoryItem> itemsOfKind(FixtureMemoryKind kind) {
        return corpus.itemsOfKind(kind);
    }
}
