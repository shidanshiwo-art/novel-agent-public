package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.service.data.ChapterContextLoader;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在已完成 backfill 的真实 MySQL 上验证 Canonical 经过 Repository、Loader、Provider
 * 进入 PLAN/DRAFT/REVIEW；本测试只读，不负责创建或清理 backfill 数据。
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "runCanonicalBackfillVerification", matches = "true")
class CanonicalMemoryBackfillRuntimeIntegrationTest {

    @Autowired
    private ContextRepository contextRepository;
    @Autowired
    private PlanningRepository planningRepository;
    @Autowired
    private ChapterContextLoader chapterContextLoader;

    @Test
    void shouldExposeNovel001BackfillToDraftPlanAndReview() {
        List<MemoryContextItem> contextCandidates = contextRepository
                .findCanonicalMemoryContextItems("novel-001", 11);
        ChapterContextAggregate draftContext = chapterContextLoader
                .loadContext("novel-001", 11, MemoryMode.AUTO);
        MemoryContextPack draft = draftContext.getMemoryContextPack();

        List<MemoryContextItem> planningCandidates = planningRepository
                .findCanonicalMemoryContextItems("novel-001", 11);
        MemoryContextProvider provider = new MemoryContextProvider();
        MemoryContextPack plan = provider.provide(
                MemoryQuerySpec.plan(), MemoryBudgetSpec.defaultP0(), planningCandidates);
        MemoryContextPack review = provider.provide(
                MemoryQuerySpec.review(), MemoryBudgetSpec.defaultP0(), contextCandidates);

        System.out.printf(
                "novel-001 Canonical runtime verification: candidates=%d, DRAFT=%s, PLAN=%s, REVIEW=%s%n",
                contextCandidates.size(), ids(draft), ids(plan), ids(review));

        assertThat(contextCandidates)
                .isNotEmpty()
                .allMatch(item -> item.sourceType().startsWith("CANONICAL_"));
        assertThat(draft).isNotNull();
        assertThat(draft.currentStates())
                .isNotEmpty()
                .allMatch(item -> "CANONICAL_FACT".equals(item.sourceType()))
                .anyMatch(item -> item.content().contains("残晶")
                        || item.content().contains("同源")
                        || item.content().contains("频率"));
        assertThat(plan.consolidated())
                .isNotEmpty()
                .allMatch(item -> item.category() == MemoryContextCategory.CONSOLIDATED)
                .allMatch(item -> "CANONICAL_PROJECTION".equals(item.sourceType()))
                .anyMatch(item -> "PROJECTION_ACTIVE".equals(item.status()));
        assertThat(review.episodes())
                .isNotEmpty()
                .allMatch(item -> "CANONICAL_EVENT".equals(item.sourceType()));
    }

    private List<String> ids(MemoryContextPack pack) {
        return pack == null ? List.of() : pack.items().stream()
                .map(MemoryContextItem::itemId)
                .toList();
    }
}
