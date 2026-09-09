package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterDeletionRepositoryTest {

    private static final String PROJECT_CODE = "chapter-deletion-repository-test";

    @Autowired
    private NovelProjectRepository novelProjectRepository;
    @Autowired
    private INovelProjectDao novelProjectDao;
    @Autowired
    private IOutlineNodeDao outlineNodeDao;
    @Autowired
    private IChapterPlanDao chapterPlanDao;
    @Autowired
    private IStoryChapterDao storyChapterDao;
    @Autowired
    private IStorySummaryDao storySummaryDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUpBeforeTest() {
        cleanUp();
    }

    @AfterEach
    void cleanUpAfterTest() {
        cleanUp();
    }

    @Test
    void shouldRejectDeletingIntermediateChapterAndAllowDeletingLastChapter() {
        NovelProjectPO project = insertProject();
        OutlineNodePO arc = insertArc(project.getId());
        ChapterPlanPO firstPlan = insertChapterPlan(project.getId(), arc.getId(), 1);
        ChapterPlanPO lastPlan = insertChapterPlan(project.getId(), arc.getId(), 2);
        insertChapter(project.getId(), firstPlan.getId(), 1);
        StoryChapterPO lastChapter = insertChapter(project.getId(), lastPlan.getId(), 2);
        insertSummary(project.getId(), lastChapter.getId(), 2);

        System.out.println("尝试删除存在更大章节号正文的第1章");
        assertThatThrownBy(() -> novelProjectRepository.deleteChapter(PROJECT_CODE, 1))
                .isInstanceOf(AppException.class)
                .satisfies(throwable -> assertThat(((AppException) throwable).getInternalDetail())
                        .contains("只允许删除最后一章"));
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1))
                .isNotNull();
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 2))
                .isNotNull();

        System.out.println("删除最后一章第2章");
        novelProjectRepository.deleteChapter(PROJECT_CODE, 2);
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1))
                .isNotNull();
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 2))
                .isNull();
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(project.getId(), 2))
                .isNull();
        assertThat(chapterPlanDao.queryByChapterNumber(project.getId(), 2).getStatus())
                .isEqualTo("READY");
        assertThat(novelProjectDao.queryByProjectCode(PROJECT_CODE).getCurrentChapterNumber())
                .isEqualTo(1);
        System.out.printf(
                "ChapterDeletionRepositoryTest intermediateDeleteRejected=true chapterMemoryAndChapterDeleted=true planStatus=%s currentChapter=%d%n",
                chapterPlanDao.queryByChapterNumber(project.getId(), 2).getStatus(),
                novelProjectDao.queryByProjectCode(PROJECT_CODE).getCurrentChapterNumber());
    }

    private NovelProjectPO insertProject() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("章节删除校验测试");
        project.setGenre("玄幻");
        project.setTargetChapterCount(2);
        project.setWordsPerChapter(2500);
        project.setCurrentChapterNumber(2);
        project.setStatus("WRITING");
        novelProjectDao.insert(project);
        return project;
    }

    private OutlineNodePO insertArc(Long projectId) {
        OutlineNodePO book = new OutlineNodePO();
        book.setProjectId(projectId);
        book.setNodeCode("book-1");
        book.setNodeKind("BOOK");
        book.setSequenceNo(1);
        book.setStartChapter(1);
        book.setEndChapter(2);
        book.setTitle("章节删除校验测试");
        book.setSummary("章节删除校验测试");
        book.setStatus("READY");
        outlineNodeDao.insert(book);

        OutlineNodePO arc = new OutlineNodePO();
        arc.setProjectId(projectId);
        arc.setParentId(book.getId());
        arc.setNodeCode("arc-1");
        arc.setNodeKind("VOLUME");
        arc.setSequenceNo(1);
        arc.setStartChapter(1);
        arc.setEndChapter(2);
        arc.setTitle("章节删除校验线");
        arc.setSummary("章节删除校验线");
        arc.setStatus("READY");
        outlineNodeDao.insert(arc);
        return arc;
    }

    private ChapterPlanPO insertChapterPlan(Long projectId, Long arcId, int chapterNumber) {
        ChapterPlanPO plan = new ChapterPlanPO();
        plan.setProjectId(projectId);
        plan.setOutlineNodeId(arcId);
        plan.setChapterNumber(chapterNumber);
        plan.setTitle("第" + chapterNumber + "章");
        plan.setSummary("章节删除校验正文");
        plan.setStatus("COMPLETED");
        chapterPlanDao.insert(plan);
        return chapterPlanDao.queryByChapterNumber(projectId, chapterNumber);
    }

    private StoryChapterPO insertChapter(Long projectId, Long chapterPlanId, int chapterNumber) {
        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(projectId);
        chapter.setChapterPlanId(chapterPlanId);
        chapter.setChapterNumber(chapterNumber);
        chapter.setTitle("第" + chapterNumber + "章");
        chapter.setContent("这是第" + chapterNumber + "章正文。");
        chapter.setWordCount(chapter.getContent().length());
        chapter.setStatus("FINALIZED");
        storyChapterDao.insertOrUpdate(chapter);
        return storyChapterDao.queryByProjectIdAndChapterNumber(projectId, chapterNumber);
    }

    private void insertSummary(Long projectId, Long chapterId, int chapterNumber) {
        StorySummaryPO summary = new StorySummaryPO();
        summary.setProjectId(projectId);
        summary.setChapterId(chapterId);
        summary.setChapterNumber(chapterNumber);
        summary.setShortSummary("待删除章节摘要");
        summary.setStatus("GENERATED");
        storySummaryDao.insertOrUpdate(summary);
    }

    private void cleanUp() {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, PROJECT_CODE);
    }
}
