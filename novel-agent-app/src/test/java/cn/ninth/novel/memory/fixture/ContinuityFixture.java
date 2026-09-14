package cn.ninth.novel.memory.fixture;

import java.util.List;

/**
 * Chapters 5-10 continuity regression corpus.
 */
public record ContinuityFixture(MemoryFixtureCorpus corpus) {

    public ContinuityFixture {
        if (corpus == null) {
            throw new IllegalArgumentException("corpus must not be null");
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

    public FixtureMemoryItem memoryItem(String id) {
        return corpus.memoryItem(id);
    }

    public FixtureCandidate candidate(String id) {
        return corpus.candidate(id);
    }
}
