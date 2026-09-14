package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuitySemanticResult;
import cn.ninth.novel.domain.chapter.model.valobj.QualityContext;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySemanticDecision;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Continuity Validator 两阶段检查测试。 */
class ContinuityValidationNodeTest {

    @Test
    void shouldGenerateCandidateAndMarkCanonicalLocationConflictHard() {
        ReviewContext context = reviewContext(
                "郝乐位于行政楼。",
                "宿舍门推开时，郝乐从宿舍床上弹起来。",
                List.of(character("郝乐")),
                MemoryContextItem.canonical(
                        "CANONICAL_STATE", "hao-le-location", MemoryContextCategory.CURRENT_STATES,
                        "郝乐位于行政楼。", 13, "chapter-13-final", "FACT_ACTIVE", "第13日", 1));
        ContinuityValidationNode node = new ContinuityValidationNode(
                new ConflictCandidateGenerator(),
                (candidate, ignored) -> {
                    assertThat(candidate.type()).isEqualTo("CHARACTER_STATE");
                    assertThat(candidate.entity()).isEqualTo("郝乐");
                    return ContinuitySemanticResult.conflict();
                });

        Map<String, Object> update = node.apply(state(context));
        List<ConflictCandidate> candidates = candidates(update);
        List<ContinuityFinding> findings = findings(update);

        assertThat(candidates).hasSize(1);
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.getType()).isEqualTo("CHARACTER_STATE");
            assertThat(finding.getEntity()).isEqualTo("郝乐");
            assertThat(finding.getSeverity()).isEqualTo(ContinuitySeverity.HARD);
            assertThat(finding.getCurrentEvidence()).contains("宿舍", "郝乐");
            assertThat(finding.getHistoricalEvidence()).contains("行政楼");
            assertThat(finding.getSourceChapter()).isEqualTo(13);
        });
        System.out.printf(
                "Ch14 位置冲突：candidate=%s，decision=CONFLICT，severity=%s%n",
                candidates.get(0).entity(), findings.get(0).getSeverity());
    }

    @Test
    void shouldNotMarkNormalMovementHard() {
        ReviewContext context = reviewContext(
                "郝乐位于宿舍。",
                "第二天，郝乐从宿舍前往训练场训练。",
                List.of(character("郝乐")),
                MemoryContextItem.canonical(
                        "CANONICAL_STATE", "hao-le-location", MemoryContextCategory.CURRENT_STATES,
                        "郝乐位于宿舍。", 13, "chapter-13-final", "FACT_ACTIVE", "第13日", 1));
        ContinuityValidationNode node = new ContinuityValidationNode(
                new ConflictCandidateGenerator(),
                (candidate, ignored) -> {
                    assertThat(candidate.type()).isEqualTo("CHARACTER_STATE");
                    return ContinuitySemanticResult.notConflict();
                });

        Map<String, Object> update = node.apply(state(context));

        assertThat(candidates(update)).hasSize(1);
        assertThat(findings(update)).isEmpty();
        System.out.printf(
                "正常移动：historical=宿舍，current=训练场，candidate=%d，hardFindings=%d%n",
                candidates(update).size(), findings(update).stream()
                        .filter(finding -> finding.getSeverity() == ContinuitySeverity.HARD)
                        .count());
    }

    @Test
    void shouldNotMarkCh16NormalDrugKnowledgeAndMovementHard() {
        ReviewContext context = reviewContext(
                "沈夜持有普通药剂。沈夜知道训练场在北侧。第15日沈夜从宿舍前往训练场。",
                "第16日，沈夜正常使用普通药剂缓解伤势，并根据现场脚印推断门后有人，随后从宿舍前往训练场。",
                List.of(character("沈夜")),
                MemoryContextItem.canonical(
                        "CANONICAL_STATE", "potion", MemoryContextCategory.CURRENT_STATES,
                        "沈夜持有普通药剂。", 15, "chapter-15-final", "FACT_ACTIVE", "第15日", 1),
                MemoryContextItem.canonical(
                        "CANONICAL_KNOWLEDGE", "knowledge", MemoryContextCategory.CONSOLIDATED,
                        "沈夜知道训练场在北侧。", 15, "chapter-15-final", "FACT_ACTIVE", "第15日", 2),
                MemoryContextItem.canonical(
                        "CANONICAL_EVENT", "movement", MemoryContextCategory.EPISODES,
                        "第15日沈夜从宿舍前往训练场。", 15, "chapter-15-final", "ACCEPTED", "第15日", 3),
                MemoryContextItem.canonical(
                        "CANONICAL_RULE", "potion-rule", MemoryContextCategory.RULES,
                        "普通药剂可用于缓解伤势。", 12, "chapter-12-final", "FACT_ACTIVE", "第12日", 1));
        ContinuityValidationNode node = new ContinuityValidationNode(
                new ConflictCandidateGenerator(),
                (candidate, ignored) -> ContinuitySemanticResult.notConflict());

        Map<String, Object> update = node.apply(state(context));

        assertThat(candidates(update)).isNotEmpty()
                .allSatisfy(candidate -> assertThat(candidate.type())
                        .isIn("CHARACTER_STATE", "ITEM_STATE", "ABILITY_RULE", "KNOWLEDGE", "TIMELINE"));
        assertThat(findings(update)).isEmpty();
        System.out.printf(
                "Ch16 负样本：candidates=%d，normalFindings=%d，hardFindings=%d%n",
                candidates(update).size(), findings(update).size(), findings(update).stream()
                        .filter(finding -> finding.getSeverity() == ContinuitySeverity.HARD)
                        .count());
    }

    @Test
    void shouldMapUncertainCandidateToSoft() {
        ReviewContext context = reviewContext(
                "郝乐位于行政楼。",
                "郝乐出现在宿舍。",
                List.of(character("郝乐")),
                MemoryContextItem.canonical(
                        "CANONICAL_STATE", "hao-le-location", MemoryContextCategory.CURRENT_STATES,
                        "郝乐位于行政楼。", 13, "chapter-13-final", "FACT_ACTIVE", "第13日", 1));
        ContinuityValidationNode node = new ContinuityValidationNode(
                new ConflictCandidateGenerator(),
                (candidate, ignored) -> new ContinuitySemanticResult(
                        ContinuitySemanticDecision.UNCERTAIN));

        List<ContinuityFinding> findings = findings(node.apply(state(context)));

        assertThat(findings).singleElement()
                .extracting(ContinuityFinding::getSeverity)
                .isEqualTo(ContinuitySeverity.SOFT);
        System.out.printf("UNCERTAIN 映射检查：severity=%s%n", findings.get(0).getSeverity());
    }

    @Test
    void shouldSendOnlyNarrowEvidenceToModelVerifier() {
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<String> userPrompt = new AtomicReference<>();
        IChapterModelPort model = new IChapterModelPort() {
            @Override
            public Flux<String> stream(String system, String user) {
                return Flux.empty();
            }

            @Override
            public String call(String system, String user) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T call(String system, String user, Class<T> responseType) {
                systemPrompt.set(system);
                userPrompt.set(user);
                return responseType.cast(ContinuitySemanticResult.conflict());
            }
        };
        ConflictCandidate candidate = new ConflictCandidate(
                "CHARACTER_STATE", "郝乐", "郝乐出现在宿舍", "郝乐位于行政楼",
                List.of("郝乐上一章离开行政楼"), "第14日", 13, true, "需要复核");

        ContinuitySemanticResult result = new ModelContinuitySemanticVerifier(model)
                .verify(candidate, ReviewContext.builder().currentDraft("正文").build());

        assertThat(result.decision()).isEqualTo(ContinuitySemanticDecision.CONFLICT);
        assertThat(systemPrompt).hasValueSatisfying(value -> assertThat(value)
                .contains("只能复核输入中给出的一个冲突候选")
                .contains("CONFLICT", "NOT_CONFLICT", "UNCERTAIN"));
        assertThat(userPrompt).hasValueSatisfying(value -> assertThat(value)
                .contains("郝乐出现在宿舍", "郝乐位于行政楼", "郝乐上一章离开行政楼", "第14日")
                .doesNotContain("全文扫描", "其他问题"));
        System.out.printf(
                "语义验证器窄输入检查：decision=%s，promptChars=%d%n",
                result.decision(), userPrompt.get().length());
    }

    @Test
    void shouldCheckpointConflictCandidatesAndFindings() {
        ReviewContext context = reviewContext(
                "郝乐位于行政楼。",
                "郝乐出现在宿舍。",
                List.of(character("郝乐")),
                MemoryContextItem.canonical(
                        "CANONICAL_STATE", "hao-le-location", MemoryContextCategory.CURRENT_STATES,
                        "郝乐位于行政楼。", 13, "chapter-13-final", "FACT_ACTIVE", "第13日", 1));
        Map<String, Object> update = new ContinuityValidationNode(
                new ConflictCandidateGenerator(),
                (candidate, ignored) -> ContinuitySemanticResult.conflict())
                .apply(state(context));
        ChapterGraphState before = new ChapterGraphState(update);
        ChapterCheckpointStateCodec codec = new ChapterCheckpointStateCodec(new ObjectMapper());
        ChapterGraphState restored = new ChapterGraphState(codec.decode(codec.encode(before.data())));

        assertThat(restored.conflictCandidates()).hasSize(1);
        assertThat(restored.conflictCandidates().get(0).type()).isEqualTo("CHARACTER_STATE");
        assertThat(restored.continuityFindings()).singleElement()
                .extracting(ContinuityFinding::getSeverity)
                .isEqualTo(ContinuitySeverity.HARD);
        System.out.printf(
                "ConflictCandidate checkpoint 检查：candidates=%d，findings=%d，entity=%s%n",
                restored.conflictCandidates().size(),
                restored.continuityFindings().size(),
                restored.conflictCandidates().get(0).entity());
    }

    private ChapterGraphState state(ReviewContext context) {
        return new ChapterGraphState(Map.of(ChapterGraphKeys.REVIEW_CONTEXT, context));
    }

    private ReviewContext reviewContext(
            String historicalEvidence,
            String currentDraft,
            List<StoryCharacterEntity> characters,
            MemoryContextItem... additionalItems
    ) {
        List<MemoryContextItem> items = List.of(additionalItems);
        MemoryContextPack pack = new MemoryContextPack(
                MemoryProfile.REVIEW,
                items.stream().filter(item -> item.category() == MemoryContextCategory.RULES).toList(),
                items.stream().filter(item -> item.category() == MemoryContextCategory.OPEN_LOOPS).toList(),
                items.stream().filter(item -> item.category() == MemoryContextCategory.CURRENT_STATES).toList(),
                items.stream().filter(item -> item.category() == MemoryContextCategory.CONSOLIDATED).toList(),
                items.stream().filter(item -> item.category() == MemoryContextCategory.EPISODES).toList(),
                items.stream().mapToInt(MemoryContextItem::estimatedTokenCount).sum());
        return ReviewContext.builder()
                .currentDraft(currentDraft)
                .continuityContext(pack)
                .qualityContext(QualityContext.builder().relevantCharacters(characters).build())
                .build();
    }

    private StoryCharacterEntity character(String name) {
        return StoryCharacterEntity.builder().name(name).build();
    }

    @SuppressWarnings("unchecked")
    private List<ConflictCandidate> candidates(Map<String, Object> update) {
        return (List<ConflictCandidate>) update.get(ChapterGraphKeys.CONFLICT_CANDIDATES);
    }

    @SuppressWarnings("unchecked")
    private List<ContinuityFinding> findings(Map<String, Object> update) {
        return (List<ContinuityFinding>) update.get(ChapterGraphKeys.CONTINUITY_FINDINGS);
    }
}
