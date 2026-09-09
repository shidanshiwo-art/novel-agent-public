package cn.ninth.novel.infrastructure.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Cleans project fixtures according to the foreign-key dependency graph.
 *
 * <p>This helper is test-only. It deliberately removes outline nodes from
 * leaves to roots so the self-referencing outline foreign key is exercised
 * normally instead of being disabled during teardown.</p>
 */
public final class TestProjectDataCleanup {

    private TestProjectDataCleanup() {
    }

    public static void deleteProjectByCode(JdbcTemplate jdbcTemplate, String projectCode) {
        Long projectId = jdbcTemplate.query(
                "SELECT id FROM novel_project WHERE project_code = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                projectCode);
        if (projectId != null) {
            deleteProject(jdbcTemplate, projectId);
        }
    }

    public static void deleteProject(JdbcTemplate jdbcTemplate, Long projectId) {
        if (projectId == null) {
            return;
        }

        // generation_metrics、story_summary -> story_chapter -> chapter_plan -> outline_node
        jdbcTemplate.update("DELETE FROM generation_metrics WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM story_summary WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM story_chapter WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM chapter_plan WHERE project_id = ?", projectId);
        deleteOutlineNodesBottomUp(jdbcTemplate, projectId);

        // The remaining project children have no dependency on one another.
        jdbcTemplate.update("DELETE FROM story_character WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM story_bible WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM novel_project WHERE id = ?", projectId);
    }

    private static void deleteOutlineNodesBottomUp(JdbcTemplate jdbcTemplate, Long projectId) {
        int deleted;
        do {
            List<Long> leafIds = jdbcTemplate.queryForList("""
                    SELECT child.id
                    FROM outline_node child
                    WHERE child.project_id = ?
                      AND child.parent_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1
                          FROM outline_node descendant
                          WHERE descendant.project_id = child.project_id
                            AND descendant.parent_id = child.id
                      )
                    """, Long.class, projectId);
            deleted = 0;
            for (Long leafId : leafIds) {
                deleted += jdbcTemplate.update(
                        "DELETE FROM outline_node WHERE project_id = ? AND id = ?",
                        projectId, leafId);
            }
        } while (deleted > 0);

        jdbcTemplate.update(
                "DELETE FROM outline_node WHERE project_id = ? AND parent_id IS NULL",
                projectId);
    }
}
