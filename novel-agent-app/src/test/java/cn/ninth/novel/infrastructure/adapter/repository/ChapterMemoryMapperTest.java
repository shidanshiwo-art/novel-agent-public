package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterMemoryMapperTest {

    @Test
    void shouldMapStoryStateSnapshotJsonToFourCurrentStateLists() {
        StorySummaryPO summary = new StorySummaryPO();
        summary.setStoryStateSnapshotJson("""
                {
                  "resources": ["残图", "灵石"],
                  "abilities": ["灵力受伤", "只能施展一次秘术"],
                  "knowledge": ["知道城门有暗号", "不知道幕后人身份"],
                  "presence": ["人在北城门", "同行者已离场"]
                }
                """);

        StoryStateSnapshot snapshot = ChapterMemoryMapper.toStoryStateSnapshot(summary);

        assertThat(snapshot.resources()).containsExactly("残图", "灵石");
        assertThat(snapshot.abilities()).containsExactly("灵力受伤", "只能施展一次秘术");
        assertThat(snapshot.knowledge()).containsExactly("知道城门有暗号", "不知道幕后人身份");
        assertThat(snapshot.presence()).containsExactly("人在北城门", "同行者已离场");
        System.out.printf("StoryStateSnapshot mapped resources=%s abilities=%s knowledge=%s presence=%s%n",
                snapshot.resources(), snapshot.abilities(), snapshot.knowledge(), snapshot.presence());
    }

    @Test
    void shouldUseEmptySnapshotForMissingOrMalformedJson() {
        StorySummaryPO summary = new StorySummaryPO();
        summary.setStoryStateSnapshotJson("not-json");

        StoryStateSnapshot snapshot = ChapterMemoryMapper.toStoryStateSnapshot(summary);

        assertThat(snapshot).isEqualTo(StoryStateSnapshot.empty());
        assertThat(ChapterMemoryMapper.toStoryStateSnapshot(null)).isEqualTo(StoryStateSnapshot.empty());
        System.out.println("StoryStateSnapshot malformedOrMissingJson fallback=empty");
    }
}
