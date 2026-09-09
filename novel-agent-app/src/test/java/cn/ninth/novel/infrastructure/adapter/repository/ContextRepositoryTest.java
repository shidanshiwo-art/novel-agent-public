package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.service.data.ChapterContextLoader;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.IStoryBibleDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStoryCharacterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryBiblePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 使用真实 MySQL 和 MyBatis Mapper 验证章节结构化上下文加载。
 */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=${context.repository.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${context.repository.test.username:root}",
                "spring.datasource.password=${context.repository.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class ContextRepositoryTest {

    private static final String PROJECT_CODE = "context-repository-story";

    @Autowired
    private ContextRepository contextRepository;
    @Autowired
    private ChapterContextLoader chapterContextLoader;
    @Autowired
    private INovelProjectDao novelProjectDao;
    @Autowired
    private IStoryBibleDao storyBibleDao;
    @Autowired
    private IOutlineNodeDao outlineNodeDao;
    @Autowired
    private IChapterPlanDao chapterPlanDao;
    @Autowired
    private IStoryCharacterDao storyCharacterDao;
    @Autowired
    private IStoryChapterDao storyChapterDao;
    @Autowired
    private IStorySummaryDao storySummaryDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUpProject();
        insertContextFixture();
    }

    @AfterEach
    void tearDown() {
        cleanUpProject();
    }

    @Test
    void shouldLoadStructuredContextFromRealDatabase() throws Exception {
        NovelProjectEntity project = contextRepository.loadProject(PROJECT_CODE);
        StoryBibleEntity bible = contextRepository.loadStoryBible(PROJECT_CODE);
        ChapterPlanEntity chapterPlan = contextRepository.loadChapterPlan(PROJECT_CODE, 2);
        ChapterContextAggregate context = chapterContextLoader.loadContext(PROJECT_CODE, 2);
        List<StoryCharacterEntity> characters = contextRepository.loadCharacters(PROJECT_CODE);
        ChapterHistoryVO history = contextRepository.loadHistory(PROJECT_CODE, 2);
        List<ChapterMemoryVO> memories =
                contextRepository.findRecentChapterMemories(PROJECT_CODE, 2, 8);

        assertNotNull(project);
        assertEquals("北境夜行", project.getTitle());
        assertEquals(2500, project.getWordsPerChapter());
        assertNotNull(bible);
        assertEquals("林渊带着残图调查父亲失踪真相。", bible.getOneSentencePremise());
        assertEquals("CONFIRMED", bible.getStatus());
        assertNotNull(chapterPlan);
        assertEquals(2, chapterPlan.getChapterNumber());
        assertEquals("议会徽记", chapterPlan.getTitle());
        assertEquals("确认残页上的徽记来源", chapterPlan.getSummary());
        assertEquals("READY", chapterPlan.getStatus());
        assertNotNull(context.getChapterPlan());
        assertEquals("议会徽记", context.getChapterPlan().getTitle());
        assertEquals("确认残页上的徽记来源", context.getChapterPlan().getSummary());
        assertEquals("READY", context.getChapterPlan().getStatus());
        context.validateReadyForGeneration();
        assertEquals(1, characters.size());
        assertEquals("lin-yuan", characters.get(0).getCharacterCode());
        assertEquals(1, history.getRecentMemories().size());
        assertEquals(1, history.getRecentMemories().get(0).getChapterNumber());
        assertEquals("林渊进入钟楼并取得残页。", history.getRecentMemories().get(0).getShortSummary());
        assertEquals(List.of("取得带有议会徽记的残页"), history.getRecentMemories().get(0).getKeyEvents());
        assertEquals(List.of("议会为何追查父亲"), history.getRecentMemories().get(0).getUnresolved());
        assertEquals("残页出现议会徽记", history.getRecentMemories().get(0).getEndingHook());
        assertEquals(List.of("带有议会徽记的残页"), history.getStoryStateSnapshot().resources());
        assertEquals(List.of("林渊仍在钟楼"), history.getStoryStateSnapshot().presence());
        assertNotNull(history.getPreviousChapter());
        assertEquals(1, history.getPreviousChapter().getChapterNumber());
        assertEquals("林渊在午夜进入钟楼并取得残页。", history.getPreviousChapter().getContent());
        assertEquals(1, memories.size());
        assertEquals(1, memories.get(0).getChapterNumber());
        assertEquals("林渊进入钟楼并取得残页。", memories.get(0).getShortSummary());
        assertEquals(List.of("取得带有议会徽记的残页"), memories.get(0).getKeyEvents());
        assertEquals(List.of("议会为何追查父亲"), memories.get(0).getUnresolved());
        assertEquals("残页出现议会徽记", memories.get(0).getEndingHook());

        System.out.printf(
                "ContextRepositoryTest loaded project=%s chapterPlan=%d title=%s historyChapter=%d memoryChapter=%d keyEvents=%s%n",
                project.getProjectCode(),
                chapterPlan.getChapterNumber(),
                chapterPlan.getTitle(),
                history.getPreviousChapter().getChapterNumber(),
                memories.get(0).getChapterNumber(),
                memories.get(0).getKeyEvents());
    }

    @Test
    void shouldExcludeDirtyChapterAndDerivedSummaryFromLaterHistory() {
        StoryChapterPO dirtyChapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(projectId(), 1);
        dirtyChapter.setStatus("DIRTY");
        storyChapterDao.insertOrUpdate(dirtyChapter);

        StorySummaryPO summary = storySummaryDao
                .queryByProjectIdAndChapterNumber(projectId(), 1);
        summary.setStatus("VERIFIED");
        storySummaryDao.insertOrUpdate(summary);

        ChapterHistoryVO history = contextRepository.loadHistory(PROJECT_CODE, 2);

        assertEquals(0, history.getRecentMemories().size());
        assertNull(history.getPreviousChapter());
        assertEquals(0, contextRepository.findRecentChapterMemories(PROJECT_CODE, 2, 8).size());
        System.out.printf(
                "dirty chapter excluded from later history: chapterStatus=%s, summaries=%d, previousChapter=%s%n",
                dirtyChapter.getStatus(),
                history.getRecentMemories().size(),
                history.getPreviousChapter());
    }

    private Long projectId() {
        return novelProjectDao.queryByProjectCode(PROJECT_CODE).getId();
    }

    private void insertContextFixture() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("北境夜行");
        project.setGenre("玄幻");
        project.setTargetChapterCount(2);
        project.setWordsPerChapter(2500);
        project.setCurrentChapterNumber(1);
        project.setStatus("WRITING");
        novelProjectDao.insert(project);

        StoryBiblePO bible = new StoryBiblePO();
        bible.setProjectId(project.getId());
        bible.setOneSentencePremise("林渊带着残图调查父亲失踪真相。");
        bible.setCoreTheme("真相与代价");
        bible.setMainConflict("林渊与北城议会争夺父亲遗留线索");
        bible.setWorldBackground("北境诸城由议会和古老家族共同统治。");
        bible.setPowerSystemJson("{\"realms\":[\"启灵\",\"凝光\"]}");
        bible.setHardRulesJson("[\"影步不能在强光下使用\"]");
        bible.setStyleGuide("第三人称限知，克制叙事。");
        bible.setStatus("CONFIRMED");
        storyBibleDao.insert(bible);

        StoryCharacterPO protagonist = new StoryCharacterPO();
        protagonist.setProjectId(project.getId());
        protagonist.setCharacterCode("lin-yuan");
        protagonist.setName("林渊");
        protagonist.setRoleType("MALE_LEAD");
        protagonist.setGender("MALE");
        protagonist.setPersonality("克制、谨慎");
        protagonist.setCurrentStateJson("{\"location\":\"北城客栈\"}");
        protagonist.setLifeStatus("ALIVE");
        protagonist.setStatus("ACTIVE");
        storyCharacterDao.insert(protagonist);

        OutlineNodePO book = outlineNode(
                project.getId(), null, "book-1", "BOOK", 1, 1, 2,
                "北境夜行", "林渊调查父亲失踪真相");
        outlineNodeDao.insert(book);
        OutlineNodePO arc = outlineNode(
                project.getId(), book.getId(), "arc-1", "ARC", 1, 1, 1,
                "钟楼残页", "围绕议会徽记追查线索");
        outlineNodeDao.insert(arc);
        OutlineNodePO secondArc = outlineNode(
                project.getId(), book.getId(), "arc-2", "ARC", 2, 2, 2,
                "议会徽记", "确认残页上的议会徽记来源");
        outlineNodeDao.insert(secondArc);

        ChapterPlanPO firstPlan = chapterPlan(
                project.getId(), arc.getId(), 1, "午夜钟楼",
                "进入钟楼寻找线索", "READY");
        ChapterPlanPO secondPlan = chapterPlan(
                project.getId(), secondArc.getId(), 2, "议会徽记",
                "确认残页上的徽记来源", "READY");
        chapterPlanDao.insert(firstPlan);
        chapterPlanDao.insert(secondPlan);
        firstPlan = chapterPlanDao.queryByChapterNumber(project.getId(), 1);

        StoryChapterPO firstChapter = new StoryChapterPO();
        firstChapter.setProjectId(project.getId());
        firstChapter.setChapterPlanId(firstPlan.getId());
        firstChapter.setChapterNumber(1);
        firstChapter.setTitle("午夜钟楼");
        firstChapter.setContent("林渊在午夜进入钟楼并取得残页。");
        firstChapter.setWordCount(17);
        firstChapter.setStatus("FINALIZED");
        storyChapterDao.insertOrUpdate(firstChapter);

        StorySummaryPO firstSummary = new StorySummaryPO();
        firstSummary.setProjectId(project.getId());
        firstSummary.setChapterId(firstChapter.getId());
        firstSummary.setChapterNumber(1);
        firstSummary.setShortSummary("林渊进入钟楼并取得残页。");
        firstSummary.setKeyEventsJson("[\"取得带有议会徽记的残页\"]");
        firstSummary.setUnresolvedQuestionsJson("[\"议会为何追查父亲\"]");
        firstSummary.setEndingHook("残页出现议会徽记");
        firstSummary.setStoryStateSnapshotJson(
                "{\"resources\":[\"带有议会徽记的残页\"],"
                        + "\"abilities\":[],\"knowledge\":[],"
                        + "\"presence\":[\"林渊仍在钟楼\"]}");
        firstSummary.setStatus("VERIFIED");
        storySummaryDao.insertOrUpdate(firstSummary);

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
        node.setStatus("READY");
        return node;
    }

    private ChapterPlanPO chapterPlan(
            Long projectId,
            Long outlineNodeId,
            int chapterNumber,
            String title,
            String summary,
            String status) {
        ChapterPlanPO plan = new ChapterPlanPO();
        plan.setProjectId(projectId);
        plan.setOutlineNodeId(outlineNodeId);
        plan.setChapterNumber(chapterNumber);
        plan.setTitle(title);
        plan.setSummary(summary);
        plan.setStatus(status);
        return plan;
    }

    private void cleanUpProject() {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, PROJECT_CODE);
    }
}
