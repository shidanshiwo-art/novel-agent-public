package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.QualityContext;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ReviewPreparation 固定上下文边界测试。 */
class ReviewPreparationContextTest {

    @Test
    void shouldFilterContinuityMemoryByEntitiesInCurrentDraft() {
        ChapterGraphState state = fixtureState();

        Map<String, Object> update = new ReviewPreparationNode().apply(state);
        ReviewContext reviewContext = (ReviewContext) update.get(ChapterGraphKeys.REVIEW_CONTEXT);
        MemoryContextPack continuity = (MemoryContextPack) reviewContext.getContinuityContext();

        List<String> selected = continuity.items().stream()
                .map(MemoryContextItem::content)
                .toList();
        assertThat(selected).contains(
                "沈夜携带灰白材料",
                "郝乐曾记录旧锁的位置",
                "旧锁附近出现异常声响",
                "灰白材料不能接触火焰",
                "沈夜尚未打开旧锁");
        assertThat(selected).doesNotContain(
                "林默保管着一枚玉佩",
                "北境发生了与本章无关的战争",
                "天衡城的税制已经改变");
        assertThat(continuity.totalItemCount()).isLessThan(8);

        System.out.printf(
                "ContinuityContext 筛选检查：selected=%d/%d，items=%s%n",
                continuity.totalItemCount(),
                state.context().orElseThrow().getReviewMemoryContextPack().totalItemCount(),
                selected
        );
    }

    @Test
    void shouldKeepQualityContextFreeOfHistoricalMemoryPack() {
        ChapterGraphState state = fixtureState();

        Map<String, Object> update = new ReviewPreparationNode().apply(state);
        ReviewContext reviewContext = (ReviewContext) update.get(ChapterGraphKeys.REVIEW_CONTEXT);
        QualityContext quality = (QualityContext) reviewContext.getQualityContext();

        assertThat(quality.getChapterPlan()).isSameAs(state.context().orElseThrow().getChapterPlan());
        assertThat(quality.getCurrentDraft()).isEqualTo(state.draft().orElseThrow());
        assertThat(quality.getRelevantCharacters())
                .extracting(StoryCharacterEntity::getName)
                .containsExactly("沈夜", "郝乐");
        assertThat(quality.getArcGoal()).isEqualTo("查明旧锁与灰白材料的关系");
        assertThat(quality.getPreviousChapterEndingBridge()).isEqualTo("旧锁在门后自行转动");

        // QualityContext 的字段集合只承载质量审核所需投影，不存在 historical facts/events 列表。
        assertThat(quality.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("historicalFacts", "recentEvents", "memoryContextPack");
        assertThat(String.valueOf(quality)).doesNotContain("北境发生了与本章无关的战争");

        System.out.printf(
                "QualityContext 边界检查：characters=%s，arcGoal=%s，endingBridge=%s%n",
                quality.getRelevantCharacters().stream()
                        .map(StoryCharacterEntity::getName)
                        .toList(),
                quality.getArcGoal(),
                quality.getPreviousChapterEndingBridge()
        );
    }

    @Test
    void shouldSerializePreparedContextWithoutReintroducingFullMemory() {
        ReviewContext prepared = (ReviewContext) new ReviewPreparationNode()
                .apply(fixtureState())
                .get(ChapterGraphKeys.REVIEW_CONTEXT);
        ChapterCheckpointStateCodec codec = new ChapterCheckpointStateCodec(new ObjectMapper());
        ChapterGraphState checkpointState = new ChapterGraphState(Map.of(
                ChapterGraphKeys.REVIEW_CONTEXT, prepared));
        ChapterGraphState restored = new ChapterGraphState(
                codec.decode(codec.encode(checkpointState.data())));

        assertThat(restored.reviewContext()).hasValueSatisfying(value -> {
            assertThat(value.getCurrentDraft()).contains("沈夜", "旧锁");
            assertThat(value.getContinuityContext()).isInstanceOf(MemoryContextPack.class);
            assertThat(value.getQualityContext()).isInstanceOf(QualityContext.class);
            assertThat(String.valueOf(value.getQualityContext()))
                    .doesNotContain("北境发生了与本章无关的战争");
        });
        System.out.printf(
                "ReviewContext 序列化检查：draft=%s，qualityContextType=%s%n",
                restored.reviewContext().orElseThrow().getCurrentDraft(),
                restored.reviewContext().orElseThrow().getQualityContext().getClass().getSimpleName()
        );
    }

    private ChapterGraphState fixtureState() {
        ChapterPlanEntity chapterPlan = ChapterPlanEntity.builder()
                .chapterNumber(3)
                .title("旧锁")
                .summary("沈夜与郝乐调查灰白材料和旧锁")
                .build();
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .chapterPlan(chapterPlan)
                .arc(new OutlineNodeVO(
                        "arc-1", "volume-1", OutlineNodeKindEnum.ARC, 1,
                        "旧城疑云", "查明旧锁与灰白材料的关系", 1, 10, "READY"))
                .characters(List.of(
                        StoryCharacterEntity.builder().name("沈夜").personality("谨慎").build(),
                        StoryCharacterEntity.builder().name("郝乐").personality("直接").build(),
                        StoryCharacterEntity.builder().name("林默").personality("沉默").build()))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(
                                ChapterMemoryVO.builder()
                                        .chapterNumber(2)
                                        .endingHook("旧锁在门后自行转动")
                                        .build()))
                        .build())
                .reviewMemoryContextPack(reviewMemoryFixture())
                .build();
        return new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.DRAFT, "沈夜与郝乐查看灰白材料，随后走向旧锁。"));
    }

    private MemoryContextPack reviewMemoryFixture() {
        return new MemoryContextPack(
                MemoryProfile.REVIEW,
                List.of(
                        MemoryContextItem.of(
                                "rule-related", MemoryContextCategory.RULES,
                                "灰白材料不能接触火焰", 2),
                        MemoryContextItem.of(
                                "rule-unrelated", MemoryContextCategory.RULES,
                                "天衡城的税制已经改变", 1)),
                List.of(
                        MemoryContextItem.of(
                                "loop-related", MemoryContextCategory.OPEN_LOOPS,
                                "沈夜尚未打开旧锁", 2)),
                List.of(
                        MemoryContextItem.of(
                                "state-related", MemoryContextCategory.CURRENT_STATES,
                                "沈夜携带灰白材料", 2),
                        MemoryContextItem.of(
                                "state-unrelated", MemoryContextCategory.CURRENT_STATES,
                                "林默保管着一枚玉佩", 2)),
                List.of(
                        MemoryContextItem.of(
                                "fact-related", MemoryContextCategory.CONSOLIDATED,
                                "郝乐曾记录旧锁的位置", 1),
                        MemoryContextItem.of(
                                "fact-unrelated", MemoryContextCategory.CONSOLIDATED,
                                "北境发生了与本章无关的战争", 1)),
                List.of(
                        MemoryContextItem.of(
                                "event-related", MemoryContextCategory.EPISODES,
                                "旧锁附近出现异常声响", 2)),
                0);
    }
}
