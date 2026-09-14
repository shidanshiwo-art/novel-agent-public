package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.memory.adapter.repository.MemoryCommitRepository;
import cn.ninth.novel.domain.memory.model.CanonicalEvent;
import cn.ninth.novel.domain.memory.model.CanonicalFact;
import cn.ninth.novel.domain.memory.model.CanonicalProjection;
import cn.ninth.novel.domain.memory.model.MemoryCommitPlan;
import cn.ninth.novel.domain.memory.model.MemoryCommitResult;
import cn.ninth.novel.domain.memory.model.MemoryFactStatusChange;
import cn.ninth.novel.domain.memory.model.MemoryOutboxEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/**
 * MySQL Canonical Commit 事务适配器。
 *
 * <p>PGVector 不在这里同步写入，只由本事务写入 outbox，后续异步消费。</p>
 */
@Repository
public class MemoryCanonicalCommitRepository implements MemoryCommitRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public MemoryCanonicalCommitRepository(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public MemoryCommitResult commit(MemoryCommitPlan plan) {
        return transactionTemplate.execute(status -> commitInTransaction(plan));
    }

    private MemoryCommitResult commitInTransaction(MemoryCommitPlan plan) {
        int insertedCommit = jdbcTemplate.update("""
                INSERT IGNORE INTO memory_commit
                    (commit_key, project_code, chapter_number, chapter_version, content_hash,
                     final_content)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                plan.commitKey(), plan.projectCode(), plan.chapterNumber(),
                plan.finalSourceVersion().chapterVersion(),
                plan.finalSourceVersion().contentHash(), plan.finalContent());
        if (insertedCommit == 0) {
            String existingSource = jdbcTemplate.query(
                    "SELECT chapter_version, content_hash FROM memory_commit WHERE commit_key = ?",
                    resultSet -> resultSet.next()
                            ? resultSet.getString(1) + "#" + resultSet.getString(2)
                            : null,
                    plan.commitKey());
            String requestedSource = plan.finalSourceVersion().chapterVersion()
                    + "#" + plan.finalSourceVersion().contentHash();
            if (existingSource != null && !existingSource.equals(requestedSource)) {
                throw new IllegalStateException(
                        "Canonical commitKey 已绑定其他正文版本，commitKey=" + plan.commitKey());
            }
            return MemoryCommitResult.alreadyCommitted(plan.commitKey());
        }

        jdbcTemplate.update("""
                INSERT INTO memory_accepted_chapter_version
                    (commit_key, project_code, chapter_number, chapter_version, content_hash,
                     final_content)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                plan.commitKey(), plan.projectCode(), plan.chapterNumber(),
                plan.finalSourceVersion().chapterVersion(),
                plan.finalSourceVersion().contentHash(), plan.finalContent());

        for (CanonicalEvent event : plan.events()) {
            insertEvent(event, plan.finalContent());
        }
        for (CanonicalFact fact : plan.facts()) {
            insertFact(fact);
        }
        for (MemoryFactStatusChange change : plan.factStatusChanges()) {
            int updated = jdbcTemplate.update(
                    "UPDATE memory_canonical_fact SET status = ? WHERE fact_id = ?",
                    change.status().name(), change.factId());
            if (updated != 1) {
                throw new IllegalStateException(
                        "Canonical Fact 状态变更目标不存在，factId=" + change.factId());
            }
        }
        for (CanonicalProjection projection : plan.projections()) {
            insertProjection(projection);
        }
        for (MemoryOutboxEntry entry : plan.outboxEntries()) {
            insertOutbox(entry);
        }

        return new MemoryCommitResult(
                plan.commitKey(), false, plan.events().size(), plan.facts().size(),
                plan.projections().size(), plan.outboxEntries().size());
    }

    private void insertEvent(CanonicalEvent event, String finalContent) {
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_event
                    (event_id, project_code, chapter_number, description, chapter_version,
                     content_hash, evidence_start_offset, evidence_end_offset, evidence_excerpt,
                     story_time, candidate_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE event_id = event_id
                """,
                event.eventId(), event.projectCode(), event.chapterNumber(), event.description(),
                event.sourceVersion().chapterVersion(), event.sourceVersion().contentHash(),
                event.evidenceRange().startOffset(), event.evidenceRange().endOffset(),
                event.evidenceRange().resolve(finalContent),
                event.storyTime(), event.candidateId());
    }

    private void insertFact(CanonicalFact fact) {
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_fact
                    (fact_id, project_code, proposition, source_ids_json, status, candidate_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    project_code = VALUES(project_code),
                    proposition = VALUES(proposition),
                    source_ids_json = VALUES(source_ids_json),
                    status = VALUES(status),
                    candidate_id = VALUES(candidate_id)
                """,
                fact.factId(), fact.projectCode(), fact.proposition(),
                serialize(fact.sourceIds()), fact.status().name(), fact.candidateId());
    }

    private void insertProjection(CanonicalProjection projection) {
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_projection
                    (projection_id, project_code, content, source_ids_json, status)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    project_code = VALUES(project_code),
                    content = VALUES(content),
                    source_ids_json = VALUES(source_ids_json),
                    status = VALUES(status)
                """,
                projection.projectionId(), projection.projectCode(), projection.content(),
                serialize(projection.sourceIds()), projection.status().name());
    }

    private void insertOutbox(MemoryOutboxEntry entry) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO memory_outbox
                    (outbox_id, commit_key, aggregate_type, aggregate_id, payload, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """,
                entry.outboxId(), entry.commitKey(), entry.aggregateType(),
                entry.aggregateId(), entry.payload());
    }

    private String serialize(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
