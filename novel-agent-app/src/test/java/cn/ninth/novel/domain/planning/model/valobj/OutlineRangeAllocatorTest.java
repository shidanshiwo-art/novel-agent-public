package cn.ninth.novel.domain.planning.model.valobj;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class OutlineRangeAllocatorTest {

    private final OutlineRangeAllocator allocator = new OutlineRangeAllocator();

    @Test
    void shouldAllocateExampleRangesWithCompleteCoverage() {
        List<OutlineRangeAllocator.ChapterRange> ranges = allocator.allocate(1, 10, 3);

        System.out.printf("allocated ranges: %s%n", ranges);
        assertThat(ranges).extracting(
                OutlineRangeAllocator.ChapterRange::startChapter,
                OutlineRangeAllocator.ChapterRange::endChapter
        ).containsExactly(
                tuple(1, 4), tuple(5, 7), tuple(8, 10)
        );
    }

    @Test
    void shouldKeepRangesContinuousAndCoverAnyValidParentRange() {
        List<OutlineRangeAllocator.ChapterRange> ranges = allocator.allocate(7, 15, 4);

        System.out.printf("invariant ranges: %s%n", ranges);
        assertThat(ranges).hasSize(4);
        assertThat(ranges.get(0).startChapter()).isEqualTo(7);
        assertThat(ranges.get(ranges.size() - 1).endChapter()).isEqualTo(15);
        for (int index = 0; index < ranges.size(); index++) {
            OutlineRangeAllocator.ChapterRange range = ranges.get(index);
            assertThat(range.startChapter()).isLessThanOrEqualTo(range.endChapter());
            if (index > 0) {
                assertThat(range.startChapter())
                        .isEqualTo(ranges.get(index - 1).endChapter() + 1);
            }
        }
    }

    @Test
    void shouldAllocateTheLargestSupportedChapterRangeWithoutOverflow() {
        List<OutlineRangeAllocator.ChapterRange> ranges = allocator.allocate(
                1, Integer.MAX_VALUE, 2
        );

        System.out.printf("maximum range allocation: %s%n", ranges);
        assertThat(ranges).extracting(
                OutlineRangeAllocator.ChapterRange::startChapter,
                OutlineRangeAllocator.ChapterRange::endChapter
        ).containsExactly(
                tuple(1, 1_073_741_824), tuple(1_073_741_825, Integer.MAX_VALUE)
        );
    }

    @Test
    void shouldRejectImpossibleOrInvalidRanges() {
        assertThatThrownBy(() -> allocator.allocate(0, 10, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allocator.allocate(10, 1, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allocator.allocate(1, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allocator.allocate(1, 2, 3))
                .isInstanceOf(IllegalArgumentException.class);

        System.out.println("invalid range inputs rejected");
    }
}
