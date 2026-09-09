package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.Application;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=${chapter.plan.mapper.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${chapter.plan.mapper.test.username:root}",
                "spring.datasource.password=${chapter.plan.mapper.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class ChapterPlanMapperIntegrationTest {

    private final String projectCode = "chapter-plan-mapper-test-" + UUID.randomUUID();

    @Autowired
    private IChapterPlanDao chapterPlanDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long projectId;
    private Long outlineNodeId;

    @BeforeEach
    void setUpFixtures() {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count,
                     words_per_chapter, current_chapter_number, status)
                VALUES (?, 'Chapter plan mapper test', '玄幻', 10, 2000, 0, 'DRAFT')
                """, projectCode);
        projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?", Long.class, projectCode);
        jdbcTemplate.update("""
                INSERT INTO outline_node
                    (project_id, parent_id, node_code, node_kind, sequence_no,
                     start_chapter, end_chapter, title, summary, status)
                VALUES (?, NULL, ?, 'BOOK', 1, 1, 10, '测试全书', '章节计划测试作品', 'READY')
                """, projectId, "arc-" + UUID.randomUUID());
        Long bookId = jdbcTemplate.queryForObject(
                "SELECT id FROM outline_node WHERE project_id = ?", Long.class, projectId);
        jdbcTemplate.update("""
                INSERT INTO outline_node
                    (project_id, parent_id, node_code, node_kind, sequence_no,
                     start_chapter, end_chapter, title, summary, status)
                VALUES (?, ?, ?, 'ARC', 1, 1, 10, '测试剧情弧', '章节计划测试节点', 'READY')
                """, projectId, bookId, "arc-" + UUID.randomUUID());
        outlineNodeId = jdbcTemplate.queryForObject(
                "SELECT id FROM outline_node WHERE project_id = ? AND parent_id = ?",
                Long.class, projectId, bookId);
    }

    @AfterEach
    void cleanUpFixtures() {
        TestProjectDataCleanup.deleteProject(jdbcTemplate, projectId);
    }

    @Test
    void shouldInsertQueryUpdateStatusAndDeleteOneChapterPlan() {
        List<ChapterPlanPO> plans = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(this::plan)
                .toList();

        plans.forEach(plan -> assertEquals(1, chapterPlanDao.insert(plan)));
        assertEquals(10, chapterPlanDao.queryByOutlineNode(projectId, outlineNodeId).size());
        assertEquals(
                java.util.stream.IntStream.rangeClosed(1, 10).boxed().toList(),
                chapterPlanDao.queryByOutlineNode(projectId, outlineNodeId).stream()
                        .map(ChapterPlanPO::getChapterNumber)
                        .toList());
        assertEquals("第5章", chapterPlanDao.queryByChapterNumber(projectId, 5).getTitle());
        assertEquals(10, chapterPlanDao.queryByProject(projectId).size());

        ChapterPlanPO chapterPlan = chapterPlanDao.queryByChapterNumber(projectId, 5);
        chapterPlan.setTitle("第五章（修订）");
        chapterPlan.setSummary("修订后的章节计划");
        assertEquals(1, chapterPlanDao.update(chapterPlan));
        assertEquals("第五章（修订）", chapterPlanDao.queryByChapterNumber(projectId, 5).getTitle());
        assertEquals("修订后的章节计划", chapterPlanDao.queryByChapterNumber(projectId, 5).getSummary());

        assertEquals(1, chapterPlanDao.updateStatus(projectId, chapterPlan.getId(), "READY"));
        assertEquals("READY", chapterPlanDao.queryByChapterNumber(projectId, 5).getStatus());
        assertNotNull(chapterPlan.getId());

        assertEquals(1, chapterPlanDao.delete(projectId, chapterPlan.getId()));
        assertEquals(9, chapterPlanDao.queryByOutlineNode(projectId, outlineNodeId).size());
    }

    private ChapterPlanPO plan(int chapterNumber) {
        ChapterPlanPO plan = new ChapterPlanPO();
        plan.setProjectId(projectId);
        plan.setOutlineNodeId(outlineNodeId);
        plan.setChapterNumber(chapterNumber);
        plan.setTitle("第" + chapterNumber + "章");
        plan.setSummary("第" + chapterNumber + "章计划");
        plan.setStatus("PLANNED");
        return plan;
    }
}
