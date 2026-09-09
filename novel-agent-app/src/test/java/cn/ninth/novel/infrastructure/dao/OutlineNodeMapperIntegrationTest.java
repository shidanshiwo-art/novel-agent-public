package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.Application;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
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
                "spring.datasource.url=${outline.mapper.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${outline.mapper.test.username:root}",
                "spring.datasource.password=${outline.mapper.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class OutlineNodeMapperIntegrationTest {

    private final String projectCode = "outline-mapper-test-" + UUID.randomUUID();

    @Autowired
    private IOutlineNodeDao outlineNodeDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long projectId;

    @BeforeEach
    void setUpProject() {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count,
                     words_per_chapter, current_chapter_number, status)
                VALUES (?, 'Mapper test', '玄幻', 100, 2000, 0, 'DRAFT')
                """, projectCode);
        projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?",
                Long.class,
                projectCode);
    }

    @AfterEach
    void cleanUpProject() {
        TestProjectDataCleanup.deleteProject(jdbcTemplate, projectId);
    }

    @Test
    void shouldInsertQueryUpdateAndDeleteOutlineTreeNodes() {
        OutlineNodePO root = node(null, "book-1", "BOOK", 1, "全书", "全书大纲");
        assertEquals(1, outlineNodeDao.insert(root));
        assertNotNull(root.getId());

        OutlineNodePO child = node(root.getId(), "volume-1", "VOLUME", 1, "第一卷", "卷大纲");
        assertEquals(1, outlineNodeDao.insert(child));
        assertNotNull(child.getId());

        assertEquals(root.getId(), outlineNodeDao.queryRoot(projectId).getId());
        assertEquals(root.getId(), outlineNodeDao.queryByCode(projectId, "book-1").getId());
        assertEquals(child.getId(), outlineNodeDao.queryById(projectId, child.getId()).getId());
        assertEquals(2, outlineNodeDao.queryByProject(projectId).size());
        assertEquals(1, outlineNodeDao.queryChildren(projectId, root.getId()).size());
        assertEquals(1, outlineNodeDao.queryMaxSequenceNo(projectId, root.getId()));

        child.setTitle("第一卷（修订）");
        child.setSummary("修订后的卷大纲");
        assertEquals(1, outlineNodeDao.update(child));
        assertEquals("第一卷（修订）", outlineNodeDao.queryById(projectId, child.getId()).getTitle());
        assertEquals("修订后的卷大纲", outlineNodeDao.queryById(projectId, child.getId()).getSummary());

        assertEquals(1, outlineNodeDao.deleteOne(projectId, child.getId()));
        assertTrue(outlineNodeDao.queryChildren(projectId, root.getId()).isEmpty());
    }

    private OutlineNodePO node(Long parentId, String code, String kind, int sequence, String title, String summary) {
        OutlineNodePO node = new OutlineNodePO();
        node.setProjectId(projectId);
        node.setParentId(parentId);
        node.setNodeCode(code);
        node.setNodeKind(kind);
        node.setSequenceNo(sequence);
        node.setStartChapter(1);
        node.setEndChapter(100);
        node.setTitle(title);
        node.setSummary(summary);
        node.setStatus("PLANNED");
        return node;
    }
}
