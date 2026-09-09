package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.model.valobj.ChildOutlineDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.planning.service.PlanningService;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
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

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=${planning.repository.test.url:jdbc:mysql://127.0.0.1:3306/novel_agent}",
                "spring.datasource.username=${planning.repository.test.username:root}",
                "spring.datasource.password=${planning.repository.test.password:123456}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.ai.openai.api-key=test-only-placeholder"
        }
)
@ActiveProfiles("test")
class PlanningRepositoryTest {

    private final String projectCode = "planning-repository-test-" + UUID.randomUUID();

    @Autowired
    private PlanningRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IStoryChapterDao storyChapterDao;

    @Autowired
    private IStorySummaryDao storySummaryDao;

    private Long projectId;

    @BeforeEach
    void setUpProject() {
        jdbcTemplate.update("""
                INSERT INTO novel_project
                    (project_code, title, genre, target_chapter_count,
                     words_per_chapter, current_chapter_number, status)
                VALUES (?, 'Planning repository test', '玄幻', 20, 2000, 0, 'DRAFT')
                """, projectCode);
        projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM novel_project WHERE project_code = ?", Long.class, projectCode);
    }

    @AfterEach
    void cleanUpProject() {
        TestProjectDataCleanup.deleteProject(jdbcTemplate, projectId);
    }

    @Test
    void shouldManageGenericOutlineTreeWithoutLegacyFlowBranches() {
        OutlineNodeVO book = node(
                "book-1", null, "BOOK", 1, 1, 20, "全书", "全书总纲", "PLANNED");
        OutlineNodeVO savedBook = repository.saveOutline(projectCode, book);
        System.out.printf("saved root outline: project=%s, node=%s%n", projectCode, savedBook.nodeCode());

        assertThat(savedBook.nodeCode()).isEqualTo("book-1");
        assertThat(repository.findRootOutline(projectCode).nodeKind())
                .isEqualTo(OutlineNodeKindEnum.BOOK);
        assertThat(repository.findOutline(projectCode, "book-1").title()).isEqualTo("全书");
        assertThat(repository.nextSequence(projectCode, null)).isEqualTo(2);

        List<OutlineNodeVO> savedChildren = List.of(
                repository.saveOutline(
                        projectCode,
                        node("volume-1", "book-1", "VOLUME", 1, 1, 10, "第一卷", "卷一", "PLANNED")
                ),
                repository.saveOutline(
                        projectCode,
                        node("volume-2", "book-1", "VOLUME", 2, 11, 20, "第二卷", "卷二", "PLANNED")
                )
        );
        System.out.printf("saved child outlines: count=%d, codes=%s%n",
                savedChildren.size(), savedChildren.stream().map(OutlineNodeVO::nodeCode).toList());

        assertThat(savedChildren).extracting(OutlineNodeVO::nodeCode)
                .containsExactly("volume-1", "volume-2");
        assertThat(repository.listChildren(projectCode, "book-1"))
                .extracting(OutlineNodeVO::nodeCode)
                .containsExactly("volume-1", "volume-2");
        assertThat(repository.nextSequence(projectCode, "book-1")).isEqualTo(3);
        assertThat(repository.listOutlines(projectCode))
                .extracting(OutlineNodeVO::nodeCode)
                .containsExactly("book-1", "volume-1", "volume-2");

        repository.updateOutline(
                projectCode,
                node("volume-1", "book-1", "VOLUME", 1, 1, 10,
                        "第一卷（置顶）", "置顶后的卷大纲", "PLANNED")
        );
        List<OutlineNodeVO> reordered = repository.listChildren(projectCode, "book-1");
        assertThat(reordered).extracting(OutlineNodeVO::nodeCode)
                .containsExactly("volume-1", "volume-2");
        assertThat(reordered).extracting(OutlineNodeVO::sequenceNo)
                .containsExactly(1, 2);
        System.out.printf("reordered siblings: parent=book-1, codes=%s, sequence=%s%n",
                reordered.stream().map(OutlineNodeVO::nodeCode).toList(),
                reordered.stream().map(OutlineNodeVO::sequenceNo).toList());

        OutlineNodeVO movedVolume = repository.reorderOutline(projectCode, "volume-2", 1);
        assertThat(movedVolume.sequenceNo()).isEqualTo(1);
        assertThat(repository.listChildren(projectCode, "book-1"))
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("volume-2", 1),
                        org.assertj.core.groups.Tuple.tuple("volume-1", 2)
                );
        System.out.printf("reorder endpoint persistence: moved=%s, targetSequence=%d%n",
                movedVolume.nodeCode(), movedVolume.sequenceNo());

        repository.updateOutline(
                projectCode,
                node("volume-1", "book-1", "VOLUME", 99, 1, 10,
                        "第一卷（末尾）", "末尾后的卷大纲", "PLANNED")
        );
        List<OutlineNodeVO> clamped = repository.listChildren(projectCode, "book-1");
        assertThat(clamped).extracting(OutlineNodeVO::nodeCode)
                .containsExactly("volume-2", "volume-1");
        assertThat(clamped).extracting(OutlineNodeVO::sequenceNo)
                .containsExactly(1, 2);

        List<ChapterOutlineVO> plans = java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(chapterNumber -> new ChapterOutlineVO(
                        chapterNumber, "book-1", "第" + chapterNumber + "章",
                        "第" + chapterNumber + "章计划", "PLANNED"
                ))
                .toList();
        plans.forEach(plan -> repository.upsertChapterPlan(projectCode, plan));
        assertThat(repository.findChapterPlan(projectCode, 2).getOutlineNodeId())
                .isEqualTo(projectNodeId("book-1"));
        assertThat(repository.listChapterPlans(projectCode)).hasSize(3);
        System.out.printf("loaded individual chapter plans: count=%d, outline=%s%n",
                repository.listChapterPlans(projectCode).size(), "book-1");

        Long chapterPlanId = repository.findChapterPlan(projectCode, 2).getId();
        ChapterOutlineVO regenerated = repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(
                        2, "book-1", "第二章（重新生成）", "重新生成后的章节计划", "READY"
                )
        );
        ChapterPlanPO replacedChapterPlan = repository.findChapterPlan(projectCode, 2);
        System.out.printf("upserted single chapter plan: chapter=%d, id=%d, status=%s%n",
                replacedChapterPlan.getChapterNumber(), replacedChapterPlan.getId(),
                replacedChapterPlan.getStatus());
        assertThat(replacedChapterPlan.getId()).isEqualTo(chapterPlanId);
        assertThat(regenerated)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode,
                        ChapterOutlineVO::title, ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(2, "book-1", "第二章（重新生成）", "重新生成后的章节计划", "READY");
        assertThat(replacedChapterPlan)
                .extracting(ChapterPlanPO::getTitle, ChapterPlanPO::getSummary,
                        ChapterPlanPO::getStatus)
                .containsExactly("第二章（重新生成）", "重新生成后的章节计划", "READY");
        assertThat(repository.listChapterPlans(projectCode))
                .extracting(ChapterPlanPO::getChapterNumber)
                .containsExactly(1, 2, 3);
        System.out.println("preserved single chapter plan id during regenerate: chapter=2");

        assertThat(repository.findChapterPlanOutlines(projectCode))
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode,
                        ChapterOutlineVO::title, ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(
                                1, "book-1", "第1章", "第1章计划", "PLANNED"
                        ),
                        org.assertj.core.api.Assertions.tuple(
                                2, "book-1", "第二章（重新生成）", "重新生成后的章节计划", "READY"
                        ),
                        org.assertj.core.api.Assertions.tuple(
                                3, "book-1", "第3章", "第3章计划", "PLANNED"
                        )
                );
        ChapterOutlineVO manuallyUpdated = repository.updateChapterPlan(
                projectCode,
                2,
                new ChapterOutlineVO(999, null, "人工编辑标题", "人工编辑摘要", null)
        );
        System.out.printf("manually updated chapter plan: chapter=%d, status=%s%n",
                manuallyUpdated.chapterNumber(), manuallyUpdated.status());
        assertThat(manuallyUpdated)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode,
                        ChapterOutlineVO::title, ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(2, "book-1", "人工编辑标题", "人工编辑摘要", "READY");
        assertThat(repository.findChapterPlan(projectCode, 2).getId())
                .isEqualTo(chapterPlanId);

        ChapterPlanPO chapterPlan = repository.findChapterPlan(projectCode, 2);
        chapterPlan.setTitle("第二章（修订）");
        chapterPlan.setSummary("修订后的章节计划");
        repository.updateChapterPlan(projectCode, chapterPlan);
        assertThat(repository.findChapterPlan(projectCode, 2))
                .extracting(ChapterPlanPO::getTitle, ChapterPlanPO::getSummary)
                .containsExactly("第二章（修订）", "修订后的章节计划");
        assertThat(repository.updateChapterPlanStatus(projectCode, 2, "READY")).isEqualTo(1);
        assertThat(repository.findChapterPlan(projectCode, 2).getStatus()).isEqualTo("READY");
        System.out.printf("updated chapter plan: chapter=%d, status=%s%n",
                2, repository.findChapterPlan(projectCode, 2).getStatus());
        repository.updateOutline(
                projectCode,
                node("volume-1", "book-1", "VOLUME", 1, 1, 10,
                        "第一卷（修订）", "修订后的卷大纲", "READY")
        );
        assertThat(repository.findOutline(projectCode, "volume-1"))
                .extracting(OutlineNodeVO::title, OutlineNodeVO::summary, OutlineNodeVO::status)
                .containsExactly("第一卷（修订）", "修订后的卷大纲", "READY");

        repository.deleteOutline(projectCode, "volume-1");
        System.out.println("deleted leaf outline: volume-1");
        assertThat(repository.listChildren(projectCode, "book-1"))
                .extracting(OutlineNodeVO::nodeCode)
                .containsExactly("volume-2");
    }

    @Test
    void shouldRestoreTailVolumeRangeForImmediateChapterGenerationAndRepeatCreateDelete() {
        repository.saveOutline(
                projectCode,
                node("book-tail", null, "BOOK", 1, 1, 20,
                        "尾卷恢复测试", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("VOL_001", "book-tail", "VOLUME", 1, 1, 20,
                        "第一卷", "第一卷卷纲", "READY")
        );
        NoopPlanningDraftRepository drafts = new NoopPlanningDraftRepository();
        PlanningService planningService = new PlanningService(
                new RecordingPlanningModelPort(),
                drafts,
                repository
        );
        PlanningDraftVO firstChapterDraft = planningService.generateNextOutline(
                projectCode, "VOL_001", null
        );
        OutlineNodeVO firstChapterNode = (OutlineNodeVO) firstChapterDraft.payload();
        System.out.printf(
                "generated first chapter: code=%s, chapter=%d%n",
                firstChapterNode.nodeCode(), firstChapterNode.startChapter()
        );
        assertThat(firstChapterNode)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::startChapter,
                        OutlineNodeVO::endChapter)
                .containsExactly("ARC_001", 1, 1);
        planningService.confirmNextOutline(
                projectCode, "VOL_001", firstChapterDraft.draftId(),
                "第一章", "第一章剧情"
        );

        OutlineNodeVO firstNextVolume = planningService.createNextVolume(
                projectCode, "book-tail"
        );
        System.out.printf(
                "created tail volume: code=%s, sequence=%d, range=%d-%d%n",
                firstNextVolume.nodeCode(), firstNextVolume.sequenceNo(),
                firstNextVolume.startChapter(), firstNextVolume.endChapter()
        );
        assertThat(firstNextVolume)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::startChapter, OutlineNodeVO::endChapter)
                .containsExactly("VOL_002", 2, 2, 20);
        assertThat(repository.findOutline(projectCode, "VOL_001"))
                .extracting(OutlineNodeVO::startChapter, OutlineNodeVO::endChapter)
                .containsExactly(1, 1);

        planningService.deleteOutlineNode(projectCode, firstNextVolume.nodeCode());
        OutlineNodeVO restoredVolume = repository.findOutline(projectCode, "VOL_001");
        System.out.printf(
                "deleted empty tail volume: remaining=%s, range=%d-%d, siblings=%s%n",
                restoredVolume.nodeCode(), restoredVolume.startChapter(),
                restoredVolume.endChapter(),
                repository.listChildren(projectCode, "book-tail").stream()
                        .map(child -> child.nodeCode() + "#" + child.sequenceNo())
                        .toList()
        );
        assertThat(restoredVolume)
                .extracting(OutlineNodeVO::startChapter, OutlineNodeVO::endChapter,
                        OutlineNodeVO::sequenceNo)
                .containsExactly(1, 20, 1);
        assertThat(repository.listChildren(projectCode, "book-tail"))
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("VOL_001", 1)
                );

        PlanningDraftVO nextChapter = planningService.generateNextOutline(
                projectCode, "VOL_001", null
        );
        OutlineNodeVO nextChapterNode = (OutlineNodeVO) nextChapter.payload();
        System.out.printf(
                "immediate next chapter generation after tail deletion: code=%s, chapter=%d%n",
                nextChapterNode.nodeCode(), nextChapterNode.startChapter()
        );
        assertThat(nextChapterNode)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::nodeKind,
                        OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::startChapter, OutlineNodeVO::endChapter)
                .containsExactly("ARC_002", OutlineNodeKindEnum.ARC, 2, 2, 2);

        OutlineNodeVO secondNextVolume = planningService.createNextVolume(
                projectCode, "book-tail"
        );
        assertThat(secondNextVolume)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::startChapter, OutlineNodeVO::endChapter)
                .containsExactly("VOL_002", 2, 2, 20);
        assertThat(repository.listChildren(projectCode, "book-tail"))
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::startChapter, OutlineNodeVO::endChapter)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("VOL_001", 1, 1, 1),
                        org.assertj.core.api.Assertions.tuple("VOL_002", 2, 2, 20)
                );

        planningService.deleteOutlineNode(projectCode, secondNextVolume.nodeCode());
        assertThat(repository.findOutline(projectCode, "VOL_001"))
                .extracting(OutlineNodeVO::startChapter, OutlineNodeVO::endChapter,
                        OutlineNodeVO::sequenceNo)
                .containsExactly(1, 20, 1);

        planningService.deleteOutlineNode(projectCode, "ARC_001");
        assertThat(repository.findOutline(projectCode, "VOL_001"))
                .extracting(OutlineNodeVO::startChapter, OutlineNodeVO::endChapter)
                .containsExactly(1, 20);
        System.out.println("create-delete-create repeated with contiguous range and stable sibling sequence");
    }

    @Test
    void shouldQueryConfirmedChapterPlansWithOneOutlineNodeCode() {
        repository.saveOutline(
                projectCode,
                node("book-20", null, "BOOK", 1, 1, 20, "全书", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-20", "book-20", "ARC", 1, 1, 1, "目标剧情弧", "目标剧情弧概要", "READY")
        );
        IntStream.rangeClosed(1, 20).forEach(chapterNumber -> repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(
                        chapterNumber,
                        "arc-20",
                        "第" + chapterNumber + "章",
                        "第" + chapterNumber + "章计划",
                        "READY"
                )
        ));

        List<ChapterOutlineVO> queriedPlans = repository.findChapterPlanOutlines(projectCode);
        System.out.printf(
                "queried confirmed chapter plans: count=%d, outlineCodes=%s%n",
                queriedPlans.size(), queriedPlans.stream()
                        .map(ChapterOutlineVO::outlineNodeCode)
                        .distinct()
                        .toList()
        );
        assertThat(queriedPlans).hasSize(20);
        assertThat(queriedPlans)
                .extracting(ChapterOutlineVO::chapterNumber)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(queriedPlans)
                .extracting(ChapterOutlineVO::outlineNodeCode)
                .containsOnly("arc-20");
    }

    @Test
    void shouldMigrateLegacyBookArcsAndKeepChapterPlanReferences() {
        repository.saveOutline(
                projectCode,
                node("book-migration", null, "BOOK", 1, 1, 20,
                        "全书", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-migration-1", "book-migration", "ARC", 1, 1, 1,
                        "第一章剧情", "第一章剧情概要", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-migration-2", "book-migration", "ARC", 2, 2, 2,
                        "第二章剧情", "第二章剧情概要", "READY")
        );
        repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(1, "arc-migration-1", "第一章", "第一章计划", "READY")
        );
        repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(2, "arc-migration-2", "第二章", "第二章计划", "READY")
        );

        List<OutlineNodeVO> migratedTree = repository.listOutlines(projectCode);
        long migrated = migratedTree.stream()
                .filter(node -> node.nodeKind() == OutlineNodeKindEnum.ARC
                        && "VOL_001".equals(node.parentNodeCode()))
                .count();
        System.out.printf(
                "legacy ARC migration on outline read: migrated=%d, defaultVolume=%s%n",
                migrated, "VOL_001"
        );
        assertThat(migrated).isEqualTo(2);

        assertThat(migratedTree)
                .filteredOn(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::parentNodeCode,
                        OutlineNodeVO::sequenceNo, OutlineNodeVO::title)
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        "VOL_001", "book-migration", 1, "卷一"
                ));
        assertThat(migratedTree)
                .filteredOn(node -> node.nodeKind() == OutlineNodeKindEnum.ARC)
                .extracting(OutlineNodeVO::parentNodeCode)
                .containsOnly("VOL_001");
        assertThat(repository.findChapterPlanOutlines(projectCode))
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(1, "arc-migration-1"),
                        org.assertj.core.api.Assertions.tuple(2, "arc-migration-2")
                );

        assertThat(repository.migrateLegacyBookArcs(projectCode)).isZero();
        assertThat(repository.listOutlines(projectCode))
                .filteredOn(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME)
                .hasSize(1);
        System.out.println("legacy ARC migration is idempotent: secondRun=0, volumeCount=1");
    }

    @Test
    void shouldReuseExistingDefaultVolumeWhenMigratingLegacyBookArcs() {
        repository.saveOutline(
                projectCode,
                node("book-existing-volume", null, "BOOK", 1, 1, 20,
                        "全书", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("volume-existing", "book-existing-volume", "VOLUME", 1, 1, 20,
                        "第一卷", "已有卷一", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-existing-volume", "book-existing-volume", "ARC", 1, 3, 3,
                        "第三章剧情", "第三章剧情概要", "READY")
        );

        int migrated = repository.migrateLegacyBookArcs(projectCode);
        List<OutlineNodeVO> tree = repository.listOutlines(projectCode);
        System.out.printf(
                "existing default volume reused: migrated=%d, volumeCount=%d, arcParent=%s%n",
                migrated,
                tree.stream().filter(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME).count(),
                tree.stream().filter(node -> "arc-existing-volume".equals(node.nodeCode()))
                        .findFirst().orElseThrow().parentNodeCode()
        );
        assertThat(migrated).isEqualTo(1);
        assertThat(tree)
                .filteredOn(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME)
                .extracting(OutlineNodeVO::nodeCode)
                .containsExactly("volume-existing");
        assertThat(tree)
                .filteredOn(node -> "arc-existing-volume".equals(node.nodeCode()))
                .extracting(OutlineNodeVO::parentNodeCode)
                .containsExactly("volume-existing");
        assertThat(repository.migrateLegacyBookArcs(projectCode)).isZero();
    }

    @Test
    void shouldMarkExistingUnplannedVolumeWithoutMovingArcsOrChapterPlans() {
        repository.saveOutline(
                projectCode,
                node("book-existing-unplanned", null, "BOOK", 1, 1, 20,
                        "全书", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("VOL_001", "book-existing-unplanned", "VOLUME", 1, 1, 20,
                        "卷一", "", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-existing-child", "VOL_001", "ARC", 1, 3, 3,
                        "第三章剧情", "第三章剧情概要", "READY")
        );
        repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(3, "arc-existing-child", "第三章剧情", "第三章计划", "READY")
        );
        Long parentIdBefore = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM outline_node WHERE project_id = ? AND node_code = ?",
                Long.class, projectId, "arc-existing-child");

        int migrated = repository.migrateLegacyBookArcs(projectCode);
        Long parentIdAfter = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM outline_node WHERE project_id = ? AND node_code = ?",
                Long.class, projectId, "arc-existing-child");
        OutlineNodeVO volume = repository.findOutline(projectCode, "VOL_001");

        System.out.printf(
                "historical volume compatibility: migrated=%d, status=%s, parentId=%d->%d%n",
                migrated, volume.status(), parentIdBefore, parentIdAfter
        );
        assertThat(migrated).isZero();
        assertThat(volume.status()).isEqualTo("UNPLANNED");
        assertThat(parentIdAfter).isEqualTo(parentIdBefore);
        assertThat(repository.findOutline(projectCode, "arc-existing-child").parentNodeCode())
                .isEqualTo("VOL_001");
        assertThat(repository.findChapterPlanOutlines(projectCode))
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode)
                .containsExactly(org.assertj.core.api.Assertions.tuple(3, "arc-existing-child"));
        System.out.println("historical volume marked UNPLANNED without rebuilding ARC or changing ChapterPlan");
    }

    @Test
    void shouldInsertOrUpdateOneChapterPlanAndProtectCompletedPlan() {
        repository.saveOutline(
                projectCode,
                node("book-single", null, "BOOK", 1, 1, 20,
                        "全书", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-single", "book-single", "ARC", 1, 17, 17,
                        "当前剧情段", "当前剧情段概要", "READY")
        );

        ChapterOutlineVO inserted = repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(17, "arc-single", "初始标题", "初始摘要", "READY")
        );
        System.out.printf(
                "single chapter plan inserted: chapter=%d, status=%s%n",
                inserted.chapterNumber(), inserted.status()
        );
        assertThat(inserted)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::outlineNodeCode,
                        ChapterOutlineVO::title, ChapterOutlineVO::summary,
                        ChapterOutlineVO::status)
                .containsExactly(17, "arc-single", "初始标题", "初始摘要", "READY");

        ChapterOutlineVO updated = repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(17, "arc-single", "确认标题", "确认摘要", "READY")
        );
        System.out.printf(
                "single chapter plan updated: chapter=%d, title=%s, status=%s%n",
                updated.chapterNumber(), updated.title(), updated.status()
        );
        assertThat(repository.listChapterPlans(projectCode)).hasSize(1);
        assertThat(updated)
                .extracting(ChapterOutlineVO::title, ChapterOutlineVO::summary,
                        ChapterOutlineVO::status)
                .containsExactly("确认标题", "确认摘要", "READY");

        jdbcTemplate.update(
                "UPDATE chapter_plan SET status = 'COMPLETED' "
                        + "WHERE project_id = ? AND chapter_number = 17",
                projectId
        );
        assertThatThrownBy(() -> repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(17, "arc-single", "覆盖标题", "覆盖摘要", "READY")
        )).isInstanceOf(RuntimeException.class);
        System.out.println("completed single chapter plan rejected from AI overwrite");

        ChapterOutlineVO manuallyUpdated = repository.updateChapterPlan(
                projectCode,
                17,
                new ChapterOutlineVO(999, null, "人工修改标题", "人工修改摘要", null)
        );
        System.out.printf(
                "completed single chapter plan manually updated: chapter=%d, status=%s%n",
                manuallyUpdated.chapterNumber(), manuallyUpdated.status()
        );
        assertThat(manuallyUpdated)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::title,
                        ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(17, "人工修改标题", "人工修改摘要", "COMPLETED");
    }

    @Test
    void shouldPreferActualStorySummaryAndFallbackToPreviousChapterPlan() {
        repository.saveOutline(
                projectCode,
                node("book-summary", null, "BOOK", 1, 1, 20,
                        "全书", "全书总纲", "READY")
        );
        repository.saveOutline(
                projectCode,
                node("arc-summary", "book-summary", "ARC", 1, 16, 16,
                        "当前剧情段", "当前剧情段概要", "READY")
        );
        repository.upsertChapterPlan(
                projectCode,
                new ChapterOutlineVO(
                        16, "arc-summary", "第16章", "上一章计划摘要", "READY"
                )
        );

        assertThat(repository.findPreviousChapterSummary(projectCode, 17))
                .isEqualTo("上一章计划摘要");
        System.out.println("previous chapter summary fallback: source=chapter_plan, chapter=16");

        ChapterPlanPO plan = repository.findChapterPlan(projectCode, 16);
        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(projectId);
        chapter.setChapterPlanId(plan.getId());
        chapter.setChapterNumber(16);
        chapter.setTitle("第16章");
        chapter.setContent("人工改写后的正文");
        chapter.setWordCount(8);
        chapter.setStatus("FINALIZED");
        assertThat(storyChapterDao.insertOrUpdate(chapter)).isEqualTo(1);

        StorySummaryPO summary = new StorySummaryPO();
        summary.setProjectId(projectId);
        summary.setChapterId(chapter.getId());
        summary.setChapterNumber(16);
        summary.setShortSummary("正文实际结果");
        summary.setStatus("VERIFIED");
        assertThat(storySummaryDao.insertOrUpdate(summary)).isEqualTo(1);

        assertThat(repository.findPreviousChapterSummary(projectCode, 17))
                .isEqualTo("正文实际结果");
        System.out.println("previous chapter summary precedence: source=story_summary, value=正文实际结果");

        summary.setShortSummary("人工改写后的实际结果");
        assertThat(storySummaryDao.insertOrUpdate(summary)).isGreaterThan(0);
        assertThat(repository.findPreviousChapterSummary(projectCode, 17))
                .isEqualTo("人工改写后的实际结果");
        System.out.println("updated story_summary immediately used for next chapter planning");

        summary.setKeyEventsJson("[\"取得带有议会徽记的残页\",\"钟楼封锁\"]");
        summary.setUnresolvedQuestionsJson("[\"议会为何追查父亲\"]");
        summary.setEndingHook("残页出现议会徽记");
        assertThat(storySummaryDao.insertOrUpdate(summary)).isGreaterThan(0);

        List<ChapterMemoryVO> memories =
                repository.findRecentChapterMemories(projectCode, 17, 10);
        assertThat(memories).singleElement().satisfies(memory -> {
            assertThat(memory.getChapterNumber()).isEqualTo(16);
            assertThat(memory.getShortSummary()).isEqualTo("人工改写后的实际结果");
            assertThat(memory.getKeyEvents())
                    .containsExactly("取得带有议会徽记的残页", "钟楼封锁");
            assertThat(memory.getUnresolved()).containsExactly("议会为何追查父亲");
            assertThat(memory.getEndingHook()).isEqualTo("残页出现议会徽记");
        });
        System.out.printf(
                "recent chapter memory: chapter=%d, keyEvents=%s, unresolved=%s%n",
                memories.get(0).getChapterNumber(),
                memories.get(0).getKeyEvents(),
                memories.get(0).getUnresolved());

        assertThat(storySummaryDao.markStale(projectId, 16)).isEqualTo(1);
        assertThat(repository.findRecentChapterMemories(projectCode, 17, 10)).isEmpty();
        assertThat(repository.findPreviousChapterSummary(projectCode, 17))
                .isEqualTo("上一章计划摘要");
        System.out.println("stale story_summary fallback: source=chapter_plan, chapter=16");
    }

    private OutlineNodeVO node(
            String code,
            String parentCode,
            String kind,
            int sequence,
            int startChapter,
            int endChapter,
            String title,
            String summary,
            String status
    ) {
        return new OutlineNodeVO(
                code, parentCode, OutlineNodeKindEnum.valueOf(kind), sequence,
                title, summary, startChapter, endChapter, status
        );
    }

    private Long projectNodeId(String nodeCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outline_node WHERE project_id = ? AND node_code = ?",
                Long.class, projectId, nodeCode);
    }

    private static final class RecordingPlanningModelPort implements IPlanningModelPort {
        private int calls;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            if (responseType != ChildOutlineDraftVO.class) {
                throw new AssertionError("unexpected planning response type: " + responseType);
            }
            String title = calls++ == 0 ? "第一章" : "第二章";
            return (T) new ChildOutlineDraftVO(title, "承接第一章继续推进剧情");
        }
    }

    private static final class NoopPlanningDraftRepository implements IPlanningDraftRepository {
        private final Map<String, PlanningDraftVO> drafts = new HashMap<>();

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            PlanningDraftVO draft = new PlanningDraftVO(
                    "tail-volume-draft", projectCode, draftType, payload,
                    java.time.Instant.parse("2026-09-08T00:00:00Z")
            );
            drafts.put(draft.draftId(), draft);
            return draft;
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return Optional.ofNullable(drafts.get(draftId));
        }

        @Override
        public void remove(String projectCode, String draftId) {
            drafts.remove(draftId);
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }
}
