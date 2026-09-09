package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=${project.repository.volume.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${project.repository.volume.test.username:root}",
                "spring.datasource.password=${project.repository.volume.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class NovelProjectRepositoryVolumeTest {

    private static final String PROJECT_CODE = "project-repository-volume-test";

    @Autowired
    private NovelProjectRepository repository;
    @Autowired
    private INovelProjectDao novelProjectDao;
    @Autowired
    private IOutlineNodeDao outlineNodeDao;
    @Autowired
    private IChapterPlanDao chapterPlanDao;
    @Autowired
    private IStoryChapterDao storyChapterDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertFixture();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void shouldGroupChaptersByVolumeRange() {
        List<VolumeChapterGroupVO> groups = repository.findChaptersByVolume(PROJECT_CODE);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).volumeCode()).isEqualTo("volume-1");
        assertThat(groups.get(0).startChapter()).isEqualTo(1);
        assertThat(groups.get(0).endChapter()).isEqualTo(2);
        assertThat(groups.get(0).chapters())
                .extracting(chapter -> chapter.chapterNumber())
                .containsExactly(1, 2);
        assertThat(groups.get(1).volumeCode()).isEqualTo("volume-2");
        assertThat(groups.get(1).startChapter()).isEqualTo(3);
        assertThat(groups.get(1).endChapter()).isEqualTo(3);
        assertThat(groups.get(1).chapters())
                .extracting(chapter -> chapter.chapterNumber())
                .containsExactly(3);

        System.out.printf(
                "NovelProjectRepositoryVolumeTest grouped volume-1=%s volume-2=%s%n",
                groups.get(0).chapters().stream()
                        .map(chapter -> String.valueOf(chapter.chapterNumber()))
                        .toList(),
                groups.get(1).chapters().stream()
                        .map(chapter -> String.valueOf(chapter.chapterNumber()))
                        .toList());
    }

    private void insertFixture() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("按卷阅读测试");
        project.setGenre("玄幻");
        project.setTargetChapterCount(3);
        project.setWordsPerChapter(2000);
        project.setCurrentChapterNumber(3);
        project.setStatus("WRITING");
        novelProjectDao.insert(project);

        OutlineNodePO book = outlineNode(
                project.getId(), null, "book-1", "BOOK", 1, 1, 3, "全书", "全书大纲");
        outlineNodeDao.insert(book);
        OutlineNodePO volume1 = outlineNode(
                project.getId(), book.getId(), "volume-1", "VOLUME", 1, 1, 2,
                "第一卷", "第一卷大纲");
        outlineNodeDao.insert(volume1);
        OutlineNodePO volume2 = outlineNode(
                project.getId(), book.getId(), "volume-2", "VOLUME", 2, 3, 3,
                "第二卷", "第二卷大纲");
        outlineNodeDao.insert(volume2);

        OutlineNodePO arc1 = outlineNode(
                project.getId(), volume1.getId(), "arc-1", "ARC", 1, 1, 1,
                "第一卷剧情弧", "第一卷剧情");
        outlineNodeDao.insert(arc1);
        OutlineNodePO arc1Chapter2 = outlineNode(
                project.getId(), volume1.getId(), "arc-1-2", "ARC", 2, 2, 2,
                "第一卷第二章剧情弧", "第一卷第二章剧情");
        outlineNodeDao.insert(arc1Chapter2);
        OutlineNodePO arc2 = outlineNode(
                project.getId(), volume2.getId(), "arc-2", "ARC", 1, 3, 3,
                "第二卷剧情弧", "第二卷剧情");
        outlineNodeDao.insert(arc2);

        for (int chapterNumber = 1; chapterNumber <= 3; chapterNumber++) {
            Long outlineNodeId = chapterNumber == 1
                    ? arc1.getId()
                    : chapterNumber == 2 ? arc1Chapter2.getId() : arc2.getId();
            ChapterPlanPO plan = new ChapterPlanPO();
            plan.setProjectId(project.getId());
            plan.setOutlineNodeId(outlineNodeId);
            plan.setChapterNumber(chapterNumber);
            plan.setTitle("第" + chapterNumber + "章");
            plan.setSummary("第" + chapterNumber + "章计划");
            plan.setStatus("COMPLETED");
            chapterPlanDao.insert(plan);
            ChapterPlanPO savedPlan = chapterPlanDao.queryByChapterNumber(
                    project.getId(), chapterNumber);

            StoryChapterPO chapter = new StoryChapterPO();
            chapter.setProjectId(project.getId());
            chapter.setChapterPlanId(savedPlan.getId());
            chapter.setChapterNumber(chapterNumber);
            chapter.setTitle("第" + chapterNumber + "章");
            chapter.setContent("第" + chapterNumber + "章正文");
            chapter.setWordCount(7);
            chapter.setStatus("FINALIZED");
            storyChapterDao.insertOrUpdate(chapter);
        }
    }

    private OutlineNodePO outlineNode(
            Long projectId,
            Long parentId,
            String nodeCode,
            String nodeKind,
            int sequenceNo,
            int startChapter,
            int endChapter,
            String title,
            String summary) {
        OutlineNodePO node = new OutlineNodePO();
        node.setProjectId(projectId);
        node.setParentId(parentId);
        node.setNodeCode(nodeCode);
        node.setNodeKind(nodeKind);
        node.setSequenceNo(sequenceNo);
        node.setStartChapter(startChapter);
        node.setEndChapter(endChapter);
        node.setTitle(title);
        node.setSummary(summary);
        node.setStatus("COMPLETED");
        return node;
    }

    private void cleanUp() {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, PROJECT_CODE);
    }
}
