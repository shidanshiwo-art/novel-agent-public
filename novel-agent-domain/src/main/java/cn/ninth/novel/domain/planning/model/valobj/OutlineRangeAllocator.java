package cn.ninth.novel.domain.planning.model.valobj;

import java.util.ArrayList;
import java.util.List;

public final class OutlineRangeAllocator {

    public List<ChapterRange> allocate(
            int parentStartChapter,
            int parentEndChapter,
            int childCount
    ) {
        if (parentStartChapter <= 0 || parentEndChapter < parentStartChapter) {
            throw new IllegalArgumentException("父节点章节范围非法");
        }
        if (childCount <= 0) {
            throw new IllegalArgumentException("childCount 必须大于 0");
        }

        int parentChapterCount = parentEndChapter - parentStartChapter + 1;
        if (childCount > parentChapterCount) {
            throw new IllegalArgumentException("childCount 不能超过父节点章节数");
        }

        List<ChapterRange> ranges = new ArrayList<>(childCount);
        int chapterCursor = parentStartChapter;
        int remainingChapters = parentChapterCount;
        int remainingChildren = childCount;
        for (int index = 0; index < childCount; index++) {
            int chapterCount = remainingChapters / remainingChildren
                    + (remainingChapters % remainingChildren == 0 ? 0 : 1);
            int endChapter = chapterCursor + chapterCount - 1;
            ranges.add(new ChapterRange(chapterCursor, endChapter));
            chapterCursor = endChapter + 1;
            remainingChapters -= chapterCount;
            remainingChildren--;
        }
        return List.copyOf(ranges);
    }

    public record ChapterRange(int startChapter, int endChapter) {
    }
}
