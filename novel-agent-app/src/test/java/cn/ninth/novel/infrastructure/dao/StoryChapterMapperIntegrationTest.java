package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.Application;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=${story.chapter.mapper.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${story.chapter.mapper.test.username:root}",
                "spring.datasource.password=${story.chapter.mapper.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class StoryChapterMapperIntegrationTest {

    private final String projectCode = "story-chapter-mapper-test-" + UUID.randomUUID();

    @Autowired
    private IStoryChapterDao storyChapterDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long projectId;
    private Long chapterPlanId;

    @BeforeEach
    void setUpFixtures() {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count,
                     words_per_chapter, current_chapter_number, status)
                VALUES (?, 'Story chapter mapper test', '玄幻', 1, 2000, 0, 'DRAFT')
                """, projectCode);
        projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?", Long.class, projectCode);
        Long outlineNodeId = insertOutlineNode();
        jdbcTemplate.update("""
                INSERT INTO chapter_plan
                    (project_id, outline_node_id, chapter_number, title, summary, status)
                VALUES (?, ?, 1, '第一章', '第一章计划', 'READY')
                """, projectId, outlineNodeId);
        chapterPlanId = jdbcTemplate.queryForObject("""
                SELECT id FROM chapter_plan
                WHERE project_id = ? AND chapter_number = 1
                """, Long.class, projectId);
    }

    @AfterEach
    void cleanUpFixtures() {
        TestProjectDataCleanup.deleteProject(jdbcTemplate, projectId);
    }

    @Test
    void shouldPersistAndReadChapterUsingChapterPlanId() {
        StoryChapterPO chapter = chapter("第一版正文");
        assertTrue(storyChapterDao.insertOrUpdate(chapter) > 0);
        assertNotNull(chapter.getId());

        StoryChapterPO stored = storyChapterDao.queryByProjectIdAndChapterNumber(projectId, 1);
        assertEquals(chapterPlanId, stored.getChapterPlanId());
        assertEquals("第一版正文", stored.getContent());
        assertEquals(1, storyChapterDao.queryByProjectId(projectId).size());
        assertEquals(1, storyChapterDao.queryByProjectIdAndContentLike(projectId, "第一版", 10).size());
        assertEquals(1, storyChapterDao.countByProjectId(projectId));

        chapter.setContent("修订后的正文");
        chapter.setWordCount(6);
        assertTrue(storyChapterDao.insertOrUpdate(chapter) > 0);
        assertEquals("修订后的正文",
                storyChapterDao.queryByProjectIdAndChapterNumber(projectId, 1).getContent());

        assertEquals(1, storyChapterDao.deleteByProjectIdAndChapterNumber(projectId, 1));
        assertEquals(0, storyChapterDao.countByProjectId(projectId));
    }

    private Long insertOutlineNode() {
        jdbcTemplate.update("""
                INSERT INTO outline_node
                    (project_id, parent_id, node_code, node_kind, sequence_no,
                     start_chapter, end_chapter, title, summary, status)
                VALUES (?, NULL, ?, 'BOOK', 1, 1, 1, '测试全书', '正文测试作品', 'READY')
                """, projectId, "arc-" + UUID.randomUUID());
        Long bookId = jdbcTemplate.queryForObject(
                "SELECT id FROM outline_node WHERE project_id = ?", Long.class, projectId);
        jdbcTemplate.update("""
                INSERT INTO outline_node
                    (project_id, parent_id, node_code, node_kind, sequence_no,
                     start_chapter, end_chapter, title, summary, status)
                VALUES (?, ?, ?, 'ARC', 1, 1, 1, '测试剧情弧', '正文测试节点', 'READY')
                """, projectId, bookId, "arc-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outline_node WHERE project_id = ? AND parent_id = ?",
                Long.class, projectId, bookId);
    }

    private StoryChapterPO chapter(String content) {
        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(projectId);
        chapter.setChapterPlanId(chapterPlanId);
        chapter.setChapterNumber(1);
        chapter.setTitle("第一章");
        chapter.setContent(content);
        chapter.setWordCount(content.length());
        chapter.setStatus("FINALIZED");
        return chapter;
    }
}
