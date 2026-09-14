package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryContextProviderTest {

    private final MemoryContextProvider provider = new MemoryContextProvider();

    @Test
    void defaultBudgetAppliesMaxItemsPerCategory() {
        MemoryBudgetSpec budget = MemoryBudgetSpec.defaults();
        List<MemoryContextItem> items = new ArrayList<>();
        Set<String> explicitIds = new HashSet<>();
        for (MemoryContextCategory category : MemoryContextCategory.values()) {
            int count = budget.maxItems(category) + 4;
            for (int index = 0; index < count; index++) {
                String id = category.name() + "-" + index;
                items.add(MemoryContextItem.bridge(
                        id, category, category.name() + " 内容 " + index, index + 1));
                explicitIds.add(id);
            }
        }

        MemoryContextPack pack = provider.provide(
                new MemoryQuerySpec(
                        MemoryProfile.PLAN,
                        Set.of(), Set.of(), Set.of(), Set.of(), explicitIds),
                budget,
                items);
        System.out.printf("类别预算：rules=%d openLoops=%d currentStates=%d consolidated=%d episodes=%d%n",
                pack.rules().size(), pack.openLoops().size(), pack.currentStates().size(),
                pack.consolidated().size(), pack.episodes().size());

        assertThat(pack.rules()).hasSize(budget.rulesMaxItems());
        assertThat(pack.openLoops()).hasSize(budget.openLoopsMaxItems());
        assertThat(pack.currentStates()).hasSize(budget.currentStatesMaxItems());
        assertThat(pack.consolidated()).hasSize(budget.consolidatedMaxItems());
        assertThat(pack.episodes()).hasSize(budget.episodesMaxItems());
        assertThat(pack.estimatedTokenCount()).isLessThanOrEqualTo(budget.totalTokenBudget());
    }

    @Test
    void planPrioritizesOpenLoopsConsolidatedAndRelevantRules() {
        List<MemoryContextItem> items = List.of(
                MemoryContextItem.of("rule-hit", MemoryContextCategory.RULES, "不得穿过禁门", 190),
                MemoryContextItem.of("rule-noise", MemoryContextCategory.RULES, "普通礼仪", 199),
                MemoryContextItem.of("loop-open", MemoryContextCategory.OPEN_LOOPS, "失踪信使尚未找到", 7),
                MemoryContextItem.of("consolidated-arc", MemoryContextCategory.CONSOLIDATED,
                        "本段调查已锁定北港线索", 180),
                MemoryContextItem.bridge("episode-bridge", MemoryContextCategory.EPISODES,
                        "上一章马车继续北上", 199));

        MemoryContextPack pack = provider.provide(
                new MemoryQuerySpec(
                        MemoryProfile.PLAN,
                        Set.of(), Set.of(), Set.of("loop-open"), Set.of("rule-hit"), Set.of()),
                new MemoryBudgetSpec(100, 5, 5, 10, 6, 3),
                items);
        System.out.printf("PLAN 优先级：%s%n", pack.items().stream()
                .map(MemoryContextItem::itemId).toList());

        assertThat(pack.openLoops()).extracting(MemoryContextItem::itemId)
                .containsExactly("loop-open");
        assertThat(pack.consolidated()).extracting(MemoryContextItem::itemId)
                .containsExactly("consolidated-arc");
        assertThat(pack.rules()).extracting(MemoryContextItem::itemId)
                .containsExactly("rule-hit");
        assertThat(pack.rules()).extracting(MemoryContextItem::itemId)
                .doesNotContain("rule-noise");
    }

    @Test
    void draftUsesCurrentStatesAndBridgeEpisodesButNotOrdinaryBreakfast() {
        MemoryContextItem breakfast = MemoryContextItem.of(
                "breakfast-ch3", MemoryContextCategory.EPISODES, "第3章早餐闲谈", 3);
        MemoryContextItem bridge = MemoryContextItem.bridge(
                "previous-chapter-bridge", MemoryContextCategory.EPISODES,
                "上一章结尾：马车驶向北港", 199);
        MemoryContextItem currentState = MemoryContextItem.of(
                "crystal-location", MemoryContextCategory.CURRENT_STATES,
                "残晶当前位于马车暗格", 199);

        MemoryContextPack pack = provider.provide(
                new MemoryQuerySpec(
                        MemoryProfile.DRAFT,
                        Set.of("crystal-location"),
                        Set.of("crystal-location"),
                        Set.of(), Set.of(), Set.of()),
                MemoryBudgetSpec.defaults(),
                List.of(breakfast, bridge, currentState));
        System.out.printf("DRAFT bridge/current：%s%n",
                pack.items().stream().map(MemoryContextItem::itemId).toList());

        assertThat(pack.items()).extracting(MemoryContextItem::itemId)
                .contains("previous-chapter-bridge", "crystal-location")
                .doesNotContain("breakfast-ch3");
    }

    @Test
    void reviewCanTemporarilyReadArchivedEvidenceAndExplicitStaging() {
        MemoryContextItem archivedHistory = MemoryContextItem.archived(
                "superseded-location", MemoryContextCategory.EPISODES,
                "历史：残晶曾位于地下室", 7);
        MemoryContextItem stagingEvidence = MemoryContextItem.staging(
                "staging-evidence", MemoryContextCategory.EPISODES,
                "待审证据：地图来源可疑", 9);

        MemoryContextPack pack = provider.provide(
                new MemoryQuerySpec(
                        MemoryProfile.REVIEW,
                        Set.of(), Set.of(), Set.of(), Set.of(),
                        Set.of("superseded-location", "staging-evidence")),
                MemoryBudgetSpec.defaults(),
                List.of(archivedHistory, stagingEvidence));
        System.out.printf("REVIEW 历史/证据：%s%n",
                pack.items().stream().map(MemoryContextItem::itemId).toList());

        assertThat(pack.items()).extracting(MemoryContextItem::itemId)
                .containsExactlyInAnyOrder("superseded-location", "staging-evidence");
    }

    @Test
    void stagedItemsStayOutOfPlanAndDraftRecall() {
        MemoryContextItem staged = MemoryContextItem.staging(
                "staged-fact", MemoryContextCategory.CURRENT_STATES,
                "暂存状态", 200);
        for (MemoryProfile profile : List.of(MemoryProfile.PLAN, MemoryProfile.DRAFT)) {
            MemoryContextPack pack = provider.provide(
                    new MemoryQuerySpec(profile, Set.of(), Set.of("staged-fact"),
                            Set.of(), Set.of(), Set.of("staged-fact")),
                    MemoryBudgetSpec.defaults(),
                    List.of(staged));
            System.out.printf("%s staging recall=%s%n", profile, pack.items());
            assertThat(pack.items()).isEmpty();
        }
    }

    @Test
    void contextSizeAndTokenEstimateDoNotGrowWithChapterCount() {
        List<Integer> chapterCounts = List.of(10, 50, 100, 200, 300);
        List<Integer> itemCounts = new ArrayList<>();
        List<Integer> tokenCounts = new ArrayList<>();
        for (int chapterCount : chapterCounts) {
            List<MemoryContextItem> items = scalableItems(chapterCount);
            MemoryContextPack pack = provider.provide(
                    new MemoryQuerySpec(
                            MemoryProfile.DRAFT,
                            Set.of("current-state"),
                            Set.of("current-state"),
                            Set.of("loop-chapter-7"),
                            Set.of("rule-long-term"),
                            Set.of()),
                    MemoryBudgetSpec.defaults(),
                    items);
            itemCounts.add(pack.totalItemCount());
            tokenCounts.add(pack.estimatedTokenCount());
            System.out.printf("规模=%d chapters, items=%d, tokens=%d%n",
                    chapterCount, pack.totalItemCount(), pack.estimatedTokenCount());

            assertThat(pack.items()).extracting(MemoryContextItem::itemId)
                    .contains("loop-chapter-7");
            assertThat(pack.estimatedTokenCount())
                    .isLessThanOrEqualTo(MemoryBudgetSpec.defaults().totalTokenBudget());
        }

        assertThat(itemCounts).containsOnly(itemCounts.get(0));
        assertThat(tokenCounts).containsOnly(tokenCounts.get(0));
    }

    @Test
    void budgetTrimDoesNotChangeFactLifecycle() {
        MemoryFact fact = new MemoryFact(
                "fact-location", "残晶 located_at 地下室", List.of("evidence-1"));
        MemoryContextItem item = MemoryContextItem.of(
                fact.factId(), MemoryContextCategory.CURRENT_STATES, fact.proposition(), 1);
        MemoryContextPack pack = provider.provide(
                new MemoryQuerySpec(MemoryProfile.DRAFT),
                new MemoryBudgetSpec(1, 0, 0, 0, 0, 0),
                List.of(item));

        System.out.printf("预算裁剪 items=%d, factStatus=%s%n",
                pack.totalItemCount(), fact.status());
        assertThat(pack.items()).isEmpty();
        assertThat(fact.status()).isEqualTo(cn.ninth.novel.domain.memory.model.MemoryFactStatus.FACT_ACTIVE);
    }

    private static List<MemoryContextItem> scalableItems(int chapterCount) {
        List<MemoryContextItem> items = new ArrayList<>();
        items.add(MemoryContextItem.of(
                "current-state", MemoryContextCategory.CURRENT_STATES,
                "当前场景实体与位置", chapterCount));
        items.add(MemoryContextItem.of(
                "loop-chapter-7", MemoryContextCategory.OPEN_LOOPS,
                "第7章留下的失踪信使伏笔仍未解决", 7));
        items.add(MemoryContextItem.of(
                "rule-long-term", MemoryContextCategory.RULES,
                "回声感知必须主动激活", 7));
        for (int chapter = 1; chapter <= chapterCount; chapter++) {
            items.add(MemoryContextItem.of(
                    "ordinary-episode-" + chapter,
                    MemoryContextCategory.EPISODES,
                    "第" + chapter + "章普通早餐与路人闲谈",
                    chapter));
        }
        for (int index = 0; index < 3; index++) {
            items.add(MemoryContextItem.bridge(
                    "bridge-" + index,
                    MemoryContextCategory.EPISODES,
                    "上一章衔接内容 " + index,
                    chapterCount));
        }
        return items;
    }
}
