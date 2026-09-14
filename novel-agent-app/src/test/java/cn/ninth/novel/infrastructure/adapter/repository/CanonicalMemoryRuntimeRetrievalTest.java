package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.adapter.repository.IContextRepository;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import cn.ninth.novel.domain.chapter.service.data.ChapterContextLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 使用真实 MySQL 验证 Canonical Runtime Retrieval；不通过 Legacy summary 伪造候选。
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class CanonicalMemoryRuntimeRetrievalTest {

    private final String projectCode = "canonical-runtime-" + UUID.randomUUID();

    @Autowired
    private ContextRepository contextRepository;
    @Autowired
    private PlanningRepository planningRepository;
    @Autowired
    private ChapterContextLoader chapterContextLoader;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        createCanonicalTablesIfMissing();
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, words_per_chapter, current_chapter_number, status)
                VALUES (?, 'Canonical runtime test', '玄幻', 2000, 4, 'DRAFT')
                """, projectCode);
        insertAcceptedSource("commit-event", 2, "chapter-v2", "事件正文");
        insertAcceptedSource("commit-fact-active", 2, "chapter-v2", "事实正文");
        insertAcceptedSource("commit-fact-archived", 2, "chapter-v2", "归档事实正文");
        insertAcceptedSource("commit-fact-invalidated", 2, "chapter-v2", "失效事实正文");
        insertAcceptedSource("commit-projection-active", 2, "chapter-v2", "当前投影正文");
        insertAcceptedSource("commit-projection-stale", 2, "chapter-v2", "过期投影正文");
        insertAcceptedSource("commit-projection-archived", 2, "chapter-v2", "归档投影正文");
        insertAcceptedSource("commit-future", 6, "chapter-v6", "未来正文");

        insertEvent("event-accepted", 2, "chapter-v2", "林舟在钟楼取得残图。", "第2日", hash("commit-event"));
        insertEvent("event-unaccepted", 2, "chapter-not-accepted", "不应进入上下文的事件。", "第2日", hash("not-accepted"));
        insertEvent("event-future", 6, "chapter-v6", "未来章节事件。", "第6日", hash("commit-future"));
        insertFact("fact-active", "残图当前在林舟手中。", "FACT_ACTIVE", "commit-fact-active");
        insertFact("fact-archived", "残图曾在守门人手中。", "FACT_ARCHIVED", "commit-fact-archived");
        insertFact("fact-invalidated", "林舟从未进入钟楼。", "FACT_INVALIDATED", "commit-fact-invalidated");
        insertProjection("projection-active", "残图线索正在推动北港调查。", "PROJECTION_ACTIVE", "commit-projection-active");
        insertProjection("projection-stale", "旧调查阶段结果，等待重建。", "PROJECTION_STALE", "commit-projection-stale");
        insertProjection("projection-archived", "已经结束的旧调查阶段结果。", "PROJECTION_ARCHIVED", "commit-projection-archived");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM memory_outbox WHERE commit_key IN "
                + "(SELECT commit_key FROM memory_commit WHERE project_code = ?)", projectCode);
        jdbcTemplate.update("DELETE FROM memory_canonical_projection WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_canonical_fact WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_canonical_event WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_accepted_chapter_version WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_commit WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM novel_project WHERE project_code = ?", projectCode);
    }

    @Test
    void shouldReadAcceptedCanonicalObjectsAndApplySourceBoundaries() {
        List<MemoryContextItem> contextItems = contextRepository
                .findCanonicalMemoryContextItems(projectCode, 5);

        System.out.printf(
                "Canonical runtime candidates: total=%d, ids=%s, sourceTypes=%s%n",
                contextItems.size(),
                contextItems.stream().map(MemoryContextItem::itemId).toList(),
                contextItems.stream().map(MemoryContextItem::sourceType).distinct().toList());

        assertThat(contextItems).extracting(MemoryContextItem::itemId)
                .containsExactlyInAnyOrder(
                        "fact-active", "fact-archived",
                        "projection-active", "projection-stale", "projection-archived",
                        "event-accepted");
        assertThat(contextItems).noneMatch(item -> item.itemId().equals("fact-invalidated"));
        assertThat(contextItems).noneMatch(item -> item.itemId().equals("event-unaccepted"));
        assertThat(contextItems).noneMatch(item -> item.itemId().equals("event-future"));

        MemoryContextItem event = item(contextItems, "event-accepted");
        assertThat(event)
                .extracting(MemoryContextItem::category, MemoryContextItem::sourceType,
                        MemoryContextItem::sourceId, MemoryContextItem::sourceChapter,
                        MemoryContextItem::sourceVersion, MemoryContextItem::status,
                        MemoryContextItem::storyTime, MemoryContextItem::order)
                .containsExactly(MemoryContextCategory.EPISODES, "CANONICAL_EVENT",
                        "event-accepted", 2, "chapter-v2", "ACCEPTED", "第2日", 2);

        MemoryContextItem archivedFact = item(contextItems, "fact-archived");
        assertThat(archivedFact)
                .extracting(MemoryContextItem::category, MemoryContextItem::status,
                        MemoryContextItem::archived, MemoryContextItem::sourceChapter,
                        MemoryContextItem::sourceVersion)
                .containsExactly(MemoryContextCategory.CURRENT_STATES, "FACT_ARCHIVED",
                        true, 2, "chapter-v2");

        MemoryContextItem staleProjection = item(contextItems, "projection-stale");
        assertThat(staleProjection)
                .extracting(MemoryContextItem::category, MemoryContextItem::status,
                        MemoryContextItem::archived, MemoryContextItem::sourceType)
                .containsExactly(MemoryContextCategory.CONSOLIDATED, "PROJECTION_STALE",
                        true, "CANONICAL_PROJECTION");
    }

    @Test
    void shouldPutCanonicalFactIntoDraftContextWithoutArchivedOrInvalidatedFacts() {
        ChapterContextAggregate context = chapterContextLoader.loadContext(
                projectCode, 5, MemoryMode.V1);
        MemoryContextPack pack = context.getMemoryContextPack();

        System.out.printf(
                "DRAFT Canonical ContextPack: items=%d, currentStates=%s, reviewItems=%d%n",
                pack == null ? 0 : pack.totalItemCount(),
                pack == null ? List.of() : pack.currentStates().stream()
                        .map(MemoryContextItem::itemId).toList(),
                context.getReviewMemoryContextPack() == null
                        ? 0 : context.getReviewMemoryContextPack().totalItemCount());

        assertThat(pack).isNotNull();
        assertThat(pack.currentStates()).extracting(MemoryContextItem::itemId)
                .contains("fact-active")
                .doesNotContain("fact-archived", "fact-invalidated");
        assertThat(pack.currentStates()).allMatch(item -> "CANONICAL_FACT".equals(item.sourceType()));
    }

    @Test
    void shouldPutActiveProjectionIntoPlanAndHistoricalObjectsIntoReview() {
        List<MemoryContextItem> candidates = planningRepository
                .findCanonicalMemoryContextItems(projectCode, 5);
        MemoryContextProvider provider = new MemoryContextProvider();
        MemoryContextPack plan = provider.provide(
                MemoryQuerySpec.plan(),
                cn.ninth.novel.domain.memory.model.MemoryBudgetSpec.defaultP0(),
                candidates);
        MemoryContextPack review = provider.provide(
                MemoryQuerySpec.review(),
                cn.ninth.novel.domain.memory.model.MemoryBudgetSpec.defaultP0(),
                candidates);

        System.out.printf(
                "PLAN/REVIEW Canonical packs: plan=%s, review=%s%n",
                plan.items().stream().map(MemoryContextItem::itemId).toList(),
                review.items().stream().map(MemoryContextItem::itemId).toList());

        assertThat(plan.consolidated()).extracting(MemoryContextItem::itemId)
                .contains("projection-active")
                .doesNotContain("projection-stale", "projection-archived");
        assertThat(review.episodes()).extracting(MemoryContextItem::itemId)
                .contains("event-accepted");
        assertThat(review.currentStates()).extracting(MemoryContextItem::itemId)
                .contains("fact-active", "fact-archived")
                .doesNotContain("fact-invalidated");
        assertThat(review.consolidated()).extracting(MemoryContextItem::itemId)
                .contains("projection-active", "projection-stale", "projection-archived");
    }

    @Test
    void shouldNotReadLegacyWhenCanonicalPackAlreadyHasAHit() {
        IContextRepository repository = mock(IContextRepository.class);
        MemoryContextItem canonicalFact = MemoryContextItem.canonical(
                "CANONICAL_FACT", "fact-hit", MemoryContextCategory.CURRENT_STATES,
                "Canonical 当前状态", 2, "chapter-v2", "FACT_ACTIVE", null, 2);
        when(repository.findCanonicalMemoryContextItems("mock-project", 5))
                .thenReturn(List.of(canonicalFact));
        when(repository.findLegacyMemoryContextItems("mock-project", 5))
                .thenThrow(new AssertionError("canonical 命中后不应读取 Legacy"));

        ChapterContextAggregate context = new ChapterContextLoader(repository)
                .loadContext("mock-project", 5, MemoryMode.AUTO);

        System.out.printf(
                "Canonical-first fallback gate: currentStates=%s%n",
                context.getMemoryContextPack().currentStates().stream()
                        .map(MemoryContextItem::itemId).toList());
        assertThat(context.getMemoryContextPack().currentStates())
                .extracting(MemoryContextItem::itemId).containsExactly("fact-hit");
    }

    private MemoryContextItem item(List<MemoryContextItem> items, String itemId) {
        return items.stream().filter(item -> item.itemId().equals(itemId)).findFirst().orElseThrow();
    }

    private void insertAcceptedSource(
            String commitKey,
            int chapterNumber,
            String chapterVersion,
            String content
    ) {
        String contentHash = hash(commitKey);
        jdbcTemplate.update("""
                INSERT INTO memory_commit
                    (commit_key, project_code, chapter_number, chapter_version, content_hash, final_content)
                VALUES (?, ?, ?, ?, ?, ?)
                """, commitKey, projectCode, chapterNumber, chapterVersion, contentHash, content);
        jdbcTemplate.update("""
                INSERT INTO memory_accepted_chapter_version
                    (commit_key, project_code, chapter_number, chapter_version, content_hash, final_content)
                VALUES (?, ?, ?, ?, ?, ?)
                """, commitKey, projectCode, chapterNumber, chapterVersion, contentHash, content);
    }

    private void insertEvent(
            String eventId,
            int chapterNumber,
            String chapterVersion,
            String description,
            String storyTime,
            String contentHash
    ) {
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_event
                    (event_id, project_code, chapter_number, description, chapter_version,
                     content_hash, evidence_start_offset, evidence_end_offset, evidence_excerpt,
                     story_time, candidate_id)
                VALUES (?, ?, ?, ?, ?, ?, 0, 1, ?, ?, ?)
                """, eventId, projectCode, chapterNumber, description, chapterVersion,
                contentHash, description, storyTime, eventId);
    }

    private void insertFact(
            String factId,
            String proposition,
            String status,
            String commitKey
    ) {
        insertOutbox(commitKey, "FACT", factId);
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_fact
                    (fact_id, project_code, proposition, source_ids_json, status, candidate_id)
                VALUES (?, ?, ?, '["event-accepted"]', ?, ?)
                """, factId, projectCode, proposition, status, factId);
    }

    private void insertProjection(
            String projectionId,
            String content,
            String status,
            String commitKey
    ) {
        insertOutbox(commitKey, "PROJECTION", projectionId);
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_projection
                    (projection_id, project_code, content, source_ids_json, status)
                VALUES (?, ?, ?, '["event-accepted", "fact-active"]', ?)
                """, projectionId, projectCode, content, status);
    }

    private void insertOutbox(String commitKey, String aggregateType, String aggregateId) {
        jdbcTemplate.update("""
                INSERT INTO memory_outbox
                    (outbox_id, commit_key, aggregate_type, aggregate_id, payload, status)
                VALUES (?, ?, ?, ?, ?, 'DONE')
                """, hash(commitKey + aggregateType + aggregateId), commitKey,
                aggregateType, aggregateId, aggregateId);
    }

    private String hash(String value) {
        return String.format("%064d", Math.abs((long) value.hashCode()));
    }

    private void createCanonicalTablesIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_commit (
                    commit_key VARCHAR(255) NOT NULL,
                    project_code VARCHAR(64) NOT NULL,
                    chapter_number INT UNSIGNED NOT NULL,
                    chapter_version VARCHAR(255) NOT NULL,
                    content_hash CHAR(64) NOT NULL,
                    final_content LONGTEXT NOT NULL,
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (commit_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_accepted_chapter_version (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    commit_key VARCHAR(255) NOT NULL,
                    project_code VARCHAR(64) NOT NULL,
                    chapter_number INT UNSIGNED NOT NULL,
                    chapter_version VARCHAR(255) NOT NULL,
                    content_hash CHAR(64) NOT NULL,
                    final_content LONGTEXT NOT NULL,
                    accepted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_memory_accepted_commit (commit_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_canonical_event (
                    event_id VARCHAR(128) NOT NULL,
                    project_code VARCHAR(64) NOT NULL,
                    chapter_number INT UNSIGNED NOT NULL,
                    description TEXT NOT NULL,
                    chapter_version VARCHAR(255) NOT NULL,
                    content_hash CHAR(64) NOT NULL,
                    evidence_start_offset INT UNSIGNED NOT NULL,
                    evidence_end_offset INT UNSIGNED NOT NULL,
                    evidence_excerpt TEXT NOT NULL,
                    story_time VARCHAR(255) DEFAULT NULL,
                    candidate_id VARCHAR(128) NOT NULL,
                    PRIMARY KEY (event_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_canonical_fact (
                    fact_id VARCHAR(128) NOT NULL,
                    project_code VARCHAR(64) NOT NULL,
                    proposition TEXT NOT NULL,
                    source_ids_json JSON NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    candidate_id VARCHAR(128) NOT NULL,
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (fact_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_canonical_projection (
                    projection_id VARCHAR(128) NOT NULL,
                    project_code VARCHAR(64) NOT NULL,
                    content TEXT NOT NULL,
                    source_ids_json JSON NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (projection_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_outbox (
                    outbox_id CHAR(64) NOT NULL,
                    commit_key VARCHAR(255) NOT NULL,
                    aggregate_type VARCHAR(32) NOT NULL,
                    aggregate_id VARCHAR(128) NOT NULL,
                    payload LONGTEXT NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    PRIMARY KEY (outbox_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
