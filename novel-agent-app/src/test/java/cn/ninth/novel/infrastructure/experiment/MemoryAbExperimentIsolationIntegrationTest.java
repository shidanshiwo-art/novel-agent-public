package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 MySQL 验证 A/B 复制的业务与 Canonical 隔离边界。 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class MemoryAbExperimentIsolationIntegrationTest {

    private final String baselineProjectCode = "ab-fixture-" + UUID.randomUUID();
    private final String legacyProjectCode = baselineProjectCode + "-a";
    private final String v1ProjectCode = baselineProjectCode + "-b";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MemoryAbExperimentHarness harness;

    @AfterEach
    void cleanUp() {
        deleteProject(v1ProjectCode);
        deleteProject(legacyProjectCode);
        deleteProject(baselineProjectCode);
    }

    @Test
    void shouldCloneIndependentLegacyAndV1Environments() {
        createFixture();

        MemoryAbExperimentManifest manifest = harness.freezeBaseline(
                baselineProjectCode,
                legacyProjectCode,
                v1ProjectCode,
                1,
                10,
                11,
                16
        );
        MemoryAbIsolationReport report = harness.cloneEnvironments(manifest);

        MemoryAbIsolationReport.Environment legacy = environment(report, "A");
        MemoryAbIsolationReport.Environment v1 = environment(report, "B");

        System.out.printf(
                "A/B isolation: A(finalized=%d, canonical=%d), "
                        + "B(finalized=%d, canonical=%d/%d/%d), plans=%d/%d%n",
                legacy.finalizedChapterCount(), legacy.canonicalCommitCount(),
                v1.finalizedChapterCount(), v1.eventCount(), v1.factCount(),
                v1.projectionCount(), legacy.chapterPlanCount(), v1.chapterPlanCount());

        assertThat(legacy.finalizedChapterCount()).isEqualTo(10);
        assertThat(v1.finalizedChapterCount()).isEqualTo(10);
        assertThat(legacy.outlineCount()).isEqualTo(v1.outlineCount());
        assertThat(legacy.chapterPlanCount()).isEqualTo(16);
        assertThat(v1.chapterPlanCount()).isEqualTo(16);
        assertThat(legacy.canonicalCommitCount()).isZero();
        assertThat(legacy.acceptedVersionCount()).isZero();
        assertThat(legacy.eventCount()).isZero();
        assertThat(legacy.factCount()).isZero();
        assertThat(legacy.projectionCount()).isZero();
        assertThat(v1.canonicalCommitCount()).isEqualTo(1);
        assertThat(v1.acceptedVersionCount()).isEqualTo(1);
        assertThat(v1.eventCount()).isEqualTo(1);
        assertThat(v1.factCount()).isEqualTo(1);
        assertThat(v1.projectionCount()).isEqualTo(1);
        assertThat(legacy.generationMetricCount()).isZero();
        assertThat(legacy.retrievalMetricCount()).isZero();
        assertThat(legacy.promptTraceCount()).isZero();
        assertThat(v1.generationMetricCount()).isZero();
        assertThat(v1.retrievalMetricCount()).isZero();
        assertThat(v1.promptTraceCount()).isZero();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM memory_canonical_event
                WHERE project_code = ? AND event_id = 'event-fixture'
                """, Long.class, v1ProjectCode)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM memory_canonical_event
                WHERE project_code = ? AND event_id <> 'event-fixture'
                """, Long.class, v1ProjectCode)).isEqualTo(1);
    }

    private MemoryAbIsolationReport.Environment environment(
            MemoryAbIsolationReport report,
            String group
    ) {
        return report.environments().stream()
                .filter(value -> group.equals(value.group()))
                .findFirst()
                .orElseThrow();
    }

    private void createFixture() {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count, words_per_chapter,
                     current_chapter_number, status)
                VALUES (?, 'A/B fixture', '悬疑', 80, 1200, 10, 'WRITING')
                """, baselineProjectCode);
        Long projectId = projectId(baselineProjectCode);
        jdbcTemplate.update("""
                INSERT INTO story_bible
                    (project_id, one_sentence_premise, core_theme, main_conflict,
                     ending_direction, world_background, hard_rules_json, style_guide, status)
                VALUES (?, '一块残晶牵出旧城真相。', '记忆与选择', '主角追查残晶来源。',
                        '真相被确认。', '城市存在不可逆的记忆损耗。',
                        CAST('[\"死者不能复生\"]' AS JSON), '克制悬疑。', 'CONFIRMED')
                """, projectId);
        jdbcTemplate.update("""
                INSERT INTO story_character
                    (project_id, character_code, name, role_type, gender, status)
                VALUES (?, 'CHAR_001', '林舟', 'MALE_LEAD', 'MALE', 'ACTIVE')
                """, projectId);

        jdbcTemplate.update("""
                INSERT INTO outline_node
                    (project_id, parent_id, node_code, node_kind, sequence_no,
                     title, summary, status)
                VALUES (?, NULL, 'BOOK_001', 'BOOK', 1, '全书', '残晶真相主线。', 'READY')
                """, projectId);
        Long bookId = jdbcTemplate.queryForObject("""
                SELECT id FROM outline_node WHERE project_id = ? AND node_code = 'BOOK_001'
                """, Long.class, projectId);
        for (int chapter = 1; chapter <= 16; chapter++) {
            String status = chapter <= 10 ? "COMPLETED" : "READY";
            String code = "ARC_" + String.format("%03d", chapter);
            jdbcTemplate.update("""
                    INSERT INTO outline_node
                        (project_id, parent_id, node_code, node_kind, sequence_no,
                         start_chapter, end_chapter, title, summary, status)
                    VALUES (?, ?, ?, 'ARC', ?, ?, ?, ?, ?, ?)
                    """, projectId, bookId, code, chapter, chapter, chapter,
                    "第" + chapter + "章", "第" + chapter + "章章纲。", status);
            Long arcId = jdbcTemplate.queryForObject("""
                    SELECT id FROM outline_node WHERE project_id = ? AND node_code = ?
                    """, Long.class, projectId, code);
            jdbcTemplate.update("""
                    INSERT INTO chapter_plan
                        (project_id, outline_node_id, chapter_number, title, summary, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, projectId, arcId, chapter, "第" + chapter + "章",
                    "第" + chapter + "章章纲。", status);
        }

        for (int chapter = 1; chapter <= 10; chapter++) {
            Long planId = jdbcTemplate.queryForObject("""
                    SELECT id FROM chapter_plan WHERE project_id = ? AND chapter_number = ?
                    """, Long.class, projectId, chapter);
            String content = "第" + chapter + "章正文：林舟记录残晶位置。";
            jdbcTemplate.update("""
                    INSERT INTO story_chapter
                        (project_id, chapter_plan_id, chapter_number, title, content, word_count, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'FINALIZED')
                    """, projectId, planId, chapter, "第" + chapter + "章", content,
                    content.length());
            Long chapterId = jdbcTemplate.queryForObject("""
                    SELECT id FROM story_chapter WHERE project_id = ? AND chapter_number = ?
                    """, Long.class, projectId, chapter);
            jdbcTemplate.update("""
                    INSERT INTO story_summary
                        (project_id, chapter_id, chapter_number, short_summary,
                         key_events_json, unresolved_questions_json, ending_hook,
                         story_state_snapshot_json, status)
                    VALUES (?, ?, ?, '本章记录残晶位置。', CAST('[\"残晶位置被记录\"]' AS JSON),
                            CAST('[\"残晶来源未明\"]' AS JSON), '线索继续。',
                            CAST('{\"resources\":[\"残晶\"]}' AS JSON), 'GENERATED')
                    """, projectId, chapterId, chapter);
        }

        String content = "第10章正文：林舟确认残晶仍在手中。";
        String hash = sha256(content);
        jdbcTemplate.update("""
                INSERT INTO memory_commit
                    (commit_key, project_code, chapter_number, chapter_version, content_hash, final_content)
                VALUES ('commit-fixture', ?, 10, 'source-version-fixture', ?, ?)
                """, baselineProjectCode, hash, content);
        jdbcTemplate.update("""
                INSERT INTO memory_accepted_chapter_version
                    (commit_key, project_code, chapter_number, chapter_version, content_hash, final_content)
                VALUES ('commit-fixture', ?, 10, 'source-version-fixture', ?, ?)
                """, baselineProjectCode, hash, content);
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_event
                    (event_id, project_code, chapter_number, description, chapter_version,
                     content_hash, evidence_start_offset, evidence_end_offset,
                     evidence_excerpt, story_time, candidate_id)
                VALUES ('event-fixture', ?, 10, '林舟确认残晶位置。', 'source-version-fixture',
                        ?, 0, ?, ?, '第十日', 'candidate-fixture')
                """, baselineProjectCode, hash, content.length(), content);
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_fact
                    (fact_id, project_code, proposition, source_ids_json, status, candidate_id)
                VALUES ('fact-fixture', ?, '残晶当前在林舟手中。',
                        CAST('[\"candidate-fixture\"]' AS JSON), 'FACT_ACTIVE', 'candidate-fixture')
                """, baselineProjectCode);
        jdbcTemplate.update("""
                INSERT INTO memory_canonical_projection
                    (projection_id, project_code, content, source_ids_json, status)
                VALUES ('projection-fixture', ?, '残晶位置已经确认。',
                        CAST('[\"event-fixture\",\"fact-fixture\"]' AS JSON), 'PROJECTION_ACTIVE')
                """, baselineProjectCode);
        jdbcTemplate.update("""
                INSERT INTO memory_outbox
                    (outbox_id, commit_key, aggregate_type, aggregate_id, payload, status)
                VALUES (?, 'commit-fixture', 'EVENT', 'event-fixture',
                        '{\"projectCode\":\"fixture\",\"eventId\":\"event-fixture\"}', 'DONE')
                """, sha256("outbox-fixture"));
    }

    private Long projectId(String projectCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?", Long.class, projectCode);
    }

    private void deleteProject(String projectCode) {
        if (projectCode == null) {
            return;
        }
        MapProject project = findProject(projectCode);
        if (project == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM memory_outbox WHERE commit_key IN "
                + "(SELECT commit_key FROM memory_commit WHERE project_code = ?)", projectCode);
        jdbcTemplate.update("DELETE FROM memory_canonical_projection WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_canonical_fact WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_canonical_event WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_accepted_chapter_version WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_commit WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM memory_retrieval_metrics WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM chapter_model_trace WHERE project_code = ?", projectCode);
        jdbcTemplate.update("DELETE FROM generation_metrics WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM story_summary WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM story_chapter WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM chapter_plan WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM outline_node WHERE project_id = ? AND node_kind = 'ARC'", project.id());
        jdbcTemplate.update("DELETE FROM outline_node WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM story_character WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM story_bible WHERE project_id = ?", project.id());
        jdbcTemplate.update("DELETE FROM novel_project WHERE id = ?", project.id());
    }

    private MapProject findProject(String projectCode) {
        List<MapProject> rows = jdbcTemplate.query("SELECT id FROM novel_project WHERE project_code = ?",
                (resultSet, rowNum) -> new MapProject(resultSet.getLong("id")), projectCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record MapProject(Long id) {
    }
}
