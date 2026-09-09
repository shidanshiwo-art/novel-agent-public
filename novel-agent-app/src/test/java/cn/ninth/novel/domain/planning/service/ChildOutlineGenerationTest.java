package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChildOutlineDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class ChildOutlineGenerationTest {

    @Test
    void shouldRejectFirstVolumeGenerationBecauseRootConfirmationCreatesIt() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        Throwable failure = catchThrowable(() -> newService(model, drafts, repository)
                .createNextVolume("novel-001", "BOOK_001"));

        System.out.printf("first volume generation rejected before model call: %s%n", failure);
        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.toString()).contains("当前没有活动卷，不能生成下一卷");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
        System.out.println("next volume generation now requires the auto-created default volume");
    }

    @Test
    void shouldCreateNextVolumeStructureWithServerAssignedSequence() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "归途", "全书主线", 1, 120, "READY");
        OutlineNodeVO currentVolume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "卷一", "阶段结果", 1, 120, "READY");
        repository.nodes = List.of(
                book,
                currentVolume,
                node("ARC_019", "VOL_001", OutlineNodeKindEnum.ARC,
                        1, "钟声", "异变开始", 19, 19, "READY")
        );

        OutlineNodeVO next = newService(model, drafts, repository)
                .createNextVolume("novel-001", "BOOK_001");

        System.out.printf("next volume structure: code=%s, sequence=%d, range=%d-%d, status=%s%n",
                next.nodeCode(), next.sequenceNo(), next.startChapter(), next.endChapter(), next.status());
        assertThat(next)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::parentNodeCode,
                        OutlineNodeVO::nodeKind, OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::title, OutlineNodeVO::summary,
                        OutlineNodeVO::startChapter, OutlineNodeVO::endChapter,
                        OutlineNodeVO::status)
                .containsExactly("VOL_002", "BOOK_001", OutlineNodeKindEnum.VOLUME, 2,
                        "卷二", "", 20, 120, "UNPLANNED");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
        System.out.println("next volume structure is created by the service before any model call");
    }

    @Test
    void shouldGenerateExactlyOneNextChapterFromVolume() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.parent = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "边城调查", 1, 120, "READY");
        repository.nodes = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        1, "归途", "全书主线", 1, 120, "READY"),
                repository.parent
        );

        PlanningDraftVO result = newService(model, drafts, repository)
                .generateNextOutline("novel-001", "VOL_001", null);
        OutlineNodeVO next = (OutlineNodeVO) result.payload();

        System.out.printf("next chapter draft: code=%s, chapter=%s%n",
                next.nodeCode(), next.startChapter());
        assertThat(next.nodeCode()).isEqualTo("ARC_001");
        assertThat(next.parentNodeCode()).isEqualTo("VOL_001");
        assertThat(next.nodeKind()).isEqualTo(OutlineNodeKindEnum.ARC);
        assertThat(next.sequenceNo()).isEqualTo(1);
        assertThat(next.startChapter()).isEqualTo(1);
        assertThat(next.endChapter()).isEqualTo(1);
        assertThat(model.userPrompts).singleElement().asString()
                .contains(
                        "【当前卷】", "【上一章大纲】", "【当前卷已有章节大纲】",
                        "【本次目标章节】", "第1章", "【用户补充要求】"
                )
                .doesNotContain("null", "preferredCount", "建议数量", "children");
        System.out.println("next chapter generation verified: chapter 1 allocated by the service");
    }

    @Test
    void shouldRejectNextChapterGenerationWhenCurrentVolumeIsUnplanned() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.parent = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "卷一", "", 1, 120, "UNPLANNED");
        repository.nodes = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        1, "归途", "全书主线", 1, 120, "READY"),
                repository.parent
        );

        Throwable failure = catchThrowable(() -> newService(model, drafts, repository)
                .generateNextOutline("novel-001", "VOL_001", null));

        System.out.printf("unplanned volume chapter generation rejected: %s%n", failure);
        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.toString()).contains("请先完成当前卷规划");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
    }

    @Test
    void shouldGenerateCurrentVolumeOutlineWithoutChangingStructureFields() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.parent = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "卷一", "", 1, 120, "UNPLANNED");
        repository.nodes = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        1, "归途", "全书主线", 1, 120, "READY"),
                repository.parent
        );

        PlanningDraftVO result = newService(model, drafts, repository)
                .generateVolumeOutline("novel-001", "VOL_001", "突出边城调查");
        OutlineNodeVO draft = (OutlineNodeVO) result.payload();

        System.out.printf("current volume outline draft: code=%s, title=%s, status=%s%n",
                draft.nodeCode(), draft.title(), draft.status());
        assertThat(result.draftType()).isEqualTo("VOLUME_OUTLINE");
        assertThat(draft)
                .extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::parentNodeCode,
                        OutlineNodeVO::nodeKind, OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::title, OutlineNodeVO::summary,
                        OutlineNodeVO::startChapter, OutlineNodeVO::endChapter,
                        OutlineNodeVO::status)
                .containsExactly("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                        "卷一", "边城调查", 1, 120, "UNPLANNED");
        assertThat(model.systemPrompts).singleElement().asString()
                .contains("卷级大纲", "序号和结构名称由系统维护", "只返回创作内容");
        assertThat(model.userPrompts).singleElement().asString()
                .contains("结构位置：卷一", "当前卷尚未完成正式卷纲", "突出边城调查");
        System.out.println("current volume structure fields remain system-owned before confirmation");
    }

    @Test
    void shouldRejectNextChapterGenerationFromHistoricalVolume() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.parent = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "已收束", 1, 20, "READY");
        repository.nodes = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        1, "归途", "全书主线", 1, 120, "READY"),
                repository.parent,
                node("VOL_002", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                        2, "第二卷", "当前推进", 21, 120, "READY")
        );

        Throwable failure = catchThrowable(() -> newService(model, drafts, repository)
                .generateNextOutline("novel-001", "VOL_001", null));

        System.out.printf("historical volume chapter generation rejected: %s%n", failure);
        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.toString()).contains("只能在当前活动卷下生成章节大纲");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
    }

    @Test
    void shouldProvideCurrentVolumeAndExistingChapterContextWhenGeneratingNextChapter() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "归途", "全书主线", 1, 120, "READY");
        OutlineNodeVO volume = node("VOL_002", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                2, "灰烬之门", "主角带着真相离开边城", 28, 120, "READY");
        repository.parent = volume;
        repository.nodes = List.of(
                book,
                volume,
                node("ARC_028", "VOL_002", OutlineNodeKindEnum.ARC,
                        1, "灰烬", "边城燃起大火", 28, 28, "READY"),
                node("ARC_029", "VOL_002", OutlineNodeKindEnum.ARC,
                        2, "追兵", "追兵封锁北门", 29, 29, "READY"),
                node("ARC_030", "VOL_002", OutlineNodeKindEnum.ARC,
                        3, "离城", "主角带着真相离开边城", 30, 30, "READY")
        );

        PlanningDraftVO result = newService(model, drafts, repository)
                .generateNextOutline("novel-001", "VOL_002", "继续推进");

        OutlineNodeVO next = (OutlineNodeVO) result.payload();
        String prompt = model.userPrompts.get(0);
        System.out.printf("next chapter context: target=%d, promptLength=%d%n",
                next.startChapter(), prompt.length());
        assertThat(next.startChapter()).isEqualTo(31);
        assertThat(next.endChapter()).isEqualTo(31);
        assertThat(prompt).contains(
                        "【当前卷】",
                        "标题：灰烬之门",
                        "摘要：主角带着真相离开边城",
                        "【上一章大纲】",
                        "第30章 · 离城",
                        "摘要：主角带着真相离开边城",
                        "【当前卷已有章节大纲】",
                        "第28章 · 灰烬",
                        "摘要：边城燃起大火",
                        "第29章 · 追兵",
                        "摘要：追兵封锁北门",
                        "【本次目标章节】",
                        "第31章"
                )
                .doesNotContain("正文", "章节正文");
        assertThat(model.systemPrompts).singleElement().asString()
                .contains(
                        "章级大纲",
                        "当前卷",
                        "上一章大纲",
                        "当前卷已有章节大纲",
                        "只包含 title 和 summary",
                        "不返回章节号、卷号、章节范围"
                );
        System.out.println("next chapter prompt context verified: previous and current-volume outlines are included");
    }

    @Test
    void shouldRejectNextChapterWhenProjectTargetHasBeenReached() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.targetChapterCount = 100;
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "归途", "全书主线", 1, 100, "READY");
        OutlineNodeVO volume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "阶段结果", 1, 100, "READY");
        repository.parent = volume;
        repository.nodes = List.of(
                book,
                volume,
                node("ARC_100", "VOL_001", OutlineNodeKindEnum.ARC,
                        100, "终点之后", "故事仍未结束", 100, 100, "READY")
        );

        Throwable failure = catchThrowable(() -> newService(model, drafts, repository)
                .generateNextOutline("novel-001", "VOL_001", null));

        System.out.printf("target chapter limit rejected: %s%n", failure);
        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.toString())
                .contains("预计章节数已达到 100 章，请先调整预计章节数后继续创作。");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
    }

    @Test
    void shouldProvidePreviousVolumeFactsWhenGeneratingNextVolumeOutline() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "归途", "全书主线", 1, 120, "READY");
        OutlineNodeVO firstVolume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "午夜钟楼", "", 1, 20, "UNPLANNED");
        OutlineNodeVO secondVolume = node("VOL_002", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                2, "卷二", "", 21, 120, "UNPLANNED");
        repository.parent = secondVolume;
        repository.previousChapterSummary = "主角带着染血船票离开边城";
        repository.recentMemories = List.of(new ChapterMemoryVO(
                20, "钟楼异变被暂时压制", List.of("染血船票现身"),
                List.of("幕后势力身份未明"), "北门外出现追兵"
        ));
        repository.nodes = List.of(
                book,
                firstVolume,
                node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC,
                        1, "钟声", "异变开始", 1, 1, "READY"),
                secondVolume,
                node("ARC_021", "VOL_002", OutlineNodeKindEnum.ARC,
                        1, "灰烬", "边城陷落", 21, 21, "READY")
        );

        PlanningDraftVO result = newService(model, drafts, repository)
                .generateVolumeOutline("novel-001", "VOL_002", "继续推进");

        OutlineNodeVO next = (OutlineNodeVO) result.payload();
        assertThat(next.nodeCode()).isEqualTo("VOL_002");
        assertThat(next.title()).isEqualTo("卷二");
        assertThat(model.userPrompts).singleElement().asString()
                .contains(
                        "【全书大纲】",
                        "全书主线",
                        "【前置卷正式卷纲】",
                        "标题：午夜钟楼",
                        "摘要：无",
                        "【前置卷已规划章节】",
                        "第1章 · 钟声",
                        "摘要：异变开始",
                        "【已发生剧情事实】",
                        "上一章实际摘要：主角带着染血船票离开边城",
                        "染血船票现身",
                        "北门外出现追兵",
                        "继续推进"
                )
                .doesNotContain("正文", "chapterPlan", "章节正文");
        assertThat(model.systemPrompts).singleElement().asString()
                .contains(
                        "卷级大纲",
                        "只包含 title 和 summary",
                        "不返回卷号、章节号、章节范围"
                );
        System.out.println("next volume outline prompt context verified: book, prior arc, actual summary and memory are included");
    }

    @Test
    void shouldRejectNextVolumeWhenCurrentVolumeHasNoArc() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.parent = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "尚未展开", 1, 120, "READY");
        repository.nodes = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        1, "归途", "全书主线", 1, 120, "READY"),
                repository.parent
        );

        Throwable failure = catchThrowable(() -> newService(model, drafts, repository)
                .createNextVolume("novel-001", "BOOK_001"));
        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.toString()).contains("当前卷尚未规划章节，不能开始下一卷");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
        System.out.println("empty current volume rejected before next volume generation");
    }

    @Test
    void shouldRejectMissingParentAndArcParentBeforeCallingModel() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = newService(model, drafts, repository);

        repository.parent = null;
        repository.nodes = List.of();
        assertThatThrownBy(() -> service.generateNextOutline(
                "novel-001", "MISSING", "需求"))
                .isInstanceOf(RuntimeException.class);
        repository.parent = node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC,
                1, "第一章", "第一章概要", 1, 1, "READY");
        repository.nodes = List.of(repository.parent);
        assertThatThrownBy(() -> service.generateNextOutline(
                "novel-001", "ARC_001", "需求"))
                .isInstanceOf(RuntimeException.class);

        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
        System.out.println("missing parent and ARC parent rejected before model generation");
    }

    private PlanningService newService(
            RecordingModelPort model,
            RecordingDraftRepository drafts,
            RecordingPlanningRepository repository
    ) {
        return new PlanningService(model, drafts, repository);
    }

    private static OutlineNodeVO node(
            String nodeCode,
            String parentNodeCode,
            OutlineNodeKindEnum nodeKind,
            int sequenceNo,
            String title,
            String summary,
            int startChapter,
            int endChapter,
            String status
    ) {
        return new OutlineNodeVO(nodeCode, parentNodeCode, nodeKind, sequenceNo,
                title, summary, startChapter, endChapter, status);
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private final List<Class<?>> responseTypes = new ArrayList<>();
        private final List<String> userPrompts = new ArrayList<>();
        private final List<String> systemPrompts = new ArrayList<>();
        private int calls;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            calls++;
            responseTypes.add(responseType);
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            System.out.printf("next outline prompt: %s%n", userPrompt);
            assertThat(systemPrompt + "\n" + userPrompt)
                    .doesNotContain("nodeCode", "sequenceNo", "status", "preferredCount",
                            "children", "后端");
            if (responseType == ChildOutlineDraftVO.class) {
                return (T) new ChildOutlineDraftVO("第一卷", "边城调查");
            }
            throw new AssertionError("unexpected response type: " + responseType);
        }
    }

    private static final class RecordingDraftRepository implements IPlanningDraftRepository {
        private final List<String> savedTypes = new ArrayList<>();

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            savedTypes.add(draftType);
            return new PlanningDraftVO("draft-next", projectCode, draftType, payload,
                    Instant.parse("2026-09-04T01:00:00Z"));
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

    private static final class RecordingPlanningRepository implements IPlanningRepository {
        private OutlineNodeVO parent = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "归途", "全书主线", 1, 120, "READY");
        private List<OutlineNodeVO> nodes = List.of(parent);
        private int targetChapterCount = 120;
        private String previousChapterSummary;
        private List<ChapterMemoryVO> recentMemories = List.of();

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(projectCode, "归途", "东方奇幻", targetChapterCount,
                    2500, 0, "DRAFT");
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            return new StoryBibleVO("少年背负灭门之谜", "人在真相前的选择",
                    "主角与幕后势力的长期对抗", "主角完成自我和解",
                    "边城与诸国共存的奇幻大陆", null, null, null, "CONFIRMED");
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            return List.of();
        }

        @Override
        public String findPreviousChapterSummary(String projectCode, Integer chapterNumber) {
            return previousChapterSummary;
        }

        @Override
        public List<ChapterMemoryVO> findRecentChapterMemories(
                String projectCode, Integer chapterNumber, int limit
        ) {
            return recentMemories;
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return nodes.stream().filter(node -> node.nodeCode().equals(nodeCode))
                    .findFirst().orElse(null);
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            return nodes;
        }

        @Override
        public int nextSequence(String projectCode, String parentNodeCode) {
            return (int) nodes.stream()
                    .filter(node -> java.util.Objects.equals(node.parentNodeCode(), parentNodeCode))
                    .count() + 1;
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return false;
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return false;
        }

        @Override
        public OutlineNodeVO finalizeCurrentVolumeAndCreateNext(
                String projectCode,
                String currentVolumeNodeCode,
                OutlineNodeVO nextVolume
        ) {
            List<OutlineNodeVO> updated = new ArrayList<>(nodes);
            updated.add(nextVolume);
            nodes = updated;
            return nextVolume;
        }
    }
}
