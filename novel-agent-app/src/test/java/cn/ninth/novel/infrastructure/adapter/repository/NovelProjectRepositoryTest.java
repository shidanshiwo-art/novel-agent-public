package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.IStoryBibleDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStoryCharacterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class NovelProjectRepositoryTest {

    private static final String PROJECT_CODE =
            "project-management-repository-test";

    @Autowired
    private NovelProjectRepository repository;
    @Autowired
    private INovelProjectDao novelProjectDao;
    @Autowired
    private IChapterPlanDao chapterPlanDao;
    @Autowired
    private IStoryBibleDao storyBibleDao;
    @Autowired
    private IOutlineNodeDao outlineNodeDao;
    @Autowired
    private IStoryCharacterDao storyCharacterDao;
    @Autowired
    private IStoryChapterDao storyChapterDao;
    @Autowired
    private IStorySummaryDao storySummaryDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBeforeTest() {
        cleanUp();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanUp();
    }

    @Test
    void shouldPersistAndQueryProjectManagementData() {
        NovelProjectVO created = repository.createProject(new NovelProjectVO(
                PROJECT_CODE,
                "北境夜行",
                "玄幻",
                100,
                2500,
                0,
                "DRAFT"
        ));
        NovelProjectPO project = novelProjectDao.queryByProjectCode(PROJECT_CODE);
        assertThat(created.projectCode()).isEqualTo(PROJECT_CODE);
        assertThat(project.getId()).isNotNull();

        repository.saveBible(PROJECT_CODE, bible("真相与代价"));
        StoryBiblePO firstBible = storyBibleDao.queryByProjectId(project.getId());
        repository.saveBible(PROJECT_CODE, bible("选择与牺牲"));
        StoryBiblePO updatedBible = storyBibleDao.queryByProjectId(project.getId());
        assertThat(updatedBible.getId()).isEqualTo(firstBible.getId());
        assertThat(updatedBible.getCoreTheme()).isEqualTo("选择与牺牲");

        OutlineNodePO firstOutline = outline(
                project.getId(), "arc-1", 1, "午夜钟楼"
        );
        outlineNodeDao.insert(firstOutline);
        OutlineNodePO secondOutline = outline(
                project.getId(), "arc-2", 2, "议会徽记"
        );
        outlineNodeDao.insert(secondOutline);
        assertThat(outlineNodeDao.queryByProject(project.getId()))
                .hasSize(2);

        StoryCharacterVO character = repository.addCharacter(
                PROJECT_CODE,
                character()
        );
        assertThat(character.characterCode()).isEqualTo("lin-yuan");
        assertThat(storyCharacterDao.queryByCharacterCode(
                project.getId(),
                "lin-yuan"
        ).getName()).isEqualTo("林渊");
        Long originalCharacterId = storyCharacterDao.queryByCharacterCode(
                project.getId(),
                "lin-yuan"
        ).getId();

        StoryCharacterVO updatedCharacter = repository.updateCharacter(
                PROJECT_CODE,
                "lin-yuan",
                character("林渊·人工修订")
        );
        assertThat(updatedCharacter.name()).isEqualTo("林渊·人工修订");
        StoryCharacterPO updatedCharacterPO = storyCharacterDao
                .queryByCharacterCode(
                project.getId(),
                "lin-yuan"
        );
        assertThat(updatedCharacterPO.getId()).isEqualTo(originalCharacterId);
        assertThat(updatedCharacterPO.getName()).isEqualTo("林渊·人工修订");

        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(project.getId());
        chapter.setChapterPlanId(firstOutline.getId());
        chapter.setChapterNumber(1);
        chapter.setTitle(firstOutline.getTitle());
        chapter.setContent("林渊在午夜进入钟楼并取得残页。");
        chapter.setWordCount(17);
        chapter.setStatus("FINALIZED");
        storyChapterDao.insertOrUpdate(chapter);
        novelProjectDao.advanceProgress(project.getId(), 1);

        StoryChapterPO secondChapter = new StoryChapterPO();
        secondChapter.setProjectId(project.getId());
        secondChapter.setChapterPlanId(secondOutline.getId());
        secondChapter.setChapterNumber(2);
        secondChapter.setTitle(secondOutline.getTitle());
        secondChapter.setContent("议会徽记指向北城深处的密室。");
        secondChapter.setWordCount(15);
        secondChapter.setStatus("FINALIZED");
        storyChapterDao.insertOrUpdate(secondChapter);

        StorySummaryPO summary = new StorySummaryPO();
        summary.setProjectId(project.getId());
        summary.setChapterId(chapter.getId());
        summary.setChapterNumber(1);
        summary.setShortSummary("林渊取得残页");
        summary.setStatus("GENERATED");
        storySummaryDao.insertOrUpdate(summary);

        Long originalChapterId = chapter.getId();
        GeneratedChapterVO overwritten = repository.overwriteChapterContent(
                PROJECT_CODE,
                1,
                "午夜钟楼·新题",
                "林渊完成了人工润色。",
                10
        );
        StoryChapterPO overwrittenPO = storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), 1);
        assertThat(overwrittenPO.getId()).isEqualTo(originalChapterId);
        assertThat(overwrittenPO.getTitle()).isEqualTo("午夜钟楼·新题");
        assertThat(overwritten.title()).isEqualTo("午夜钟楼·新题");
        assertThat(overwritten.content()).isEqualTo("林渊完成了人工润色。");
        assertThat(overwritten.wordCount()).isEqualTo(10);
        assertThat(overwritten.status()).isEqualTo("DIRTY");
        assertThat(overwrittenPO.getStatus()).isEqualTo("DIRTY");
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(
                project.getId(),
                1
        ).getStatus()).isEqualTo("STALE");
        System.out.printf(
                "manual chapter overwrite invalidated chapter memory: chapterStatus=%s, summaryStatus=%s%n",
                overwrittenPO.getStatus(),
                storySummaryDao.queryByProjectIdAndChapterNumber(project.getId(), 1).getStatus());

        GeneratedChapterVO generated = repository.findChapter(PROJECT_CODE, 1);
        List<GeneratedChapterVO> generatedChapters = repository.findChapters(
                PROJECT_CODE
        );
        NovelProjectVO progress = repository.findProject(PROJECT_CODE);
        assertThat(generated.title()).isEqualTo("午夜钟楼·新题");
        assertThat(generated.content()).contains("人工润色");
        assertThat(generatedChapters)
                .extracting(GeneratedChapterVO::chapterNumber)
                .containsExactly(1, 2);
        assertThat(generatedChapters)
                .extracting(GeneratedChapterVO::title)
                .containsExactly("午夜钟楼·新题", "议会徽记");
        assertThat(progress.currentChapterNumber()).isEqualTo(1);
        assertThat(progress.status()).isEqualTo("WRITING");
    }

    @Test
    void shouldUpdateBookAndActiveVolumeUpperBoundsWithoutChangingFinalizedVolume() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("卷边界同步测试");
        project.setGenre("玄幻");
        project.setTargetChapterCount(100);
        project.setWordsPerChapter(2500);
        project.setCurrentChapterNumber(27);
        project.setStatus("WRITING");
        novelProjectDao.insert(project);

        OutlineNodePO book = outlineNode(
                project.getId(), null, "book-1", "BOOK", 1,
                1, 100, "全书", "全书主线");
        outlineNodeDao.insert(book);
        OutlineNodePO finalizedVolume = outlineNode(
                project.getId(), book.getId(), "volume-1", "VOLUME", 1,
                1, 12, "第一卷", "已封口卷");
        outlineNodeDao.insert(finalizedVolume);
        OutlineNodePO activeVolume = outlineNode(
                project.getId(), book.getId(), "volume-2", "VOLUME", 2,
                13, 100, "第二卷", "活动卷");
        outlineNodeDao.insert(activeVolume);
        OutlineNodePO lastArc = outline(
                project.getId(), "arc-27", 27, "第二十七章");
        lastArc.setParentId(activeVolume.getId());
        lastArc.setStartChapter(27);
        lastArc.setEndChapter(27);
        outlineNodeDao.insert(lastArc);

        NovelProjectVO updated = repository.updateTargetChapterCount(
                PROJECT_CODE, 120);

        assertThat(updated.targetChapterCount()).isEqualTo(120);
        assertThat(novelProjectDao.queryByProjectCode(PROJECT_CODE)
                .getTargetChapterCount()).isEqualTo(120);
        assertThat(outlineNodeDao.queryByCode(project.getId(), "book-1")
                .getEndChapter()).isEqualTo(120);
        assertThat(outlineNodeDao.queryByCode(project.getId(), "volume-1")
                .getEndChapter()).isEqualTo(12);
        assertThat(outlineNodeDao.queryByCode(project.getId(), "volume-2")
                .getEndChapter()).isEqualTo(120);
        System.out.printf(
                "target sync boundaries: book=%d finalizedVolume=%d activeVolume=%d%n",
                outlineNodeDao.queryByCode(project.getId(), "book-1").getEndChapter(),
                outlineNodeDao.queryByCode(project.getId(), "volume-1").getEndChapter(),
                outlineNodeDao.queryByCode(project.getId(), "volume-2").getEndChapter());
    }

    @Test
    void shouldRejectTargetBelowMaximumActualChapter() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("实际章节校验测试");
        project.setGenre("玄幻");
        project.setTargetChapterCount(100);
        project.setWordsPerChapter(2500);
        project.setCurrentChapterNumber(0);
        project.setStatus("WRITING");
        novelProjectDao.insert(project);

        OutlineNodePO book = outlineNode(
                project.getId(), null, "book-1", "BOOK", 1,
                1, 100, "全书", "全书主线");
        outlineNodeDao.insert(book);
        ChapterPlanPO plan = new ChapterPlanPO();
        plan.setProjectId(project.getId());
        plan.setOutlineNodeId(book.getId());
        plan.setChapterNumber(73);
        plan.setTitle("第七十三章");
        plan.setSummary("已完成章节");
        plan.setStatus("COMPLETED");
        chapterPlanDao.insert(plan);

        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(project.getId());
        chapter.setChapterPlanId(plan.getId());
        chapter.setChapterNumber(73);
        chapter.setTitle("第七十三章");
        chapter.setContent("已完成正文");
        chapter.setWordCount(5);
        chapter.setStatus("FINALIZED");
        storyChapterDao.insertOrUpdate(chapter);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        repository.updateTargetChapterCount(PROJECT_CODE, 60))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("预计章节数不能小于已完成章节数");
        assertThat(novelProjectDao.queryByProjectCode(PROJECT_CODE)
                .getTargetChapterCount()).isEqualTo(100);
        System.out.println("actual chapter guard rejected target 60 below chapter 73");
    }

    private StoryBibleVO bible(String coreTheme) {
        return new StoryBibleVO(
                "林渊调查父亲失踪真相",
                coreTheme,
                "林渊与北城议会争夺线索",
                "揭开议会秘密",
                "北境诸城由议会统治",
                "{\"realms\":[\"启灵\"]}",
                "[\"影步不能在强光下使用\"]",
                "第三人称限知",
                "CONFIRMED"
        );
    }

    private OutlineNodePO outline(
            Long projectId,
            String nodeCode,
            int sequenceNo,
            String title
    ) {
        OutlineNodePO outline = new OutlineNodePO();
        outline.setProjectId(projectId);
        outline.setNodeCode(nodeCode);
        outline.setNodeKind("ARC");
        outline.setSequenceNo(sequenceNo);
        outline.setStartChapter(sequenceNo);
        outline.setEndChapter(sequenceNo);
        outline.setTitle(title);
        outline.setSummary(title + "章节摘要");
        outline.setStatus("READY");
        return outline;
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
            String summary
    ) {
        OutlineNodePO outline = new OutlineNodePO();
        outline.setProjectId(projectId);
        outline.setParentId(parentId);
        outline.setNodeCode(nodeCode);
        outline.setNodeKind(nodeKind);
        outline.setSequenceNo(sequenceNo);
        outline.setStartChapter(startChapter);
        outline.setEndChapter(endChapter);
        outline.setTitle(title);
        outline.setSummary(summary);
        outline.setStatus("READY");
        return outline;
    }

    private StoryCharacterVO character() {
        return character("林渊");
    }

    private StoryCharacterVO character(String name) {
        return new StoryCharacterVO(
                "lin-yuan",
                name,
                "MALE_LEAD",
                "MALE",
                "十八岁",
                "黑发",
                "克制、谨慎",
                "父亲失踪",
                null,
                "{\"location\":\"北城\"}",
                "ALIVE",
                "ACTIVE"
        );
    }

    private void cleanUp() {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, PROJECT_CODE);
    }
}
