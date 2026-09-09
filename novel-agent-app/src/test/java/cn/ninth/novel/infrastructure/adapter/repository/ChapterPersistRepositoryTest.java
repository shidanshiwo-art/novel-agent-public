package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.ChapterService;
import cn.ninth.novel.domain.chapter.service.agent.CompressChapterNode;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterPersistRepositoryTest {

    private static final String PROJECT_CODE = "chapter-persist-repository-test";

    @Autowired
    private ChapterPersistRepository chapterPersistRepository;
    @Autowired
    private ChapterService chapterService;
    @MockitoBean
    private CompressChapterNode compressChapterNode;
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

    @AfterEach
    void cleanUp() {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, PROJECT_CODE);
    }

    @BeforeEach
    void cleanUpBeforeTest() {
        cleanUp();
    }

    @Test
    void shouldPersistChapterAndAdvanceReadyPlanToCompleted() {
        NovelProjectPO project = insertProject();
        ChapterPlanPO chapterPlan = insertChapterPlan(project.getId());

        chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "正文第一版",
                chapterMemory("第一版摘要", "获得第一条线索"),
                new StoryStateSnapshot(
                        List.of("旧钥匙"),
                        List.of("左臂受伤"),
                        List.of("知道渡口入口"),
                        List.of("旧渡口")
                )
        );

        StoryChapterPO firstChapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        StorySummaryPO firstSummary = storySummaryDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        assertThat(firstChapter.getChapterPlanId()).isEqualTo(chapterPlan.getId());
        assertThat(firstChapter.getTitle()).isEqualTo("雨夜旧渡口");
        assertThat(firstChapter.getContent()).isEqualTo("正文第一版");
        assertThat(firstChapter.getWordCount()).isEqualTo(5);
        assertThat(firstChapter.getStatus()).isEqualTo("FINALIZED");
        assertThat(firstSummary.getChapterId()).isEqualTo(firstChapter.getId());
        assertThat(firstSummary.getShortSummary()).isEqualTo("第一版摘要");
        assertThat(firstSummary.getStoryStateSnapshotJson())
                .isEqualTo("{\"resources\":[\"旧钥匙\"],\"abilities\":[\"左臂受伤\"],"
                        + "\"knowledge\":[\"知道渡口入口\"],\"presence\":[\"旧渡口\"]}");
        assertThat(novelProjectDao.queryByProjectCode(PROJECT_CODE).getCurrentChapterNumber())
                .isEqualTo(1);
        assertThat(chapterPlanDao.queryByChapterNumber(project.getId(), 1).getStatus())
                .isEqualTo("COMPLETED");

        assertThatThrownBy(() -> chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "正文第二版",
                chapterMemory("第二版摘要", "确认旧渡口位置")
        )).isInstanceOf(AppException.class)
                .satisfies(throwable -> assertThat(((AppException) throwable).getInternalDetail())
                        .contains("章节计划未处于 READY 状态"));

        StoryChapterPO unchangedChapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        StorySummaryPO unchangedSummary = storySummaryDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        assertThat(unchangedChapter.getId()).isEqualTo(firstChapter.getId());
        assertThat(unchangedChapter.getContent()).isEqualTo("正文第一版");
        assertThat(unchangedSummary.getId()).isEqualTo(firstSummary.getId());
        assertThat(unchangedSummary.getShortSummary()).isEqualTo("第一版摘要");
        assertThat(unchangedSummary.getKeyEventsJson()).isEqualTo("[\"获得第一条线索\"]");
        System.out.printf(
                "ChapterPersistRepositoryTest persisted chapter=%d planId=%d status=%s%n",
                unchangedChapter.getChapterNumber(),
                unchangedChapter.getChapterPlanId(),
                chapterPlanDao.queryByChapterNumber(project.getId(), 1).getStatus());
    }

    @Test
    void shouldInvalidateDerivedDataAfterManualChapterOverwrite() {
        NovelProjectPO project = insertProject();
        insertChapterPlan(project.getId());

        chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "正文第一版",
                chapterMemory("第一版摘要", "获得第一条线索")
        );

        GeneratedChapterVO overwritten = novelProjectRepository.overwriteChapterContent(
                PROJECT_CODE,
                1,
                "人工修改后的标题",
                "人工修改后的正文",
                8
        );

        StoryChapterPO chapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        StorySummaryPO summary = storySummaryDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);

        assertThat(overwritten.status()).isEqualTo("DIRTY");
        assertThat(chapter.getStatus()).isEqualTo("DIRTY");
        assertThat(summary.getStatus()).isEqualTo("STALE");
        System.out.printf(
                "manual overwrite invalidated chapter derivatives: chapterStatus=%s, summaryStatus=%s%n",
                chapter.getStatus(), summary.getStatus());
    }

    @Test
    void shouldResyncDirtyChapterDerivedDataAndRestoreFinalizedStatus() {
        NovelProjectPO project = insertProject();
        insertChapterPlan(project.getId());

        chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "正文第一版",
                chapterMemory("第一版摘要", "获得第一条线索")
        );
        novelProjectRepository.overwriteChapterContent(
                PROJECT_CODE,
                1,
                "人工修改后的标题",
                "人工修改后的正文",
                8
        );

        chapterPersistRepository.persistDerivedData(
                PROJECT_CODE,
                1,
                chapterMemory("修改后摘要", "修改后实际结果")
        );

        StoryChapterPO chapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        StorySummaryPO summary = storySummaryDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);

        assertThat(chapter.getStatus()).isEqualTo("FINALIZED");
        assertThat(chapter.getTitle()).isEqualTo("人工修改后的标题");
        assertThat(chapter.getContent()).isEqualTo("人工修改后的正文");
        assertThat(summary.getStatus()).isEqualTo("GENERATED");
        assertThat(summary.getShortSummary()).isEqualTo("修改后摘要");
        assertThat(summary.getKeyEventsJson()).isEqualTo("[\"修改后实际结果\"]");
        assertThat(chapterPlanDao.queryByChapterNumber(project.getId(), 1).getStatus())
                .isEqualTo("COMPLETED");
        System.out.printf(
                "resynced dirty chapter: chapterStatus=%s, summaryStatus=%s%n",
                chapter.getStatus(), summary.getStatus());
    }

    @Test
    void shouldRejectDerivedDataSyncWhenChapterIsNotDirty() {
        NovelProjectPO project = insertProject();
        insertChapterPlan(project.getId());
        chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "正文第一版",
                chapterMemory("第一版摘要", "获得第一条线索")
        );

        assertThatThrownBy(() -> chapterPersistRepository.persistDerivedData(
                PROJECT_CODE,
                1,
                chapterMemory("不应替换的摘要", "不应替换的结果")
        )).isInstanceOf(AppException.class)
                .satisfies(throwable -> assertThat(((AppException) throwable).getInternalDetail())
                        .contains("仅允许 DIRTY 章节"));
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1)
                .getStatus()).isEqualTo("FINALIZED");
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(project.getId(), 1)
                .getShortSummary()).isEqualTo("第一版摘要");
        System.out.println("derived data sync rejected finalized chapter; original summary remained intact");
    }

    @Test
    void shouldResyncByPassingStoredManualContentDirectlyToCompression() {
        NovelProjectPO project = insertProject();
        insertChapterPlan(project.getId());
        chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "正文第一版",
                chapterMemory("第一版摘要", "获得第一条线索")
        );
        novelProjectRepository.overwriteChapterContent(
                PROJECT_CODE,
                1,
                "人工修改后的标题",
                "人工修改后的正文",
                8
        );
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(project.getId(), 1)
                .getStatus()).isEqualTo("STALE");

        ChapterMemoryVO chapterMemory = chapterMemory(
                "同步后的摘要", "同步后的实际结果");
        when(compressChapterNode.apply(any())).thenReturn(Map.of(
                ChapterGraphKeys.MEMORY, chapterMemory
        ));

        ChapterGenerationResultVO result = chapterService.resyncChapterDerivedData(
                PROJECT_CODE, 1);

        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.content()).isEqualTo("人工修改后的正文");
        assertThat(result.completedStages())
                .containsExactly("COMPRESSION", "PERSIST_DERIVED_DATA");
        verify(compressChapterNode).apply(org.mockito.ArgumentMatchers.argThat(state ->
                state.draft().orElseThrow().equals("人工修改后的正文")));
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1)
                .getStatus()).isEqualTo("FINALIZED");
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(project.getId(), 1)
                .getShortSummary()).isEqualTo("同步后的摘要");
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(project.getId(), 1)
                .getStatus()).isEqualTo("GENERATED");
        System.out.println("resync service executed COMPRESSION on stored manual content and restored FINALIZED");
    }

    @Test
    void shouldRejectPersistenceWhenChapterPlanIsMissing() {
        insertProject();

        assertThatThrownBy(() -> chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "不应生成的正文",
                chapterMemory("不应保存的摘要", "不应保存的结果")
        )).isInstanceOf(AppException.class)
                .satisfies(throwable -> assertThat(((AppException) throwable).getInternalDetail())
                        .contains("章节计划不存在"));

        NovelProjectPO project = novelProjectDao.queryByProjectCode(PROJECT_CODE);
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1))
                .isNull();
        System.out.printf(
                "ChapterPersistRepositoryTest rejected missing plan chapter=1 chapterExists=%s%n",
                storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1) != null);
    }

    @Test
    void shouldRejectPersistenceWhenChapterPlanIsNotReady() {
        NovelProjectPO project = insertProject();
        ChapterPlanPO chapterPlan = insertChapterPlan(project.getId());
        chapterPlanDao.updateStatus(project.getId(), chapterPlan.getId(), "PLANNED");

        assertThatThrownBy(() -> chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "不应生成的正文",
                chapterMemory("不应保存的摘要", "不应保存的结果")
        )).isInstanceOf(AppException.class)
                .satisfies(throwable -> assertThat(((AppException) throwable).getInternalDetail())
                        .contains("章节计划未处于 READY 状态"));

        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1))
                .isNull();
        assertThat(chapterPlanDao.queryByChapterNumber(project.getId(), 1).getStatus())
                .isEqualTo("PLANNED");
        System.out.printf(
                "ChapterPersistRepositoryTest rejected non-ready plan=%d status=%s chapterExists=%s%n",
                chapterPlan.getId(),
                chapterPlanDao.queryByChapterNumber(project.getId(), 1).getStatus(),
                storyChapterDao.queryByProjectIdAndChapterNumber(project.getId(), 1) != null);
    }

    private NovelProjectPO insertProject() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("北境夜行");
        project.setGenre("玄幻");
        project.setTargetChapterCount(100);
        project.setWordsPerChapter(2500);
        project.setCurrentChapterNumber(0);
        project.setStatus("DRAFT");
        novelProjectDao.insert(project);
        return project;
    }

    private ChapterPlanPO insertChapterPlan(Long projectId) {
        OutlineNodePO book = new OutlineNodePO();
        book.setProjectId(projectId);
        book.setNodeCode("book-1");
        book.setNodeKind("BOOK");
        book.setSequenceNo(1);
        book.setStartChapter(1);
        book.setEndChapter(1);
        book.setTitle("北境夜行");
        book.setSummary("林澈追查兄长下落。");
        book.setStatus("READY");
        outlineNodeDao.insert(book);

        OutlineNodePO arc = new OutlineNodePO();
        arc.setProjectId(projectId);
        arc.setParentId(book.getId());
        arc.setNodeCode("arc-1");
        arc.setNodeKind("ARC");
        arc.setSequenceNo(1);
        arc.setStartChapter(1);
        arc.setEndChapter(1);
        arc.setTitle("旧渡口线索");
        arc.setSummary("林澈追查旧渡口线索。");
        arc.setStatus("READY");
        outlineNodeDao.insert(arc);

        ChapterPlanPO plan = new ChapterPlanPO();
        plan.setProjectId(projectId);
        plan.setOutlineNodeId(arc.getId());
        plan.setChapterNumber(1);
        plan.setTitle("雨夜旧渡口");
        plan.setSummary("确认兄长行踪");
        plan.setStatus("READY");
        chapterPlanDao.insert(plan);
        return chapterPlanDao.queryByChapterNumber(projectId, 1);
    }

    private ChapterMemoryVO chapterMemory(String shortSummary, String keyEvent) {
        return ChapterMemoryVO.builder()
                .shortSummary(shortSummary)
                .keyEvents(List.of(keyEvent))
                .unresolved(List.of("敲门者是谁"))
                .endingHook("门外响起三短一长敲门声")
                .build();
    }
}
