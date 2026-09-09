package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChapterPlanDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.service.PlanningService;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ChapterMemoryCrossChapterPlanningTest {

    private static final String PROJECT_CODE = "chapter-memory-cross-chapter-test";

    @Autowired
    private ChapterPersistRepository chapterPersistRepository;
    @Autowired
    private PlanningRepository planningRepository;
    @Autowired
    private ContextRepository contextRepository;
    @Autowired
    private NovelProjectRepository novelProjectRepository;
    @Autowired
    private INovelProjectDao novelProjectDao;
    @Autowired
    private IOutlineNodeDao outlineNodeDao;
    @Autowired
    private IChapterPlanDao chapterPlanDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertProject();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void shouldCarryChapterOneCompressionMemoryIntoChapterTwoPlanning() {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(PROJECT_CODE);
        insertOutline(project.getId());
        insertChapterPlan(project.getId(), "arc-1", 1);
        insertChapterPlan(project.getId(), "arc-2", 2);

        ChapterMemoryVO chapterOneMemory = new ChapterMemoryVO(
                1,
                "第一章压缩摘要",
                List.of("林澈转移到旧渡口", "林澈决定追查残页来源"),
                List.of("残页来源未知"),
                "门外响起敲门声"
        );
        chapterPersistRepository.persist(
                PROJECT_CODE,
                1,
                "第一章正文",
                chapterOneMemory
        );

        RecordingPlanningModelPort model = new RecordingPlanningModelPort();
        PlanningService planningService = new PlanningService(
                model,
                new NoopPlanningDraftRepository(),
                planningRepository
        );
        planningService.generateChapterPlan(PROJECT_CODE, 2, "承接第一章结尾");

        List<ChapterMemoryVO> memories = planningRepository
                .findRecentChapterMemories(PROJECT_CODE, 2, 5);
        assertThat(memories).singleElement().satisfies(memory -> {
            assertThat(memory.getChapterNumber()).isEqualTo(1);
            assertThat(memory.getShortSummary()).isEqualTo("第一章压缩摘要");
            assertThat(memory.getKeyEvents()).containsExactly(
                    "林澈转移到旧渡口", "林澈决定追查残页来源");
            assertThat(memory.getUnresolved()).containsExactly("残页来源未知");
            assertThat(memory.getEndingHook()).isEqualTo("门外响起敲门声");
        });
        ChapterHistoryVO contextHistory = contextRepository.loadHistory(PROJECT_CODE, 2);
        assertThat(contextHistory.getRecentMemories()).singleElement()
                .extracting(ChapterMemoryVO::getKeyEvents)
                .isEqualTo(List.of("林澈转移到旧渡口", "林澈决定追查残页来源"));
        assertThat(model.userPrompt)
                .contains(
                        "【近期剧情记忆】",
                        "第 1 章",
                        "摘要：第一章压缩摘要",
                        "关键事件：",
                        "林澈转移到旧渡口",
                        "林澈决定追查残页来源",
                        "未解决问题：",
                        "残页来源未知",
                        "结尾钩子：门外响起敲门声"
                );
        System.out.printf(
                "ChapterMemoryCrossChapterPlanningTest chapter=2 memoryChapter=%d keyEvents=%s unresolved=%s%n",
                memories.get(0).getChapterNumber(),
                memories.get(0).getKeyEvents(),
                memories.get(0).getUnresolved());
    }

    @Test
    void shouldRemovePreviousChapterMemoryWhenPreviousChapterIsDeleted() {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(PROJECT_CODE);
        insertOutline(project.getId());
        insertChapterPlan(project.getId(), "arc-1", 1);

        ChapterMemoryVO memory = new ChapterMemoryVO(
                1,
                "上一章摘要",
                List.of("林澈转移到旧渡口", "林澈决定追查残页来源"),
                List.of("残页来源未知"),
                "旧渡口传来钟声"
        );
        chapterPersistRepository.persist(PROJECT_CODE, 1, "上一章正文", memory);

        assertThat(contextRepository.loadHistory(PROJECT_CODE, 2).getRecentMemories())
                .singleElement()
                .extracting(ChapterMemoryVO::getKeyEvents)
                .isEqualTo(List.of("林澈转移到旧渡口", "林澈决定追查残页来源"));

        novelProjectRepository.deleteChapter(PROJECT_CODE, 1);

        assertThat(contextRepository.loadHistory(PROJECT_CODE, 2).getRecentMemories())
                .isEmpty();
        assertThat(planningRepository.findRecentChapterMemories(PROJECT_CODE, 2, 5))
                .isEmpty();
        assertThat(novelProjectDao.queryByProjectCode(PROJECT_CODE).getCurrentChapterNumber())
                .isZero();
        System.out.println("删除上一章后 Context 与 Planning 均不再读取该章 ChapterMemory");
    }

    private void insertProject() {
        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(PROJECT_CODE);
        project.setTitle("跨章节记忆测试");
        project.setGenre("悬疑");
        project.setTargetChapterCount(2);
        project.setWordsPerChapter(2000);
        project.setCurrentChapterNumber(0);
        project.setStatus("DRAFT");
        novelProjectDao.insert(project);
    }

    private void insertOutline(Long projectId) {
        OutlineNodePO book = new OutlineNodePO();
        book.setProjectId(projectId);
        book.setNodeCode("book-1");
        book.setNodeKind("BOOK");
        book.setSequenceNo(1);
        book.setStartChapter(1);
        book.setEndChapter(2);
        book.setTitle("跨章节记忆测试");
        book.setSummary("跨章节记忆测试");
        book.setStatus("READY");
        outlineNodeDao.insert(book);

        for (int chapterNumber = 1; chapterNumber <= 2; chapterNumber++) {
            OutlineNodePO arc = new OutlineNodePO();
            arc.setProjectId(projectId);
            arc.setParentId(book.getId());
            arc.setNodeCode("arc-" + chapterNumber);
            arc.setNodeKind("ARC");
            arc.setSequenceNo(chapterNumber);
            arc.setStartChapter(chapterNumber);
            arc.setEndChapter(chapterNumber);
            arc.setTitle("第" + chapterNumber + "章剧情");
            arc.setSummary("林澈继续追查残页来源");
            arc.setStatus("READY");
            outlineNodeDao.insert(arc);
        }
    }

    private void insertChapterPlan(Long projectId, String outlineNodeCode, int chapterNumber) {
        Long outlineNodeId = jdbcTemplate.queryForObject(
                "SELECT id FROM outline_node WHERE project_id = ? AND node_code = ?",
                Long.class, projectId, outlineNodeCode);
        ChapterPlanPO chapterPlan = new ChapterPlanPO();
        chapterPlan.setProjectId(projectId);
        chapterPlan.setOutlineNodeId(outlineNodeId);
        chapterPlan.setChapterNumber(chapterNumber);
        chapterPlan.setTitle("第" + chapterNumber + "章");
        chapterPlan.setSummary(chapterNumber == 1 ? "第一章" : "林澈承接残页线索");
        chapterPlan.setStatus("READY");
        chapterPlanDao.insert(chapterPlan);
    }

    private void cleanUp() {
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, PROJECT_CODE);
    }

    private static final class RecordingPlanningModelPort implements IPlanningModelPort {
        private String userPrompt;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            this.userPrompt = userPrompt;
            return (T) new ChapterPlanDraftVO("第二章", "承接第一章记忆继续追查残页来源");
        }
    }

    private static final class NoopPlanningDraftRepository implements IPlanningDraftRepository {
        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            return new PlanningDraftVO(
                    "chapter-memory-draft", projectCode, draftType, payload,
                    Instant.parse("2026-09-02T00:00:00Z")
            );
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return Optional.empty();
        }

        @Override
        public void remove(String projectCode, String draftId) {
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }

}
