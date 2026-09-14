package cn.ninth.novel.domain.memory.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryModelInvariantTest {

    @Test
    void eventIsImmutableHistoricalRecordAndDoesNotUseFactStatus() {
        MemoryEvent event = new MemoryEvent(
                "event-1",
                "沈夜将残晶放入消防栓箱",
                "chapter-9-v2",
                "paragraph-3",
                "第九章夜间"
        );

        List<String> methods = Arrays.stream(MemoryEvent.class.getDeclaredMethods())
                .map(Method::getName)
                .sorted()
                .toList();
        boolean usesFactStatus = Arrays.stream(MemoryEvent.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == MemoryFactStatus.class);
        System.out.printf("Event id=%s, storyTime=%s, mutableMethods=%s%n",
                event.eventId(), event.storyTime(),
                methods.stream().filter(method -> method.startsWith("set")).toList());

        assertThat(event.description()).isEqualTo("沈夜将残晶放入消防栓箱");
        assertThat(usesFactStatus).isFalse();
        assertThat(methods).noneMatch(method -> method.startsWith("set"));
        assertThatThrownBy(() -> new MemoryEvent("event-2", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factOwnsCurrentOrHistoricalStatusAndPreservesSources() {
        MemoryFact fact = new MemoryFact(
                "fact-1",
                "残晶 located_at 消防栓箱",
                List.of("event-1", "evidence-9-3")
        );

        MemoryFact archived = fact.supersede();
        MemoryFact invalidated = fact.invalidate();
        System.out.printf("Fact id=%s, sources=%s, statuses=%s -> %s / %s%n",
                fact.factId(), fact.sourceIds(), fact.status(),
                archived.status(), invalidated.status());

        assertThat(fact.status()).isEqualTo(MemoryFactStatus.FACT_ACTIVE);
        assertThat(archived.status()).isEqualTo(MemoryFactStatus.FACT_ARCHIVED);
        assertThat(invalidated.status()).isEqualTo(MemoryFactStatus.FACT_INVALIDATED);
        assertThat(archived.sourceIds()).containsExactly("event-1", "evidence-9-3");
        assertThatThrownBy(() -> new MemoryFact("fact-2", "命题", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fact.supersede().sourceIds().add("event-2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void projectionHasIndependentStatusAndReconstructableSources() {
        MemoryProjection projection = new MemoryProjection(
                "projection-1",
                "调查结果：残晶被藏入消防栓箱，沈夜暂未取回。",
                List.of("event-1", "fact-1")
        );

        MemoryProjection stale = projection.markStale();
        MemoryProjection archived = projection.archive();
        MemoryProjection rebuilt = stale.reactivate();
        System.out.printf("Projection id=%s, sources=%s, statuses=%s -> %s / %s / %s%n",
                projection.projectionId(), projection.sourceIds(), projection.status(),
                stale.status(), archived.status(), rebuilt.status());

        assertThat(projection.status()).isEqualTo(MemoryProjectionStatus.PROJECTION_ACTIVE);
        assertThat(stale.status()).isEqualTo(MemoryProjectionStatus.PROJECTION_STALE);
        assertThat(archived.status()).isEqualTo(MemoryProjectionStatus.PROJECTION_ARCHIVED);
        assertThat(rebuilt.status()).isEqualTo(MemoryProjectionStatus.PROJECTION_ACTIVE);
        assertThat(projection.sourceIds()).containsExactly("event-1", "fact-1");
        assertThatThrownBy(() -> new MemoryProjection("projection-2", "摘要", List.of("fact-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openLoopIsAnUnresolvedThreadWithoutFactOrProjectionLifecycle() {
        MemoryOpenLoop openLoop = new MemoryOpenLoop(
                "loop-1",
                "黑色残晶的来源尚未揭示",
                List.of("event-1")
        );
        List<String> fieldNames = Arrays.stream(MemoryOpenLoop.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList();
        System.out.printf("OpenLoop id=%s, description=%s, fields=%s%n",
                openLoop.openLoopId(), openLoop.description(), fieldNames);

        assertThat(openLoop.description()).contains("尚未揭示");
        assertThat(fieldNames).noneMatch(name -> name.equals("status"));
        assertThat(openLoop.sourceIds()).containsExactly("event-1");
    }

    @Test
    void p0OperationSetExcludesUpdateMergeAndGeneralize() {
        List<String> operations = Arrays.stream(MemoryOperation.values())
                .map(Enum::name)
                .toList();
        System.out.printf("P0 memory operations=%s%n", operations);

        assertThat(operations).containsExactly(
                "ADD", "REINFORCE", "SUPERSEDE", "INVALIDATE", "NOOP");
        assertThat(operations).doesNotContain("UPDATE", "MERGE", "GENERALIZE");
    }
}
