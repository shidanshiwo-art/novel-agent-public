package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryEvidenceRef;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryOperation;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryReconcilerTest {

    private final MemoryReconciler reconciler = new MemoryReconciler();

    @Test
    void repeatedCrystalConfirmationReinforcesTheSameFact() {
        MemoryFact existing = fact(
                "fact-crystal-origin",
                "残晶 related_to 玄烛教",
                "old-evidence");
        MemoryCandidate candidate = candidate(
                "candidate-confirmation-2",
                "残晶 related_to 玄烛教",
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.READY_FOR_GATE);

        MemoryOperationDecision decision = reconciler.reconcile(
                candidate, List.of(), List.of(existing));
        System.out.printf("重复确认 operation=%s fact=%s reason=%s%n",
                decision.operation(), decision.relatedFact().factId(), decision.reason());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.REINFORCE);
        assertThat(decision.relatedFact()).isSameAs(existing);
        assertThat(existing.status()).isEqualTo(MemoryFactStatus.FACT_ACTIVE);
    }

    @Test
    void sameCandidateAndSameEvidenceAreNoop() {
        String statement = "黑色残晶 located_at 旧钟楼地下室";
        MemoryCandidate candidate = candidate(
                "candidate-same-evidence",
                statement,
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.PROVISIONAL);
        MemoryFact existing = fact(
                "fact-basement",
                statement,
                candidate.candidateId());

        MemoryOperationDecision decision = reconciler.reconcile(
                candidate, List.of(existing));
        System.out.printf("相同 candidate/evidence operation=%s reason=%s%n",
                decision.operation(), decision.reason());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.NOOP);
        assertThat(decision.relatedFact()).isSameAs(existing);
    }

    @Test
    void crystalMovementSupersedesOldLocationWithoutOverwritingIt() {
        MemoryFact oldLocation = fact(
                "fact-basement",
                "黑色残晶 located_at 旧钟楼地下室",
                "chapter-5-evidence");
        MemoryCandidate moved = candidate(
                "candidate-carriage",
                "黑色残晶 located_at 北上马车暗格",
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.READY_FOR_GATE);

        MemoryOperationDecision decision = reconciler.reconcile(
                moved, List.of(oldLocation));
        System.out.printf("残晶移动 operation=%s oldFact=%s oldStatus=%s%n",
                decision.operation(), decision.relatedFact().factId(), oldLocation.status());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.SUPERSEDE);
        assertThat(decision.relatedFact()).isSameAs(oldLocation);
        assertThat(oldLocation.status()).isEqualTo(MemoryFactStatus.FACT_ACTIVE);
        assertThat(decision.candidate().evidenceRange().excerpt())
                .contains("北上马车暗格");
    }

    @Test
    void fakeMapExplicitlyInvalidatesOneExistingFact() {
        MemoryFact fakeMap = fact(
                "fact-fake-map",
                "地图显示北境入口在东门",
                "map-evidence");
        MemoryCandidate invalidation = candidate(
                "candidate-fake-map-review",
                "伪地图已被证明错误",
                MemoryCandidateType.FACT_INVALIDATION,
                MemoryCandidateStatus.READY_FOR_GATE);

        MemoryOperationDecision decision = reconciler.reconcile(
                invalidation, List.of(fakeMap));
        System.out.printf("伪地图 operation=%s fact=%s reason=%s%n",
                decision.operation(), decision.relatedFact().factId(), decision.reason());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.INVALIDATE);
        assertThat(decision.relatedFact()).isSameAs(fakeMap);
        assertThat(fakeMap.status()).isEqualTo(MemoryFactStatus.FACT_ACTIVE);
    }

    @Test
    void guessAndConfirmationRemainSeparateProvenanceAndAreNotMergedBySimilarity() {
        MemoryFact guess = fact(
                "fact-guess",
                "残晶 located_at 旧钟楼地下室（猜测）",
                "guess-evidence");
        MemoryCandidate confirmation = candidate(
                "candidate-confirmed-location",
                "残晶 located_at 旧钟楼地下室",
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.READY_FOR_GATE);

        MemoryOperationDecision decision = reconciler.reconcile(
                confirmation, List.of(guess));
        System.out.printf("猜测到确认 operation=%s oldSource=%s newEvidence=%s%n",
                decision.operation(), guess.sourceIds(),
                confirmation.evidenceRange().excerpt());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.SUPERSEDE);
        assertThat(decision.relatedFact().sourceIds()).containsExactly("guess-evidence");
        assertThat(decision.candidate().evidenceRange().excerpt())
                .isNotEqualTo(decision.relatedFact().proposition());
    }

    @Test
    void unrelatedFactsAreAddedAndNotMergedByTextualSimilarity() {
        MemoryFact ownership = fact(
                "fact-ownership",
                "残晶 owns 玄烛教",
                "ownership-evidence");
        MemoryCandidate location = candidate(
                "candidate-location",
                "残晶 located_at 北上马车暗格",
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.READY_FOR_GATE);

        MemoryOperationDecision decision = reconciler.reconcile(
                location, List.of(ownership));
        System.out.printf("不同状态槽位 operation=%s reason=%s%n",
                decision.operation(), decision.reason());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.ADD);
        assertThat(decision.relatedFact()).isNull();
    }

    @Test
    void staleCandidateInvalidatesOnlyFactWithTheSameEvidenceBinding() {
        MemoryCandidate stale = candidate(
                "candidate-retracted",
                "地图显示北境入口在东门",
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.STALE);
        MemoryFact sameSource = fact("fact-old-map", stale.evidenceRange().excerpt(), stale.candidateId());
        MemoryFact unrelated = fact("fact-unrelated", "残晶 located_at 地下室", "other-source");

        MemoryOperationDecision decision = reconciler.reconcile(
                stale, List.of(sameSource, unrelated));
        System.out.printf("来源撤回 operation=%s fact=%s%n",
                decision.operation(), decision.relatedFact().factId());

        assertThat(decision.operation()).isEqualTo(MemoryOperation.INVALIDATE);
        assertThat(decision.relatedFact()).isSameAs(sameSource);
    }

    @Test
    void candidateWithoutEvidenceExcerptCannotBeReconciledAsFact() {
        MemorySourceVersion version = MemorySourceVersion.create("chapter-1", "事实正文");
        MemoryCandidate candidate = new MemoryCandidate(
                "candidate-without-excerpt",
                version,
                MemoryEvidenceRef.of(0, 2),
                MemoryCandidateType.FACT,
                MemoryCandidateStatus.PROVISIONAL);

        assertThatThrownBy(() -> reconciler.reconcile(candidate, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excerpt");
        System.out.println("缺少可比较 evidence excerpt 的 Candidate 被拒绝进入 Reconcile");
    }

    private static MemoryFact fact(String id, String proposition, String sourceId) {
        return new MemoryFact(id, proposition, List.of(sourceId));
    }

    private static MemoryCandidate candidate(
            String id,
            String statement,
            MemoryCandidateType type,
            MemoryCandidateStatus status
    ) {
        MemorySourceVersion version = MemorySourceVersion.create("version-" + id, statement);
        MemoryEvidenceRef evidence = MemoryEvidenceRef.of(statement, 0, statement.length());
        return new MemoryCandidate(id, version, evidence, type, status);
    }
}
