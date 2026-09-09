package cn.ninth.novel.infrastructure.dao;

import cn.ninth.novel.Application;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryBiblePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class MvpMapperIntegrationTest {

    private static final String PROJECT_CODE = "mapper-integration-story";
    private static final String OTHER_PROJECT_CODE = "mapper-integration-other-story";

    @Autowired
    private INovelProjectDao novelProjectDao;
    @Autowired
    private IStoryBibleDao storyBibleDao;
    @Autowired
    private IStoryCharacterDao storyCharacterDao;
    @Autowired
    private IOutlineNodeDao outlineNodeDao;
    @Autowired
    private IStoryChapterDao storyChapterDao;
    @Autowired
    private IStorySummaryDao storySummaryDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        cleanUpProject(PROJECT_CODE);
        cleanUpProject(OTHER_PROJECT_CODE);
    }

    private void cleanUpProject(String projectCode) {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, projectCode);
    }

    @Test
    void shouldPersistAndQueryAllMvpAggregates() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("北境夜行");
        project.setGenre("玄幻");
        project.setTargetChapterCount(100);
        project.setWordsPerChapter(2500);
        project.setCurrentChapterNumber(0);
        project.setStatus("DRAFT");
        assertEquals(1, novelProjectDao.insert(project));
        assertNotNull(project.getId());

        StoryBiblePO bible = new StoryBiblePO();
        bible.setProjectId(project.getId());
        bible.setOneSentencePremise("林渊带着残图调查父亲失踪真相。");
        bible.setPowerSystemJson("{\"realms\":[\"启灵\",\"凝光\"]}");
        bible.setHardRulesJson("[\"影步不能在强光下使用\"]");
        bible.setStatus("CONFIRMED");
        assertEquals(1, storyBibleDao.insert(bible));

        StoryCharacterPO protagonist = character(project.getId(), "lin-yuan", "林渊", "MALE_LEAD");
        assertEquals(1, storyCharacterDao.insert(protagonist));

        OutlineNodePO outlineNode = outlineNode(project.getId(), "arc-1", 1, "午夜钟楼");
        assertEquals(1, outlineNodeDao.insert(outlineNode));

        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(project.getId());
        chapter.setChapterPlanId(outlineNode.getId());
        chapter.setChapterNumber(1);
        chapter.setTitle("午夜钟楼");
        chapter.setContent("林渊在午夜进入钟楼。段落一。段落二。");
        chapter.setWordCount(20);
        chapter.setStatus("FINALIZED");
        assertEquals(1, storyChapterDao.insertOrUpdate(chapter));
        assertNotNull(chapter.getId());

        StorySummaryPO summary = new StorySummaryPO();
        summary.setProjectId(project.getId());
        summary.setChapterId(chapter.getId());
        summary.setChapterNumber(1);
        summary.setShortSummary("林渊进入钟楼并取得残页。");
        summary.setKeyEventsJson("[\"获得残页\"]");
        summary.setUnresolvedQuestionsJson("[\"议会为何追查父亲\"]");
        summary.setEndingHook("残页出现议会徽记");
        summary.setStatus("VERIFIED");
        assertEquals(1, storySummaryDao.insertOrUpdate(summary));

        assertEquals("北境夜行", novelProjectDao.queryByProjectCode(PROJECT_CODE).getTitle());
        assertEquals("CONFIRMED", storyBibleDao.queryByProjectId(project.getId()).getStatus());
        assertEquals("林渊", storyCharacterDao.queryMaleLead(project.getId()).getName());
        assertEquals(1, storyCharacterDao.queryByProjectId(project.getId()).size());
        assertEquals("午夜钟楼", outlineNodeDao.queryByCode(project.getId(), "arc-1").getTitle());
        assertEquals(1, outlineNodeDao.queryByProject(project.getId()).size());
        assertEquals("FINALIZED", storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1).getStatus());
        assertEquals("[\"获得残页\"]", storySummaryDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1).getKeyEventsJson());
        assertEquals(1, storySummaryDao.queryRecent(project.getId(), 2, 3).size());
    }

    @Test
    void shouldRejectSecondMaleLeadForOneProject() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("单男主约束测试");
        project.setGenre("都市");
        project.setWordsPerChapter(2000);
        project.setCurrentChapterNumber(0);
        project.setStatus("DRAFT");
        novelProjectDao.insert(project);

        storyCharacterDao.insert(character(project.getId(), "lead-1", "林渊", "MALE_LEAD"));

        assertThrows(
                DuplicateKeyException.class,
                () -> storyCharacterDao.insert(
                        character(project.getId(), "lead-2", "陈锋", "MALE_LEAD")));
    }

    @Test
    void shouldRejectChapterPlanAndSummaryFromAnotherProject() {
        NovelProjectPO first = project(PROJECT_CODE);
        NovelProjectPO second = project(OTHER_PROJECT_CODE);
        novelProjectDao.insert(first);
        novelProjectDao.insert(second);

        OutlineNodePO secondProjectNode = outlineNode(
                second.getId(), "arc-1", 1, "异项目节点"
        );
        outlineNodeDao.insert(secondProjectNode);

        StoryChapterPO invalidChapter = new StoryChapterPO();
        invalidChapter.setProjectId(first.getId());
        invalidChapter.setChapterPlanId(secondProjectNode.getId());
        invalidChapter.setChapterNumber(1);
        invalidChapter.setTitle("错误章节");
        invalidChapter.setContent("不应写入");
        invalidChapter.setWordCount(4);
        invalidChapter.setStatus("FINALIZED");
        assertThrows(DataIntegrityViolationException.class,
                () -> storyChapterDao.insertOrUpdate(invalidChapter));

        StoryChapterPO validChapter = new StoryChapterPO();
        validChapter.setProjectId(second.getId());
        validChapter.setChapterPlanId(secondProjectNode.getId());
        validChapter.setChapterNumber(1);
        validChapter.setTitle("正确章节");
        validChapter.setContent("属于第二个项目");
        validChapter.setWordCount(7);
        validChapter.setStatus("FINALIZED");
        storyChapterDao.insertOrUpdate(validChapter);

        StorySummaryPO invalidSummary = new StorySummaryPO();
        invalidSummary.setProjectId(first.getId());
        invalidSummary.setChapterId(validChapter.getId());
        invalidSummary.setChapterNumber(1);
        invalidSummary.setShortSummary("不应写入");
        invalidSummary.setStatus("GENERATED");
        assertThrows(DataIntegrityViolationException.class,
                () -> storySummaryDao.insertOrUpdate(invalidSummary));
    }

    private NovelProjectPO project(String projectCode) {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(projectCode);
        project.setTitle(projectCode);
        project.setGenre("玄幻");
        project.setWordsPerChapter(2000);
        project.setCurrentChapterNumber(0);
        project.setStatus("DRAFT");
        return project;
    }

    private StoryCharacterPO character(Long projectId, String code, String name, String roleType) {
        StoryCharacterPO character = new StoryCharacterPO();
        character.setProjectId(projectId);
        character.setCharacterCode(code);
        character.setName(name);
        character.setRoleType(roleType);
        character.setGender("MALE");
        character.setPersonality("克制、谨慎");
        character.setCurrentStateJson("{\"location\":\"北城\"}");
        character.setLifeStatus("ALIVE");
        character.setStatus("ACTIVE");
        return character;
    }

    private OutlineNodePO outlineNode(
            Long projectId,
            String nodeCode,
            int sequenceNo,
            String title
    ) {
        OutlineNodePO outlineNode = new OutlineNodePO();
        outlineNode.setProjectId(projectId);
        outlineNode.setNodeCode(nodeCode);
        outlineNode.setNodeKind("ARC");
        outlineNode.setSequenceNo(sequenceNo);
        outlineNode.setStartChapter(1);
        outlineNode.setEndChapter(1);
        outlineNode.setTitle(title);
        outlineNode.setSummary(title + "摘要");
        outlineNode.setStatus("READY");
        return outlineNode;
    }
}
