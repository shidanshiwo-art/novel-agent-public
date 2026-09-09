package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualOutlineCrudTest {

    @Test
    void shouldCreateUpdateListAndDeleteOutlineWithoutCallingModel() {
        RecordingRepository repository = new RecordingRepository();
        ThrowingModelPort model = new ThrowingModelPort();
        PlanningService service = service(model, repository);

        OutlineNodeVO book = service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "全书大纲"
        ));
        service.createOutlineNode("novel-001", node(
                "volume", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "卷大纲"
        ));
        OutlineNodeVO updated = service.updateOutlineNode("novel-001", new OutlineNodeVO(
                "volume", "book", OutlineNodeKindEnum.VOLUME, 1,
                "第一卷（修订）", "修订后的卷大纲", 1, 20, "READY"
        ));
        service.deleteOutlineNode("novel-001", "volume");

        assertThat(service.listOutlineTree("novel-001"))
                .extracting(OutlineNodeVO::nodeCode)
                .containsExactly("book");
        assertThat(book.nodeKind()).isEqualTo(OutlineNodeKindEnum.BOOK);
        assertThat(updated.title()).isEqualTo("第一卷（修订）");
        assertThat(model.calls).isZero();
        System.out.printf(
                "ManualOutlineCrudTest CRUD project=novel-001 remaining=%s modelCalls=%d%n",
                service.listOutlineTree("novel-001").stream()
                        .map(OutlineNodeVO::nodeCode).toList(),
                model.calls
        );
    }

    @Test
    void shouldBindBookTitleToProjectTitleForManualCreateAndUpdate() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);

        OutlineNodeVO created = service.createOutlineNode("novel-001", nodeWithRange(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "外部传入的根标题", "全书大纲", 1, 100
        ));
        OutlineNodeVO updated = service.updateOutlineNode("novel-001", new OutlineNodeVO(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "尝试修改根标题", "修订后的全书大纲", 1, 100, "READY"
        ));

        System.out.printf(
                "BOOK title binding verified: created=%s, updated=%s%n",
                created.title(), updated.title()
        );
        assertThat(created.title()).isEqualTo("测试项目");
        assertThat(updated.title()).isEqualTo("测试项目");
    }

    @Test
    void shouldRejectInvalidRootAndParentRules() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                "book-2", null, OutlineNodeKindEnum.BOOK, 2, "第二本", "非法根"
        )), "一个项目只能有一个 BOOK");
        assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                "book-child", "book", OutlineNodeKindEnum.BOOK, 2, "子书", "非法子节点"
        )), "BOOK 不能作为 child");
          assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                  "volume-root", null, OutlineNodeKindEnum.VOLUME, 2, "卷", "非法根"
          )), "非 BOOK 节点必须有 parent");
          assertInfoContains(() -> service.createOutlineNode("novel-001", contentOnly(
                  null, "人工创建的书", "手动创建 BOOK 不属于本接口", 1, 100
          )), "parentNodeCode 不能为空");
          assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                  "arc-child", "missing", OutlineNodeKindEnum.ARC, 1, "剧情弧", "缺少父节点"
          )), "父节点不存在");
    }

    @Test
    void shouldEnforceBookVolumeArcHierarchy() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        assertInfoContains(() -> service.createOutlineNode("novel-001", nodeWithRange(
                "book-invalid-range", null, OutlineNodeKindEnum.BOOK, 1,
                "范围不完整的书", "非法全书范围", 1, 99
        )), "BOOK 必须覆盖全书范围");
        service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                "arc", "book", OutlineNodeKindEnum.ARC, 1, "第一章", "非法跳级"
        )), "BOOK 只能创建 VOLUME");

        service.createOutlineNode("novel-001", node(
                "volume", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "卷大纲"
        ));
        assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                "volume-child", "volume", OutlineNodeKindEnum.VOLUME, 1, "子卷", "非法同级嵌套"
        )), "VOLUME 只能创建 ARC");

        service.createOutlineNode("novel-001", node(
                "arc", "volume", OutlineNodeKindEnum.ARC, 1, "第一章", "章大纲"
        ));
        assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                "arc-child", "arc", OutlineNodeKindEnum.ARC, 1, "子章", "非法下级"
        )), "ARC 不能作为 parent");
        System.out.println("strict outline hierarchy verified: BOOK -> VOLUME -> ARC");
    }

    @Test
    void shouldRequireChildRangeInsideParentAndRejectSiblingOverlap() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", nodeWithRange(
                "volume-null", "book", OutlineNodeKindEnum.VOLUME, 1,
                "空范围卷", "非法", null, null
        )), "章节范围非法");

        service.createOutlineNode("novel-001", nodeWithRange(
                "volume-1", "book", OutlineNodeKindEnum.VOLUME, 1,
                "第一卷", "1-50", 1, 50
        ));
        assertInfoContains(() -> service.createOutlineNode("novel-001", nodeWithRange(
                "volume-outside", "book", OutlineNodeKindEnum.VOLUME, 2,
                "越界卷", "101-120", 101, 120
        )), "子节点章节范围必须位于父节点范围内");
        assertInfoContains(() -> service.createOutlineNode("novel-001", nodeWithRange(
                "volume-overlap", "book", OutlineNodeKindEnum.VOLUME, 2,
                "重叠卷", "40-80", 40, 80
        )), "与已有同级大纲重叠");

        service.createOutlineNode("novel-001", nodeWithRange(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 2,
                "第二卷", "51-100", 51, 100
        ));
        assertInfoContains(() -> service.createOutlineNode("novel-001", nodeWithRange(
                "volume-boundary", "book", OutlineNodeKindEnum.VOLUME, 3,
                "边界重叠卷", "100-100", 100, 100
        )), "与已有同级大纲重叠");

        System.out.printf(
                "manual child range validation verified: saved=%s%n",
                repository.nodes.stream()
                        .filter(node -> node.nodeKind() == OutlineNodeKindEnum.VOLUME)
                        .map(node -> node.nodeCode() + "=" + node.startChapter() + "-" + node.endChapter())
                        .toList()
        );
    }

    @Test
    void shouldRejectUpdateWhenSiblingRangeOverlaps() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        service.createOutlineNode("novel-001", nodeWithRange(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "全书", "总纲", 1, 100
        ));
        service.createOutlineNode("novel-001", nodeWithRange(
                "volume-1", "book", OutlineNodeKindEnum.VOLUME, 1,
                "第一卷", "1-50", 1, 50
        ));
        service.createOutlineNode("novel-001", nodeWithRange(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 2,
                "第二卷", "51-100", 51, 100
        ));

        assertInfoContains(() -> service.updateOutlineNode("novel-001", nodeWithRange(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 2,
                "第二卷修订", "与第一卷重叠", 40, 80
        )), "与已有同级大纲重叠");
        assertThat(repository.findOutline("novel-001", "volume-2").startChapter())
                .isEqualTo(51);
        System.out.println("manual outline update overlap rejected: volume-2 remains 51-100");
    }

    @Test
    void shouldGenerateChildStructureFieldsOnManualCreate() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));

        OutlineNodeVO firstVolume = service.createOutlineNode("novel-001", contentOnly(
                "book", "第一卷", "卷一", 1, 50
        ));
        OutlineNodeVO secondVolume = service.createOutlineNode("novel-001", contentOnly(
                "book", "第二卷", "卷二", 51, 100
        ));
        OutlineNodeVO firstArc = service.createOutlineNode("novel-001", contentOnly(
                "VOL_002", "残页初现", "章一", 51, 75
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", contentOnly(
                "VOL_001", "历史卷新增章节", "不应挂到已结束卷", 1, 25
        )), "只能在当前活动卷下生成章节大纲");

        assertThat(firstVolume)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::nodeKind,
                        OutlineNodeVO::sequenceNo, OutlineNodeVO::status)
                .containsExactly("VOL_001", OutlineNodeKindEnum.VOLUME, 1, "PLANNED");
        assertThat(secondVolume)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::nodeKind,
                        OutlineNodeVO::sequenceNo, OutlineNodeVO::status)
                .containsExactly("VOL_002", OutlineNodeKindEnum.VOLUME, 2, "PLANNED");
        assertThat(firstArc)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::nodeKind,
                        OutlineNodeVO::sequenceNo, OutlineNodeVO::status)
                .containsExactly("ARC_001", OutlineNodeKindEnum.ARC, 1, "PLANNED");
        assertThat(firstArc.parentNodeCode()).isEqualTo("VOL_002");
        System.out.printf(
                "manual create server structure verified: %s, %s, %s%n",
                firstVolume.nodeCode(), secondVolume.nodeCode(), firstArc.nodeCode()
        );
    }

    @Test
    void shouldRejectManualChapterCreationWhenCurrentVolumeIsUnplanned() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(nodeWithRange(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "全书", "总纲", 1, 100
        ));
        repository.nodes.add(new OutlineNodeVO(
                "VOL_001", "book", OutlineNodeKindEnum.VOLUME, 1,
                "卷一", "", 1, 100, "UNPLANNED"
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", contentOnly(
                "VOL_001", "第一章", "章纲", 1, 1
        )), "请先完成当前卷规划");
        assertThat(repository.nodes).extracting(OutlineNodeVO::nodeCode)
                .containsExactly("book", "VOL_001");
        System.out.println("unplanned volume manual chapter creation rejected before persistence");
    }

    @Test
    void shouldMarkUnplannedVolumePlannedAfterSavingSummary() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(nodeWithRange(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "全书", "总纲", 1, 100
        ));
        repository.nodes.add(new OutlineNodeVO(
                "VOL_001", "book", OutlineNodeKindEnum.VOLUME, 1,
                "卷一", "", 1, 100, "UNPLANNED"
        ));

        OutlineNodeVO updated = service.updateOutlineNode("novel-001", new OutlineNodeVO(
                "VOL_001", "book", OutlineNodeKindEnum.VOLUME, 1,
                "卷一", "主角进入边城并查明第一条线索", 1, 100, "UNPLANNED"
        ));

        assertThat(updated.status()).isEqualTo("PLANNED");
        assertThat(repository.findOutline("novel-001", "VOL_001").status())
                .isEqualTo("PLANNED");
        System.out.println("unplanned volume becomes PLANNED after summary is saved");
    }

    @Test
    void shouldAllocateMissingManualVolumeRangeOnServer() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));
        service.createOutlineNode("novel-001", contentOnly(
                "book", "第一卷", "卷一", 1, 40
        ));

        OutlineNodeVO secondVolume = service.createOutlineNode("novel-001", new OutlineNodeVO(
                null, "book", null, null, "第二卷", "卷二", null, null, null
        ));

        System.out.printf(
                "manual volume range allocated by backend: %d-%d%n",
                secondVolume.startChapter(), secondVolume.endChapter()
        );
        assertThat(secondVolume)
                .extracting(OutlineNodeVO::nodeKind, OutlineNodeVO::startChapter,
                        OutlineNodeVO::endChapter)
                .containsExactly(OutlineNodeKindEnum.VOLUME, 41, 100);
    }

    @Test
    void shouldRejectArcAsParentAndProtectNonLeafOrPlannedDelete() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        service.createOutlineNode("novel-001", node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));
        service.createOutlineNode("novel-001", node(
                "volume", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "第一卷"
        ));
        service.createOutlineNode("novel-001", node(
                "arc", "volume", OutlineNodeKindEnum.ARC, 1, "第一章", "第一章"
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", node(
                "child", "arc", OutlineNodeKindEnum.ARC, 1, "子节点", "非法"
        )), "ARC 不能作为 parent");
        assertInfoContains(
                () -> service.deleteOutlineNode("novel-001", "book"),
                "只能删除叶节点"
        );

        repository.children = false;
        repository.chapterPlans = true;
        assertInfoContains(
                () -> service.deleteOutlineNode("novel-001", "arc"),
                "ChapterPlan 存在时不能删除"
        );
    }

    @Test
    void shouldKeepChapterPlanProtectionWhenDeletingVolume() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));
        repository.nodes.add(node(
                "volume", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "第一卷"
        ));
        repository.chapterPlans = true;

        assertInfoContains(
                () -> service.deleteOutlineNode("novel-001", "volume"),
                "ChapterPlan 存在时不能删除"
        );
        assertThat(repository.findOutline("novel-001", "volume")).isNotNull();
        System.out.println("Volume with ChapterPlan remains protected from deletion");
    }

    @Test
    void shouldRejectNormalizedArcOnHistoricalVolumeBeforeChapterAllocation() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));
        repository.nodes.add(nodeWithRange(
                "volume-1", "book", OutlineNodeKindEnum.VOLUME, 1,
                "第一卷", "已收束", 1, 1
        ));
        repository.nodes.add(nodeWithRange(
                "arc-1", "volume-1", OutlineNodeKindEnum.ARC, 1,
                "第一章", "第一章", 1, 1
        ));
        repository.nodes.add(nodeWithRange(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 2,
                "第二卷", "当前卷", 2, 100
        ));

        assertInfoContains(() -> service.createOutlineNode("novel-001", contentOnly(
                "volume-1", "历史卷新增章节", "不应分配章节号", 1, 1
        )), "只能在当前活动卷下生成章节大纲");
        System.out.println("normalized ARC creation checks active volume before allocating its chapter number");
    }

    @Test
    void shouldLockChapterRangeWhenChildrenOrChapterPlansExist() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));

        repository.children = true;
        assertInfoContains(() -> service.updateOutlineNode("novel-001", new OutlineNodeVO(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "全书修订", "内容修订", 1, 200, "READY"
        )), "不能修改章节范围");

        repository.children = false;
        repository.chapterPlans = true;
        assertInfoContains(() -> service.updateOutlineNode("novel-001", new OutlineNodeVO(
                "book", null, OutlineNodeKindEnum.BOOK, 1,
                "全书修订", "内容修订", 1, 200, "READY"
        )), "不能修改章节范围");
    }

    @Test
    void shouldRejectCrossParentReorder() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));
        repository.nodes.add(node(
                "volume-1", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "第一卷"
        ));
        repository.nodes.add(node(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 2, "第二卷", "第二卷"
        ));

        assertInfoContains(() -> service.updateOutlineNode("novel-001", new OutlineNodeVO(
                "volume-1", "volume-2", OutlineNodeKindEnum.VOLUME, 2,
                "第一卷", "第一卷", 1, 100, "PLANNED"
        )), "parent 不能修改");
        assertThat(repository.findOutline("novel-001", "volume-1").parentNodeCode())
                .isEqualTo("book");
    }

    @Test
    void shouldReorderOutlineWithinItsCurrentParent() {
        RecordingRepository repository = new RecordingRepository();
        PlanningService service = service(new ThrowingModelPort(), repository);
        repository.nodes.add(node(
                "book", null, OutlineNodeKindEnum.BOOK, 1, "全书", "总纲"
        ));
        repository.nodes.add(node(
                "volume-1", "book", OutlineNodeKindEnum.VOLUME, 1, "第一卷", "第一卷"
        ));
        repository.nodes.add(node(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 2, "第二卷", "第二卷"
        ));

        OutlineNodeVO reordered = service.reorderOutlineNode("novel-001", "volume-2", 1);

        assertThat(reordered.sequenceNo()).isEqualTo(1);
        assertThat(repository.nodes.stream()
                .filter(node -> "book".equals(node.parentNodeCode()))
                .sorted(java.util.Comparator.comparing(OutlineNodeVO::sequenceNo)))
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("volume-2", 1),
                        org.assertj.core.groups.Tuple.tuple("volume-1", 2)
                );
        System.out.printf("ManualOutlineCrudTest reordered siblings=%s%n",
                repository.nodes.stream()
                        .filter(node -> "book".equals(node.parentNodeCode()))
                        .sorted(java.util.Comparator.comparing(OutlineNodeVO::sequenceNo))
                        .map(node -> node.nodeCode() + ":" + node.sequenceNo())
                        .toList());
    }

    private PlanningService service(
            IPlanningModelPort model,
            IPlanningRepository repository
    ) {
        return new PlanningService(
                model, new EmptyDraftRepository(), repository
        );
    }

    private OutlineNodeVO node(
            String code,
            String parentCode,
            OutlineNodeKindEnum kind,
            int sequence,
            String title,
            String summary
    ) {
        return nodeWithRange(code, parentCode, kind, sequence, title, summary,
                1, kind == OutlineNodeKindEnum.ARC ? 1 : 100);
    }

    private OutlineNodeVO nodeWithRange(
            String code,
            String parentCode,
            OutlineNodeKindEnum kind,
            int sequence,
            String title,
            String summary,
            Integer startChapter,
            Integer endChapter
    ) {
        return new OutlineNodeVO(
                code, parentCode, kind, sequence, title, summary,
                startChapter, endChapter, "PLANNED"
        );
    }

    private OutlineNodeVO contentOnly(
            String parentCode,
            String title,
            String summary,
            int startChapter,
            int endChapter
    ) {
        return new OutlineNodeVO(
                null, parentCode, null, null, title, summary,
                startChapter, endChapter, null
        );
    }

    private void assertInfoContains(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AppException.class)
                .satisfies(throwable -> {
                    AppException exception = (AppException) throwable;
                    String detail = exception.getInternalDetail() == null
                            ? exception.getInfo()
                            : exception.getInternalDetail();
                    assertThat(detail).contains(message);
                });
    }

    private static final class ThrowingModelPort implements IPlanningModelPort {
        private int calls;

        @Override
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            calls++;
            throw new AssertionError("人工大纲 CRUD 不得调用大模型");
        }
    }

    private static final class EmptyDraftRepository implements IPlanningDraftRepository {
        @Override
        public cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO save(
                String projectCode, String draftType, Object payload
        ) {
            return null;
        }

        @Override
        public Optional<cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO> find(
                String projectCode, String draftId
        ) {
            return Optional.empty();
        }

        @Override
        public void remove(String projectCode, String draftId) {
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }

    private static final class RecordingRepository implements IPlanningRepository {
        private final List<OutlineNodeVO> nodes = new ArrayList<>();
        private boolean children;
        private boolean chapterPlans;

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(
                    projectCode, "测试项目", "玄幻", 100,
                    2000, 0, "DRAFT"
            );
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return nodes.stream()
                    .filter(node -> node.nodeCode().equals(nodeCode))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            return List.copyOf(nodes);
        }

        @Override
        public OutlineNodeVO saveOutline(String projectCode, OutlineNodeVO outline) {
            nodes.add(outline);
            return outline;
        }

        @Override
        public OutlineNodeVO updateOutline(String projectCode, OutlineNodeVO outline) {
            nodes.removeIf(node -> node.nodeCode().equals(outline.nodeCode()));
            nodes.add(outline);
            return outline;
        }

        @Override
        public OutlineNodeVO reorderOutline(
                String projectCode,
                String nodeCode,
                Integer targetSequence
        ) {
            OutlineNodeVO target = findOutline(projectCode, nodeCode);
            List<OutlineNodeVO> siblings = nodes.stream()
                    .filter(node -> java.util.Objects.equals(
                            target.parentNodeCode(), node.parentNodeCode()
                    ))
                    .sorted(java.util.Comparator.comparing(OutlineNodeVO::sequenceNo))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            siblings.removeIf(node -> node.nodeCode().equals(nodeCode));
            siblings.add(Math.min(targetSequence - 1, siblings.size()), target);
            for (int index = 0; index < siblings.size(); index++) {
                OutlineNodeVO sibling = siblings.get(index);
                updateOutline(projectCode, new OutlineNodeVO(
                        sibling.nodeCode(), sibling.parentNodeCode(), sibling.nodeKind(),
                        index + 1, sibling.title(), sibling.summary(), sibling.startChapter(),
                        sibling.endChapter(), sibling.status()
                ));
            }
            return findOutline(projectCode, nodeCode);
        }

        @Override
        public void deleteOutline(String projectCode, String nodeCode) {
            nodes.removeIf(node -> node.nodeCode().equals(nodeCode));
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return children || nodes.stream()
                    .anyMatch(node -> nodeCode.equals(node.parentNodeCode()));
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return chapterPlans;
        }
    }
}
