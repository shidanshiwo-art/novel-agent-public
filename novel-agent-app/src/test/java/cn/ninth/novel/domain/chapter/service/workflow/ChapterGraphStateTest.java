package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterGraphStateTest {

    @Test
    void shouldInitializeControlFieldsFromSchemaDefaults() {
        ChapterGraphState state = ChapterGraphState.FACTORY.applyFromSchema(ChapterGraphState.SCHEMA);

        assertThat(ChapterGraphState.SCHEMA).doesNotContainKey("plan");
        assertThat(ChapterGraphState.SCHEMA)
                .containsKey(ChapterGraphKeys.MEMORY)
                .doesNotContainKey("compression");
        assertThat(state.reviseRound()).isZero();
        assertThat(state.retryCount()).isZero();
        assertThat(state.completedStages()).isEmpty();
    }

    @Test
    void shouldExposeTypedWorkflowValues() {
        ChapterMemoryVO chapterMemory = new ChapterMemoryVO();
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "novel-001",
                ChapterGraphKeys.CHAPTER_NUMBER, 3,
                ChapterGraphKeys.DRAFT, "chapter draft",
                ChapterGraphKeys.MEMORY, chapterMemory,
                ChapterGraphKeys.CURRENT_NODE, "COMPRESSION"
        ));

        assertThat(state.projectCode()).contains("novel-001");
        assertThat(state.chapterNumber()).contains(3);
        assertThat(state.draft()).contains("chapter draft");
        assertThat(state.chapterMemory()).contains(chapterMemory);
        assertThat(state.currentNode()).contains("COMPRESSION");
    }

    @Test
    void shouldAppendCompletedStagesWithoutAccumulatingDuplicates() {
        Map<String, Object> initial = ChapterGraphState.FACTORY
                .initialDataFromSchema(ChapterGraphState.SCHEMA);
        Map<String, Object> afterLoad = AgentState.updateState(
                initial,
                Map.of(ChapterGraphKeys.COMPLETED_STAGES, List.of("LOAD")),
                ChapterGraphState.SCHEMA
        );
        Map<String, Object> afterRetry = AgentState.updateState(
                afterLoad,
                Map.of(ChapterGraphKeys.COMPLETED_STAGES, List.of("LOAD", "DRAFT")),
                ChapterGraphState.SCHEMA
        );

        assertThat(new ChapterGraphState(afterRetry).completedStages())
                .containsExactly("LOAD", "DRAFT");
    }

    @Test
    void shouldReplaceCompressionSnapshotOnRetry() {
        ChapterMemoryVO first = new ChapterMemoryVO();
        ChapterMemoryVO retried = new ChapterMemoryVO();
        Map<String, Object> state = Map.of(ChapterGraphKeys.MEMORY, first);

        Map<String, Object> updated = AgentState.updateState(
                state,
                Map.of(ChapterGraphKeys.MEMORY, retried),
                ChapterGraphState.SCHEMA
        );

        assertThat(new ChapterGraphState(updated).chapterMemory()).contains(retried);
    }
}
