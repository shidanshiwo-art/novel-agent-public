package cn.ninth.novel.domain.chapter.model.valobj;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterHistoryVOTest {

    @Test
    void missingStoryStateSnapshotShouldBeReadAsEmptySnapshot() {
        ChapterHistoryVO history = ChapterHistoryVO.builder().build();

        assertThat(history.getStoryStateSnapshot()).isEqualTo(StoryStateSnapshot.empty());
        assertThat(history.getStoryStateSnapshot().resources()).isEmpty();
        assertThat(history.getStoryStateSnapshot().abilities()).isEmpty();
        assertThat(history.getStoryStateSnapshot().knowledge()).isEmpty();
        assertThat(history.getStoryStateSnapshot().presence()).isEmpty();
        System.out.println("旧历史缺失 StoryStateSnapshot：读取为四个空列表");
    }
}
