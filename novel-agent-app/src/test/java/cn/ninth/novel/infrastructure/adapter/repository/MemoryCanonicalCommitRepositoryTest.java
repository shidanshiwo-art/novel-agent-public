package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.memory.adapter.repository.MemoryCommitRepository;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryCommitPlan;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemoryCommitResult;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryFactStatusChange;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.domain.memory.service.MemoryCommitGate;
import cn.ninth.novel.domain.memory.service.FinalChapterCandidateExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class MemoryCanonicalCommitRepositoryTest {

    private static final String PROJECT_CODE = "memory-canonical-commit-test";

    @Autowired
    private MemoryCommitGate memoryCommitGate;
    @Autowired
    private MemoryCommitRepository memoryCommitRepository;
    @Autowired
    private FinalChapterCandidateExtractor candidateExtractor;
    @Autowired
    private ContextRepository contextRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchemaAndCleanRows() {
        createMemoryTablesIfMissing();
        cleanRows();
    }

    @AfterEach
    void cleanUp() {
        cleanRows();
    }

    @Test
    void shouldCommitAcceptedVersionCanonicalEventAndOutboxOnceOnRetry() {
        String content = "林舟打开门，发现钥匙在桌上。";
        MemorySourceVersion version = MemorySourceVersion.create("chapter-v1", content);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                        version, content, 0, 4, MemoryCandidateType.EVENT)
                .withStatus(MemoryCandidateStatus.READY_FOR_GATE);
        MemoryCommitRequest request = new MemoryCommitRequest(
                PROJECT_CODE, 1, content, version, List.of(candidate), List.of(), List.of(),
                List.of(), true, "memory-commit-retry");

        MemoryCommitResult first = memoryCommitGate.commit(request);
        MemoryCommitResult retry = memoryCommitGate.commit(request);

        long commits = count("memory_commit");
        long acceptedVersions = count("memory_accepted_chapter_version");
        long events = count("memory_canonical_event");
        long outbox = count("memory_outbox");
        System.out.printf(
                "Canonical retry：first=%s, retry=%s, commit=%d, accepted=%d, event=%d, outbox=%d%n",
                first.alreadyCommitted(), retry.alreadyCommitted(), commits,
                acceptedVersions, events, outbox);

        assertThat(first.alreadyCommitted()).isFalse();
        assertThat(retry.alreadyCommitted()).isTrue();
        assertThat(commits).isEqualTo(1);
        assertThat(acceptedVersions).isEqualTo(1);
        assertThat(events).isEqualTo(1);
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    void shouldRecallNewCanonicalObjectsOnTheNextChapter() {
        String content = "沈夜进入旧钟楼。残晶位于旧钟楼。买家身份仍未查明。";
        MemorySourceVersion version = MemorySourceVersion.create("final:ch11", content);
        List<MemoryCandidate> candidates = candidateExtractor.extract(
                PROJECT_CODE, 11, content, version);
        MemoryCommitRequest request = new MemoryCommitRequest(
                PROJECT_CODE, 11, content, version, candidates, List.of(), List.of(),
                List.of(), true, "finalized-ch11-memory-loop");

        MemoryCommitResult result = memoryCommitGate.commit(request);
        List<MemoryContextItem> nextChapter = contextRepository
                .findCanonicalMemoryContextItems(PROJECT_CODE, 12);

        System.out.printf(
                "FINALIZED Ch11→Ch12 recall：commit=%s, candidates=%d, event=%d, fact=%d, "
                        + "projection=%d, recalled=%s%n",
                result.alreadyCommitted(), candidates.size(), result.eventCount(),
                result.factCount(), result.projectionCount(),
                nextChapter.stream().map(MemoryContextItem::itemId).toList());

        assertThat(result.alreadyCommitted()).isFalse();
        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.factCount()).isEqualTo(1);
        assertThat(result.projectionCount()).isEqualTo(1);
        assertThat(result.outboxCount()).isEqualTo(3);
        assertThat(nextChapter)
                .anyMatch(item -> item.category() == MemoryContextCategory.EPISODES
                        && item.content().equals("沈夜进入旧钟楼。"))
                .anyMatch(item -> item.category() == MemoryContextCategory.CURRENT_STATES
                        && item.content().equals("残晶位于旧钟楼。"))
                .anyMatch(item -> item.category() == MemoryContextCategory.OPEN_LOOPS
                        && item.content().contains("买家身份仍未查明。"));
    }

    @Test
    void shouldRollbackAcceptedVersionCanonicalAndOutboxWhenTransactionFails() {
        String content = "事务失败时不得留下任何正式记忆。";
        MemorySourceVersion version = MemorySourceVersion.create("chapter-rollback", content);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                        version, content, 0, 4, MemoryCandidateType.EVENT)
                .withStatus(MemoryCandidateStatus.READY_FOR_GATE);
        MemoryCommitPlan prepared = memoryCommitGate.prepare(MemoryCommitRequest.forCandidates(
                PROJECT_CODE, 2, content, version, List.of(candidate)));
        MemoryCommitPlan broken = new MemoryCommitPlan(
                prepared.projectCode(), prepared.chapterNumber(), prepared.finalContent(),
                prepared.finalSourceVersion(), prepared.commitKey(), prepared.events(),
                prepared.facts(),
                List.of(new MemoryFactStatusChange(
                        "fact-does-not-exist", MemoryFactStatus.FACT_INVALIDATED, "故意制造事务失败")),
                prepared.projections(), prepared.outboxEntries(), prepared.nonBlockingConflicts());

        assertThatThrownBy(() -> memoryCommitRepository.commit(broken))
                .isInstanceOf(IllegalStateException.class);

        long commits = count("memory_commit");
        long acceptedVersions = count("memory_accepted_chapter_version");
        long events = count("memory_canonical_event");
        long outbox = count("memory_outbox");
        System.out.printf(
                "Canonical rollback：commit=%d, accepted=%d, event=%d, outbox=%d%n",
                commits, acceptedVersions, events, outbox);
        assertThat(commits).isZero();
        assertThat(acceptedVersions).isZero();
        assertThat(events).isZero();
        assertThat(outbox).isZero();
    }

    private long count(String table) {
        if (table.equals("memory_outbox")) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM memory_outbox WHERE commit_key IN "
                            + "(SELECT commit_key FROM memory_commit WHERE project_code = ?)",
                    Long.class, PROJECT_CODE);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE project_code = ?",
                Long.class, PROJECT_CODE);
    }

    private void cleanRows() {
        jdbcTemplate.update("DELETE FROM memory_outbox WHERE commit_key IN "
                + "(SELECT commit_key FROM memory_commit WHERE project_code = ?)", PROJECT_CODE);
        jdbcTemplate.update("DELETE FROM memory_canonical_projection WHERE project_code = ?", PROJECT_CODE);
        jdbcTemplate.update("DELETE FROM memory_canonical_fact WHERE project_code = ?", PROJECT_CODE);
        jdbcTemplate.update("DELETE FROM memory_canonical_event WHERE project_code = ?", PROJECT_CODE);
        jdbcTemplate.update("DELETE FROM memory_accepted_chapter_version WHERE project_code = ?", PROJECT_CODE);
        jdbcTemplate.update("DELETE FROM memory_commit WHERE project_code = ?", PROJECT_CODE);
    }

    private void createMemoryTablesIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_commit (
                    commit_key VARCHAR(255) NOT NULL,
                    project_code VARCHAR(64) NOT NULL,
                    chapter_number INT UNSIGNED NOT NULL,
                    chapter_version VARCHAR(255) NOT NULL,
                    content_hash CHAR(64) NOT NULL,
                    final_content LONGTEXT NOT NULL,
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (commit_key),
                    UNIQUE KEY uk_memory_commit_source
                        (project_code, chapter_number, chapter_version, content_hash)
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
                    UNIQUE KEY uk_memory_accepted_commit (commit_key),
                    UNIQUE KEY uk_memory_accepted_source
                        (project_code, chapter_number, chapter_version, content_hash)
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
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
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
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
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
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (outbox_id),
                    UNIQUE KEY uk_memory_outbox_aggregate
                        (commit_key, aggregate_type, aggregate_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
