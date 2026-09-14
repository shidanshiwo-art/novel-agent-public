package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryAdmissionItem;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryAdmissionKind;
import cn.ninth.novel.domain.memory.model.MemoryProjection;
import cn.ninth.novel.domain.memory.model.MemoryProjectionStatus;
import cn.ninth.novel.domain.memory.model.MemoryQueryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLifecycleManagerTest {

    private final MemoryLifecycleManager lifecycle = new MemoryLifecycleManager();

    @Test
    void ordinaryBreakfastLeavesTheDraftWorkingSet() {
        MemoryAdmissionItem breakfast = MemoryAdmissionItem.event("breakfast", true);
        MemoryAdmissionItem worldRule = MemoryAdmissionItem.worldRule("rule-no-time-travel");
        MemoryAdmissionItem unresolvedLoop = MemoryAdmissionItem.openLoop("loop-missing-seal");
        MemoryQuerySpec query = new MemoryQuerySpec(
                MemoryQueryProfile.DRAFT,
                Set.of(),
                Set.of(),
                Set.of("loop-missing-seal"),
                Set.of("rule-no-time-travel"),
                Set.of());

        List<MemoryAdmissionItem> admitted = lifecycle.admit(
                List.of(breakfast, worldRule, unresolvedLoop), query);
        System.out.printf("DRAFT 工作集：早餐=%s，World Rule=%s，未解决伏笔=%s%n",
                admitted.contains(breakfast), admitted.contains(worldRule), admitted.contains(unresolvedLoop));

        assertThat(admitted).containsExactly(worldRule, unresolvedLoop);
        assertThat(breakfast.ended()).isTrue();
    }

    @Test
    void currentSceneAndEffectiveStateArePromotionSignals() {
        MemoryAdmissionItem character = MemoryAdmissionItem.of(
                "character-shenye", MemoryAdmissionKind.CURRENT_STATE);
        MemoryAdmissionItem item = MemoryAdmissionItem.of(
                "item-crystal", MemoryAdmissionKind.CURRENT_STATE);
        MemoryQuerySpec query = new MemoryQuerySpec(
                MemoryQueryProfile.DRAFT,
                Set.of("character-shenye"),
                Set.of("item-crystal"),
                Set.of(),
                Set.of(),
                Set.of());

        List<MemoryAdmissionItem> admitted = lifecycle.admit(List.of(character, item), query);
        System.out.printf("当前场景/有效状态 admission：%s%n",
                admitted.stream().map(MemoryAdmissionItem::itemId).toList());

        assertThat(admitted).containsExactly(character, item);
    }

    @Test
    void supersededPositionIsNotTheCurrentDraftState() {
        MemoryFact oldPosition = new MemoryFact(
                "position-old", "残晶位于旧钟楼地下室", List.of("event-old"));
        MemoryFact currentPosition = new MemoryFact(
                "position-current", "残晶位于北上马车暗格", List.of("event-current"));
        MemoryFact archivedPosition = lifecycle.supersede(oldPosition);
        MemoryQuerySpec draftQuery = new MemoryQuerySpec(
                MemoryQueryProfile.DRAFT,
                Set.of(),
                Set.of("position-current"),
                Set.of(),
                Set.of(),
                Set.of());

        System.out.printf("SUPERSEDE：旧位置=%s，当前位置=%s%n",
                archivedPosition.status(), currentPosition.status());

        assertThat(archivedPosition.status()).isEqualTo(MemoryFactStatus.FACT_ARCHIVED);
        assertThat(lifecycle.canEnterDraft(oldPosition, draftQuery)).isFalse();
        assertThat(lifecycle.canEnterDraft(currentPosition, draftQuery)).isTrue();
    }

    @Test
    void archivedFactIsReviewOnlyAndRecallDoesNotChangeItsStatus() {
        MemoryFact activeFact = new MemoryFact(
                "fact-location", "残晶位于旧钟楼地下室", List.of("event-old"));
        MemoryFact archivedFact = lifecycle.supersede(activeFact);
        MemoryQuerySpec reviewQuery = new MemoryQuerySpec(
                MemoryQueryProfile.REVIEW,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("fact-location"));
        MemoryQuerySpec draftQuery = new MemoryQuerySpec(
                MemoryQueryProfile.DRAFT,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("fact-location"));

        boolean reviewAdmission = lifecycle.canEnterReview(archivedFact, reviewQuery);
        boolean draftAdmission = lifecycle.canEnterDraft(archivedFact, draftQuery);
        System.out.printf("Archived Fact 查询：REVIEW=%s，DRAFT=%s，status=%s%n",
                reviewAdmission, draftAdmission, archivedFact.status());

        assertThat(reviewAdmission).isTrue();
        assertThat(draftAdmission).isFalse();
        assertThat(archivedFact.status()).isEqualTo(MemoryFactStatus.FACT_ARCHIVED);

        MemoryQuerySpec reviewWithoutExplicitHistory = new MemoryQuerySpec(MemoryQueryProfile.REVIEW);
        assertThat(lifecycle.canEnterReview(archivedFact, reviewWithoutExplicitHistory)).isFalse();
    }

    @Test
    void projectionStaleIsDemotedAndBudgetLikeOmissionDoesNotArchiveFact() {
        MemoryFact fact = new MemoryFact(
                "fact-rule", "夜行能力需要月印", List.of("event-rule"));
        MemoryProjection projection = new MemoryProjection(
                "projection-1", "夜行能力规则摘要", List.of("event-rule", "fact-rule"));
        MemoryQuerySpec query = new MemoryQuerySpec(
                MemoryQueryProfile.DRAFT,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
        MemoryFactStatus before = fact.status();
        MemoryProjection stale = lifecycle.markProjectionStale(projection);
        List<MemoryAdmissionItem> omitted = lifecycle.admit(
                List.of(MemoryAdmissionItem.fact(fact.factId(), fact.status())), query);

        System.out.printf("工作集未接纳普通 Fact：items=%s，Fact status=%s，Projection status=%s%n",
                omitted, fact.status(), stale.status());

        assertThat(omitted).isEmpty();
        assertThat(fact.status()).isEqualTo(before).isEqualTo(MemoryFactStatus.FACT_ACTIVE);
        assertThat(stale.status()).isEqualTo(MemoryProjectionStatus.PROJECTION_STALE);
        assertThat(lifecycle.canEnterDraft(projection, query)).isFalse();

        MemoryFact invalidated = lifecycle.invalidate(fact);
        assertThat(lifecycle.isAdmitted(
                MemoryAdmissionItem.fact(invalidated.factId(), invalidated.status()),
                new MemoryQuerySpec(
                        MemoryQueryProfile.REVIEW,
                        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(invalidated.factId()))))
                .isFalse();
    }
}
