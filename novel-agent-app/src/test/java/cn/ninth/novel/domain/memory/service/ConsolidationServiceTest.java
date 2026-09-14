package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryConsolidationResult;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryOpenLoop;
import cn.ninth.novel.domain.memory.model.MemoryOpenLoopStatus;
import cn.ninth.novel.domain.memory.model.MemoryProjection;
import cn.ninth.novel.domain.memory.model.MemoryProjectionStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsolidationServiceTest {

    private final ConsolidationService consolidation = new ConsolidationService();

    @Test
    void repeatedConfirmationReinforcesOneFactAndDeduplicatesEvidence() {
        MemoryFact fact = new MemoryFact(
                "fact-origin", "残晶与封印同源", List.of("evidence-a"));

        MemoryFact reinforced = consolidation.reinforceSameFact(
                consolidation.reinforceSameFact(fact, List.of("evidence-b")),
                List.of("evidence-c", "evidence-b"));
        System.out.printf("REINFORCE factId=%s，sources=%s，status=%s%n",
                reinforced.factId(), reinforced.sourceIds(), reinforced.status());

        assertThat(reinforced.factId()).isEqualTo(fact.factId());
        assertThat(reinforced.proposition()).isEqualTo(fact.proposition());
        assertThat(reinforced.sourceIds())
                .containsExactly("evidence-a", "evidence-b", "evidence-c");
        assertThat(reinforced.status()).isEqualTo(MemoryFactStatus.FACT_ACTIVE);
    }

    @Test
    void resolvedOpenLoopProducesOneTraceableSummary() {
        MemoryOpenLoop loop = new MemoryOpenLoop(
                "loop-caravan", "失踪商队的去向", List.of("event-20"));

        MemoryConsolidationResult summary = consolidation.summarizeResolvedThread(
                loop,
                MemoryOpenLoopStatus.PROGRESSED,
                "失踪商队已找到",
                "沈夜获得北港线索",
                List.of("event-20", "fact-caravan-found"));
        System.out.printf("SUMMARIZE loop=%s，result=%s，impact=%s，sources=%s%n",
                loop.openLoopId(), summary.result(), summary.directImpact(), summary.sourceEvidence());

        assertThat(summary.result()).isEqualTo("失踪商队已找到");
        assertThat(summary.directImpact()).isEqualTo("沈夜获得北港线索");
        assertThat(summary.sourceEvidence())
                .containsExactly("event-20", "fact-caravan-found");
        assertThatThrownBy(() -> consolidation.summarizeResolvedThread(
                loop, MemoryOpenLoopStatus.RESOLVED, "结果", "影响", List.of("event-20")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletedProjectionCanBeRebuiltFromSummarySources() {
        MemoryConsolidationResult summary = new MemoryConsolidationResult(
                "失踪商队已找到",
                "沈夜获得北港线索",
                List.of("event-20", "fact-caravan-found"));
        MemoryProjection original = consolidation.createProjection("projection-caravan", summary);
        MemoryProjection deletedSnapshot = original.archive();
        MemoryProjection rebuilt = consolidation.rebuildProjection(deletedSnapshot, summary);
        System.out.printf("Projection rebuild id=%s，status=%s，sources=%s%n",
                rebuilt.projectionId(), rebuilt.status(), rebuilt.sourceIds());

        assertThat(rebuilt.projectionId()).isEqualTo(original.projectionId());
        assertThat(rebuilt.content()).isEqualTo(original.content());
        assertThat(rebuilt.sourceIds()).containsExactlyElementsOf(summary.sourceEvidence());
        assertThat(rebuilt.status()).isEqualTo(MemoryProjectionStatus.PROJECTION_ACTIVE);
    }

    @Test
    void consolidationHasNoGeneralizeOrMultiFactInferenceEntryPoint() {
        List<String> methods = java.util.Arrays.stream(ConsolidationService.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        MemoryConsolidationResult summary = new MemoryConsolidationResult(
                "A 已确认，B 已确认，C 已确认",
                "只记录直接影响",
                List.of("evidence-a", "evidence-b", "evidence-c"));
        MemoryProjection projection = consolidation.createProjection("projection-confirmed", summary);
        System.out.printf("P0 Consolidation methods=%s，projectionContent=%s%n",
                methods, projection.content());

        assertThat(methods).noneMatch(name -> name.toLowerCase().contains("general"));
        assertThat(projection.content()).doesNotContain("系统性技术");
        assertThat(projection.content()).contains("A 已确认，B 已确认，C 已确认");
    }
}
