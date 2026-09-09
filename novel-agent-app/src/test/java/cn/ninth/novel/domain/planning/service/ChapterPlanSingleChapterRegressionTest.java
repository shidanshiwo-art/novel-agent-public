package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.model.valobj.ChapterPlanDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.infrastructure.adapter.repository.ChapterPersistRepository;
import cn.ninth.novel.infrastructure.adapter.repository.InMemoryPlanningDraftRepository;
import cn.ninth.novel.infrastructure.adapter.repository.PlanningRepository;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import cn.ninth.novel.infrastructure.support.TestProjectDataCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=${chapter.plan.regression.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${chapter.plan.regression.test.username:root}",
                "spring.datasource.password=${chapter.plan.regression.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class ChapterPlanSingleChapterRegressionTest {

    private final String projectCode = "chapter-plan-regression-" + UUID.randomUUID();

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
    private PlanningRepository planningRepository;
    @Autowired
    private ChapterPersistRepository chapterPersistRepository;
    @Autowired
    private InMemoryPlanningDraftRepository draftRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long projectId;

    @BeforeEach
    void setUp() {
        cleanUp();

        NovelProjectPO project = new NovelProjectPO();
        project.setProjectCode(projectCode);
        project.setTitle("单章回归测试作品");
        project.setGenre("玄幻悬疑");
        project.setTargetChapterCount(300);
        project.setWordsPerChapter(2000);
        project.setCurrentChapterNumber(0);
        project.setStatus("ACTIVE");
        novelProjectDao.insert(project);
        projectId = project.getId();

        insertOutline("book-1", null, "BOOK", 1, 300,
                "全书方向", "寻找失忆真相，并在自我与城市之间作出选择。");
        insertOutline("volume-1", "book-1", "VOLUME", 1, 100,
                "青云宗卷", "主角进入青云宗寻找神器线索。");
        insertOutline("arc-17", "volume-1", "ARC", 1, 17, 17,
                "宗门大比", "宗门大比中与赵无极正面冲突。");
        insertOutline("arc-18", "volume-1", "ARC", 2, 18, 18,
                "大比余波", "宗门大比后的余波推动主角继续追查线索。");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void shouldRegressSingleChapterPlanToCompletedAndRollSummaryIntoNextChapter() {
        RecordingModelPort model = new RecordingModelPort();
        PlanningService service = new PlanningService(model, draftRepository, planningRepository);

        assertNoChapterPlans(18, 19, 20);

        PlanningDraftVO firstDraft = service.generateChapterPlan(
                projectCode, 17, "本章推进宗门大比冲突"
        );
        ChapterOutlineVO firstPayload = payload(firstDraft);
        System.out.printf(
                "regression generated chapter 17 draft: draftId=%s, node=%s, status=%s%n",
                firstDraft.draftId(), firstPayload.outlineNodeCode(), firstPayload.status()
        );
        assertThat(firstDraft.draftType()).isEqualTo("CHAPTER_PLAN");
        assertThat(firstPayload)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode,
                        ChapterOutlineVO::status)
                .containsExactly(17, "arc-17", "PLANNED");
        assertNoChapterPlans(18, 19, 20);

        service.confirmChapterPlan(
                projectCode, 17, firstDraft.draftId(),
                "第十七章（人工编辑）", "人工编辑后的第十七章计划"
        );
        ChapterPlanPO readyPlan = chapterPlan(17);
        System.out.printf(
                "regression confirmed chapter 17: status=%s, title=%s%n",
                readyPlan.getStatus(), readyPlan.getTitle()
        );
        assertThat(readyPlan)
                .extracting(ChapterPlanPO::getTitle, ChapterPlanPO::getSummary,
                        ChapterPlanPO::getStatus)
                .containsExactly("宗门大比", "人工编辑后的第十七章计划", "READY");
        assertNoChapterPlans(18, 19, 20);

        chapterPersistRepository.persist(
                projectCode,
                17,
                "第十七章正文初稿",
                chapterMemory("第十七章正文实际结果")
        );
        ChapterPlanPO completedPlan = chapterPlan(17);
        assertThat(completedPlan.getStatus()).isEqualTo("COMPLETED");
        assertThat(storyChapterDao.queryByProjectIdAndChapterNumber(projectId, 17))
                .extracting(StoryChapterPO::getContent)
                .isEqualTo("第十七章正文初稿");
        System.out.println("regression persisted chapter 17: ChapterPlan status=COMPLETED");

        ChapterOutlineVO manuallyUpdated = service.updateChapterPlan(
                projectCode,
                17,
                new ChapterOutlineVO(999, null, "宗门大比", "正文后修订的章节计划", null)
        );
        System.out.printf(
                "regression manually updated completed plan: chapter=%d, status=%s%n",
                manuallyUpdated.chapterNumber(), manuallyUpdated.status()
        );
        assertThat(manuallyUpdated)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::title,
                        ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(17, "宗门大比", "正文后修订的章节计划", "COMPLETED");

        PlanningDraftVO regenerateDraft = service.generateChapterPlan(
                projectCode, 17, "再次检查第十七章是否可以重新规划"
        );
        System.out.printf(
                "regression regenerated chapter 17 draft: draftId=%s, modelCalls=%d%n",
                regenerateDraft.draftId(), model.prompts.size()
        );
        assertThat(payload(regenerateDraft).chapterNumber()).isEqualTo(17);
        assertThat(chapterPlan(17).getStatus()).isEqualTo("COMPLETED");

        assertThatThrownBy(() -> service.confirmChapterPlan(
                projectCode, 17, regenerateDraft.draftId(),
                "AI 覆盖标题", "AI 覆盖摘要"
        )).isInstanceOf(RuntimeException.class);
        assertThat(draftRepository.find(projectCode, regenerateDraft.draftId())).isPresent();
        assertThat(chapterPlan(17))
                .extracting(ChapterPlanPO::getTitle, ChapterPlanPO::getSummary,
                        ChapterPlanPO::getStatus)
                .containsExactly("宗门大比", "正文后修订的章节计划", "COMPLETED");
        System.out.println("regression rejected chapter 17 AI confirm after COMPLETED while retaining Draft");

        StoryChapterPO revisedChapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(projectId, 17);
        revisedChapter.setContent("第十七章正文人工修订版");
        revisedChapter.setWordCount(12);
        storyChapterDao.insertOrUpdate(revisedChapter);

        StorySummaryPO revisedSummary = storySummaryDao
                .queryByProjectIdAndChapterNumber(projectId, 17);
        revisedSummary.setShortSummary("第十七章人工修订后的实际结果");
        revisedSummary.setStatus("VERIFIED");
        storySummaryDao.insertOrUpdate(revisedSummary);
        assertThat(storySummaryDao.queryByProjectIdAndChapterNumber(projectId, 17))
                .extracting(StorySummaryPO::getShortSummary)
                .isEqualTo("第十七章人工修订后的实际结果");
        System.out.println("regression updated chapter 17正文 and story_summary");

        assertNoChapterPlans(18, 19, 20);
        PlanningDraftVO chapter18Draft = service.generateChapterPlan(
                projectCode, 18, "承接第十七章实际结果"
        );
        ChapterOutlineVO chapter18Payload = payload(chapter18Draft);
        String chapter18Prompt = model.prompts.get(2);
        System.out.printf(
                "regression generated chapter 18 draft: chapter=%d, previousSummary=%s%n",
                chapter18Payload.chapterNumber(), "第十七章人工修订后的实际结果"
        );
        assertThat(chapter18Payload.chapterNumber()).isEqualTo(18);
        assertThat(chapter18Prompt)
                .contains("【上一章】", "第十七章人工修订后的实际结果")
                .doesNotContain("第十七章正文实际结果");
        assertNoChapterPlans(18, 19, 20);
    }

    private ChapterOutlineVO payload(PlanningDraftVO draft) {
        return (ChapterOutlineVO) draft.payload();
    }

    private ChapterPlanPO chapterPlan(int chapterNumber) {
        return chapterPlanDao.queryByChapterNumber(projectId, chapterNumber);
    }

    private void assertNoChapterPlans(int... chapterNumbers) {
        for (int chapterNumber : chapterNumbers) {
            assertThat(chapterPlanDao.queryByChapterNumber(projectId, chapterNumber))
                    .as("chapter %d should not require a pre-existing ChapterPlan", chapterNumber)
                    .isNull();
        }
    }

    private ChapterMemoryVO chapterMemory(String shortSummary) {
        return ChapterMemoryVO.builder()
                .chapterNumber(17)
                .shortSummary(shortSummary)
                .keyEvents(List.of(shortSummary))
                .build();
    }

    private void insertOutline(
            String nodeCode,
            String parentNodeCode,
            String nodeKind,
            int startChapter,
            int endChapter,
            String title,
            String summary
    ) {
        insertOutline(nodeCode, parentNodeCode, nodeKind, 1, startChapter, endChapter,
                title, summary);
    }

    private void insertOutline(
            String nodeCode,
            String parentNodeCode,
            String nodeKind,
            int sequenceNo,
            int startChapter,
            int endChapter,
            String title,
            String summary
    ) {
        OutlineNodePO node = new OutlineNodePO();
        node.setProjectId(projectId);
        node.setParentId(parentNodeCode == null
                ? null
                : outlineNodeDao.queryByCode(projectId, parentNodeCode).getId());
        node.setNodeCode(nodeCode);
        node.setNodeKind(nodeKind);
        node.setSequenceNo(sequenceNo);
        node.setStartChapter(startChapter);
        node.setEndChapter(endChapter);
        node.setTitle(title);
        node.setSummary(summary);
        node.setStatus("READY");
        outlineNodeDao.insert(node);
    }

    private void cleanUp() {
        draftRepository.removeByProject(projectCode);
        TestProjectDataCleanup.deleteProjectByCode(jdbcTemplate, projectCode);
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private final List<String> prompts = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            prompts.add(userPrompt);
            if (responseType != ChapterPlanDraftVO.class) {
                throw new AssertionError("unexpected response type: " + responseType);
            }
            int callNumber = prompts.size();
            return (T) new ChapterPlanDraftVO(
                    callNumber == 3 ? "第十八章 AI 草稿" : "第十七章 AI 草稿 " + callNumber,
                    callNumber == 3
                            ? "承接第十七章人工修订后的实际结果"
                            : "第十七章 AI 生成的章节计划 " + callNumber
            );
        }
    }
}
