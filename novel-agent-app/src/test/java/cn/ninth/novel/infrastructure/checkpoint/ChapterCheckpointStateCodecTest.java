package cn.ninth.novel.infrastructure.checkpoint;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterCheckpointStateCodecTest {

    private final ChapterCheckpointStateCodec codec =
            new ChapterCheckpointStateCodec(new ObjectMapper());

    @Test
    void shouldRestoreTypedChapterStateValues() {
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder()
                        .projectCode("checkpoint-project")
                        .title("北境夜行")
                        .build())
                .build();
        ReviewReportVO reviewReport = ReviewReportVO.builder().build();
        ChapterMemoryVO chapterMemory = ChapterMemoryVO.builder().build();
        StoryStateSnapshot storyStateSnapshot = new StoryStateSnapshot(
                List.of("残图"), List.of("左臂受伤"), List.of("知道钟楼入口"), List.of("钟楼内"));

        String encoded = codec.encode(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "checkpoint-project",
                ChapterGraphKeys.CHAPTER_NUMBER, 3,
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.REVIEW_REPORT, reviewReport,
                ChapterGraphKeys.MEMORY, chapterMemory,
                ChapterGraphKeys.STORY_STATE_SNAPSHOT, storyStateSnapshot,
                ChapterGraphKeys.COMPLETED_STAGES, List.of("LOAD_CONTEXT", "DRAFT")
        ));
        assertThat(encoded).contains("\"memory\"").doesNotContain("\"compression\"");
        Map<String, Object> restored = codec.decode(encoded);
        System.out.println("checkpoint schema v2 restored typed MEMORY key");

        assertThat(restored.get(ChapterGraphKeys.CONTEXT))
                .isInstanceOf(ChapterContextAggregate.class);
        assertThat(((ChapterContextAggregate) restored.get(ChapterGraphKeys.CONTEXT))
                .getProject())
                .isInstanceOf(NovelProjectEntity.class)
                .extracting(NovelProjectEntity::getTitle)
                .isEqualTo("北境夜行");
        assertThat(restored.get(ChapterGraphKeys.REVIEW_REPORT))
                .isInstanceOf(ReviewReportVO.class);
        assertThat(restored.get(ChapterGraphKeys.MEMORY))
                .isInstanceOf(ChapterMemoryVO.class);
        assertThat(restored.get(ChapterGraphKeys.STORY_STATE_SNAPSHOT))
                .isEqualTo(storyStateSnapshot);
        assertThat(restored.get(ChapterGraphKeys.COMPLETED_STAGES))
                .isEqualTo(List.of("LOAD_CONTEXT", "DRAFT"));
    }

    @Test
    void shouldRejectCheckpointWithoutStateObject() {
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":2}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
    }

    @Test
    void shouldRejectCheckpointWithOldSchemaVersion() {
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":1,\"state\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }
}
