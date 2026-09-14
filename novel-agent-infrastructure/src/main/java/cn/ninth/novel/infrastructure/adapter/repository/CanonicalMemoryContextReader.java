package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical Memory 的只读运行时适配器。
 *
 * <p>Canonical 表不是 Prompt DTO：这里负责 accepted source version、章节时间边界、
 * 生命周期和来源元数据的持久化过滤，再转换成领域层的最小 Context item。Fact 和
 * Projection 本身没有章节列，使用 Canonical Commit 关联的 accepted source 作为其
 * 物化章节/版本；没有关联时保留候选但不猜测版本，避免把数据库结构泄漏给上层。</p>
 */
@Component
final class CanonicalMemoryContextReader {

    private static final String EVENT_SOURCE_TYPE = "CANONICAL_EVENT";
    private static final String FACT_SOURCE_TYPE = "CANONICAL_FACT";
    private static final String PROJECTION_SOURCE_TYPE = "CANONICAL_PROJECTION";

    private static final String FIND_ACCEPTED_EVENTS = """
            SELECT e.event_id, e.chapter_number, e.description, e.chapter_version,
                   e.story_time
            FROM memory_canonical_event e
            INNER JOIN memory_accepted_chapter_version accepted
                ON accepted.project_code = e.project_code
               AND accepted.chapter_number = e.chapter_number
               AND accepted.chapter_version = e.chapter_version
               AND accepted.content_hash = e.content_hash
            WHERE e.project_code = ?
              AND e.chapter_number < ?
            ORDER BY e.chapter_number ASC, e.event_id ASC
            """;

    private static final String FIND_FACTS = """
            SELECT f.fact_id, f.proposition, f.source_ids_json, f.status,
                   COALESCE((
                       SELECT c.chapter_number
                       FROM memory_outbox o
                       INNER JOIN memory_commit c ON c.commit_key = o.commit_key
                       INNER JOIN memory_accepted_chapter_version accepted
                           ON accepted.commit_key = c.commit_key
                       WHERE o.aggregate_type = 'FACT'
                         AND o.aggregate_id = f.fact_id
                         AND c.project_code = f.project_code
                       ORDER BY c.created_at DESC, o.outbox_id DESC
                       LIMIT 1
                   ), 0) AS source_chapter,
                   (
                       SELECT c.chapter_version
                       FROM memory_outbox o
                       INNER JOIN memory_commit c ON c.commit_key = o.commit_key
                       INNER JOIN memory_accepted_chapter_version accepted
                           ON accepted.commit_key = c.commit_key
                       WHERE o.aggregate_type = 'FACT'
                         AND o.aggregate_id = f.fact_id
                         AND c.project_code = f.project_code
                       ORDER BY c.created_at DESC, o.outbox_id DESC
                       LIMIT 1
                   ) AS source_version
            FROM memory_canonical_fact f
            WHERE f.project_code = ?
              AND f.status IN ('FACT_ACTIVE', 'FACT_ARCHIVED')
            ORDER BY source_chapter ASC, f.fact_id ASC
            """;

    private static final String FIND_PROJECTIONS = """
            SELECT p.projection_id, p.content, p.source_ids_json, p.status,
                   COALESCE((
                       SELECT c.chapter_number
                       FROM memory_outbox o
                       INNER JOIN memory_commit c ON c.commit_key = o.commit_key
                       INNER JOIN memory_accepted_chapter_version accepted
                           ON accepted.commit_key = c.commit_key
                       WHERE o.aggregate_type = 'PROJECTION'
                         AND o.aggregate_id = p.projection_id
                         AND c.project_code = p.project_code
                       ORDER BY c.created_at DESC, o.outbox_id DESC
                       LIMIT 1
                   ), 0) AS source_chapter,
                   (
                       SELECT c.chapter_version
                       FROM memory_outbox o
                       INNER JOIN memory_commit c ON c.commit_key = o.commit_key
                       INNER JOIN memory_accepted_chapter_version accepted
                           ON accepted.commit_key = c.commit_key
                       WHERE o.aggregate_type = 'PROJECTION'
                         AND o.aggregate_id = p.projection_id
                         AND c.project_code = p.project_code
                       ORDER BY c.created_at DESC, o.outbox_id DESC
                       LIMIT 1
                   ) AS source_version
            FROM memory_canonical_projection p
            WHERE p.project_code = ?
              AND p.status IN (
                  'PROJECTION_ACTIVE', 'PROJECTION_STALE', 'PROJECTION_ARCHIVED'
              )
            ORDER BY source_chapter ASC, p.projection_id ASC
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    CanonicalMemoryContextReader(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    List<MemoryContextItem> find(String projectCode, Integer beforeChapter) {
        if (projectCode == null || projectCode.isBlank() || beforeChapter == null) {
            return List.of();
        }

        List<EventRow> events = jdbcTemplate.query(
                FIND_ACCEPTED_EVENTS,
                (resultSet, rowNumber) -> new EventRow(
                        resultSet.getString("event_id"),
                        resultSet.getInt("chapter_number"),
                        resultSet.getString("description"),
                        resultSet.getString("chapter_version"),
                        resultSet.getString("story_time")),
                projectCode.trim(), beforeChapter);
        Map<String, SourceMetadata> eventMetadata = new HashMap<>();
        List<MemoryContextItem> items = new ArrayList<>();
        for (EventRow event : events) {
            SourceMetadata metadata = new SourceMetadata(
                    event.sourceChapter(), event.sourceVersion(), event.storyTime(),
                    event.sourceChapter());
            eventMetadata.put(event.sourceId(), metadata);
            items.add(MemoryContextItem.canonical(
                    EVENT_SOURCE_TYPE,
                    event.sourceId(),
                    MemoryContextCategory.EPISODES,
                    event.content(),
                    event.sourceChapter(),
                    event.sourceVersion(),
                    "ACCEPTED",
                    event.storyTime(),
                    event.sourceChapter()));
        }

        List<FactRow> facts = jdbcTemplate.query(
                FIND_FACTS,
                (resultSet, rowNumber) -> new FactRow(
                        resultSet.getString("fact_id"),
                        resultSet.getString("proposition"),
                        resultSet.getString("source_ids_json"),
                        resultSet.getString("status"),
                        resultSet.getInt("source_chapter"),
                        resultSet.getString("source_version")),
                projectCode.trim());
        for (FactRow fact : facts) {
            SourceMetadata metadata = enrichMetadata(
                    fact.sourceChapter(), fact.sourceVersion(), fact.sourceIdsJson(), eventMetadata);
            if (isFuture(metadata.sourceChapter(), beforeChapter)) {
                continue;
            }
            items.add(MemoryContextItem.canonical(
                    FACT_SOURCE_TYPE,
                    fact.sourceId(),
                    MemoryContextCategory.CURRENT_STATES,
                    fact.content(),
                    metadata.sourceChapter(),
                    metadata.sourceVersion(),
                    fact.status(),
                    metadata.storyTime(),
                    metadata.order()));
        }

        List<ProjectionRow> projections = jdbcTemplate.query(
                FIND_PROJECTIONS,
                (resultSet, rowNumber) -> new ProjectionRow(
                        resultSet.getString("projection_id"),
                        resultSet.getString("content"),
                        resultSet.getString("source_ids_json"),
                        resultSet.getString("status"),
                        resultSet.getInt("source_chapter"),
                        resultSet.getString("source_version")),
                projectCode.trim());
        for (ProjectionRow projection : projections) {
            SourceMetadata metadata = enrichMetadata(
                    projection.sourceChapter(), projection.sourceVersion(),
                    projection.sourceIdsJson(), eventMetadata);
            if (isFuture(metadata.sourceChapter(), beforeChapter)) {
                continue;
            }
            items.add(MemoryContextItem.canonical(
                    PROJECTION_SOURCE_TYPE,
                    projection.sourceId(),
                    projectionCategory(projection.sourceId()),
                    projection.content(),
                    metadata.sourceChapter(),
                    metadata.sourceVersion(),
                    projection.status(),
                    metadata.storyTime(),
                    metadata.order()));
        }

        return items.stream()
                .sorted(Comparator
                        .comparingInt(MemoryContextItem::sourceChapter)
                        .thenComparing(item -> item.order() == null ? Integer.MAX_VALUE : item.order())
                        .thenComparing(MemoryContextItem::sourceType,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(MemoryContextItem::itemId))
                .toList();
    }

    private MemoryContextCategory projectionCategory(String projectionId) {
        return projectionId != null && projectionId.startsWith("runtime:open-loop:")
                ? MemoryContextCategory.OPEN_LOOPS
                : MemoryContextCategory.CONSOLIDATED;
    }

    private SourceMetadata enrichMetadata(
            int sourceChapter,
            String sourceVersion,
            String sourceIdsJson,
            Map<String, SourceMetadata> eventMetadata
    ) {
        if (sourceChapter > 0 || sourceVersion != null) {
            return new SourceMetadata(sourceChapter, sourceVersion, null, sourceChapter);
        }
        for (String sourceId : sourceIds(sourceIdsJson)) {
            SourceMetadata event = eventMetadata.get(sourceId);
            if (event != null) {
                return event;
            }
        }
        return new SourceMetadata(0, null, null, null);
    }

    private List<String> sourceIds(String sourceIdsJson) {
        if (sourceIdsJson == null || sourceIdsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(sourceIdsJson);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            root.forEach(node -> {
                if (node != null && node.isTextual() && !node.asText().isBlank()) {
                    values.add(node.asText().trim());
                }
            });
            return List.copyOf(values);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private boolean isFuture(int sourceChapter, int beforeChapter) {
        return sourceChapter > 0 && sourceChapter >= beforeChapter;
    }

    private record EventRow(
            String sourceId,
            int sourceChapter,
            String content,
            String sourceVersion,
            String storyTime
    ) {
    }

    private record FactRow(
            String sourceId,
            String content,
            String sourceIdsJson,
            String status,
            int sourceChapter,
            String sourceVersion
    ) {
    }

    private record ProjectionRow(
            String sourceId,
            String content,
            String sourceIdsJson,
            String status,
            int sourceChapter,
            String sourceVersion
    ) {
    }

    private record SourceMetadata(
            int sourceChapter,
            String sourceVersion,
            String storyTime,
            Integer order
    ) {
    }
}
