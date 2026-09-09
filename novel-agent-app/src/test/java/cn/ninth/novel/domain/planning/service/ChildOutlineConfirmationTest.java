package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildOutlineConfirmationTest {

    @Test
    void shouldConfirmOneNextOutlineAndDeleteDraft() {
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "全书", "全书方向", 1, 120, "READY");
        OutlineNodeVO volume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "卷方向", 1, 120, "READY");
        OutlineNodeVO generated = node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC,
                1, "模型标题", "模型概要", 1, 1, "PLANNED");
        RecordingRepository repository = new RecordingRepository(List.of(book, volume));
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                new PlanningDraftVO("draft-next", "novel-001", "NEXT_OUTLINE", generated,
                        Instant.parse("2026-09-04T01:00:00Z")));
        PlanningService service = new PlanningService(
                new NoopModelPort(), drafts, repository
        );

        service.confirmNextOutline("novel-001", "VOL_001", "draft-next",
                "用户确认标题", "用户确认概要");

        OutlineNodeVO saved = repository.saved;
        System.out.printf("next outline confirmation: code=%s, title=%s, status=%s%n",
                saved.nodeCode(), saved.title(), saved.status());
        assertThat(saved.nodeCode()).isEqualTo("ARC_001");
        assertThat(saved.parentNodeCode()).isEqualTo("VOL_001");
        assertThat(saved.nodeKind()).isEqualTo(OutlineNodeKindEnum.ARC);
        assertThat(saved.startChapter()).isEqualTo(1);
        assertThat(saved.endChapter()).isEqualTo(1);
        assertThat(saved.title()).isEqualTo("用户确认标题");
        assertThat(saved.summary()).isEqualTo("用户确认概要");
        assertThat(saved.status()).isEqualTo("READY");
        assertThat(drafts.removedIds).containsExactly("draft-next");
        System.out.println("single next outline confirmation verified: one node persisted");
    }

    @Test
    void shouldConfirmCurrentVolumeOutlineAndOpenChapterPlanning() {
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "全书", "全书方向", 1, 120, "READY");
        OutlineNodeVO volume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "卷一", "", 1, 120, "UNPLANNED");
        OutlineNodeVO generated = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "模型卷名", "模型卷纲", 1, 120, "UNPLANNED");
        RecordingRepository repository = new RecordingRepository(List.of(book, volume));
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                new PlanningDraftVO("draft-volume", "novel-001", "VOLUME_OUTLINE", generated,
                        Instant.parse("2026-09-04T01:00:00Z")));
        PlanningService service = new PlanningService(
                new NoopModelPort(), drafts, repository
        );

        service.confirmVolumeOutline("novel-001", "VOL_001", "draft-volume",
                "用户确认卷名", "用户确认卷纲");

        OutlineNodeVO saved = repository.findOutline("novel-001", "VOL_001");
        System.out.printf("volume outline confirmation: code=%s, status=%s%n",
                saved.nodeCode(), saved.status());
        assertThat(saved.title()).isEqualTo("用户确认卷名");
        assertThat(saved.summary()).isEqualTo("用户确认卷纲");
        assertThat(saved.status()).isEqualTo("PLANNED");
        assertThat(drafts.removedIds).containsExactly("draft-volume");
        System.out.println("confirmed volume outline transitions VOL_001 to PLANNED");
    }

    @Test
    void shouldFinalizeCurrentVolumeBeforeCreatingNextVolume() {
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "全书", "全书方向", 1, 100, "READY");
        OutlineNodeVO volume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "卷方向", 1, 100, "READY");
        OutlineNodeVO firstArc = node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC,
                1, "第一章", "第一章概要", 1, 1, "READY");
        OutlineNodeVO lastArc = node("ARC_027", "VOL_001", OutlineNodeKindEnum.ARC,
                27, "第二十七章", "第二十七章概要", 27, 27, "READY");
        RecordingRepository repository = new RecordingRepository(
                List.of(book, volume, firstArc, lastArc)
        );
        PlanningService service = new PlanningService(
                new NoopModelPort(), new RecordingDraftRepository(null), repository
        );

        OutlineNodeVO saved = service.createNextVolume("novel-001", "BOOK_001");

        OutlineNodeVO finalized = repository.findOutline("novel-001", "VOL_001");
        System.out.printf("volume boundary finalized: previous=%s-%s, next=%s-%s%n",
                finalized.startChapter(), finalized.endChapter(),
                saved.startChapter(), saved.endChapter());
        assertThat(finalized.endChapter()).isEqualTo(27);
        assertThat(saved.nodeCode()).isEqualTo("VOL_002");
        assertThat(saved.startChapter()).isEqualTo(28);
        assertThat(saved.endChapter()).isEqualTo(100);
        assertThat(saved.title()).isEqualTo("卷二");
        assertThat(saved.summary()).isEmpty();
        assertThat(saved.status()).isEqualTo("UNPLANNED");
        System.out.println("next volume creation verified: actual last ARC closes the previous volume before outline planning");
    }

    @Test
    void shouldRejectCreatingNextVolumeBeforeCurrentVolumeIsPlanned() {
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "全书", "全书方向", 1, 100, "READY");
        OutlineNodeVO volume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "卷一", "", 1, 100, "UNPLANNED");
        OutlineNodeVO arc = node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC,
                1, "第一章", "第一章概要", 1, 1, "READY");
        RecordingRepository repository = new RecordingRepository(List.of(book, volume, arc));
        PlanningService service = new PlanningService(
                new NoopModelPort(), new RecordingDraftRepository(null), repository
        );

        assertThatThrownBy(() -> service.createNextVolume("novel-001", "BOOK_001"))
                .isInstanceOf(RuntimeException.class)
                .satisfies(failure -> assertThat(failure.toString())
                        .contains("请先完成当前卷规划"));

        assertThat(repository.saved).isNull();
        System.out.println("next volume creation rejected before current volume planning");
    }

    @Test
    void shouldRejectCreatingNextVolumeWithoutAnActiveVolume() {
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "全书", "全书方向", 1, 120, "READY");
        RecordingRepository repository = new RecordingRepository(List.of(book));
        PlanningService service = new PlanningService(
                new NoopModelPort(), new RecordingDraftRepository(null), repository
        );

        assertThatThrownBy(() -> service.createNextVolume("novel-001", "BOOK_001"))
                .isInstanceOf(RuntimeException.class)
                .satisfies(failure -> assertThat(failure.toString())
                        .contains("当前没有活动卷，不能生成下一卷"));

        System.out.println("next volume creation rejected: default volume must be created at BOOK confirmation");
        assertThat(repository.saved).isNull();
    }

    @Test
    void shouldRejectBatchPayloadAndStaleStructure() {
        OutlineNodeVO book = node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                1, "全书", "全书方向", 1, 120, "READY");
        OutlineNodeVO volume = node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                1, "第一卷", "卷方向", 1, 120, "READY");
        RecordingRepository repository = new RecordingRepository(List.of(book, volume));
        PlanningService service = new PlanningService(
                new NoopModelPort(), new RecordingDraftRepository(
                        new PlanningDraftVO("draft-list", "novel-001", "NEXT_OUTLINE",
                                List.of(), Instant.parse("2026-09-04T01:00:00Z"))), repository
        );

        assertThatThrownBy(() -> service.confirmNextOutline(
                "novel-001", "VOL_001", "draft-list", "标题", "概要"))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.saved).isNull();
        System.out.println("batch payload rejected by single next outline confirmation");
    }

    private static OutlineNodeVO node(
            String code,
            String parent,
            OutlineNodeKindEnum kind,
            int sequence,
            String title,
            String summary,
            int start,
            int end,
            String status
    ) {
        return new OutlineNodeVO(code, parent, kind, sequence, title, summary, start, end, status);
    }

    private static final class NoopModelPort implements IPlanningModelPort {
        @Override
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            throw new AssertionError("model must not be called during confirmation");
        }
    }

    private static final class RecordingDraftRepository implements IPlanningDraftRepository {
        private final PlanningDraftVO draft;
        private final List<String> removedIds = new ArrayList<>();

        private RecordingDraftRepository(PlanningDraftVO draft) {
            this.draft = draft;
        }

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            return draft;
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return draft.draftId().equals(draftId) ? Optional.of(draft) : Optional.empty();
        }

        @Override
        public void remove(String projectCode, String draftId) {
            removedIds.add(draftId);
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }

    private static final class RecordingRepository implements IPlanningRepository {
        private final List<OutlineNodeVO> nodes;
        private OutlineNodeVO saved;

        private RecordingRepository(List<OutlineNodeVO> nodes) {
            this.nodes = new ArrayList<>(nodes);
        }

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(projectCode, "归途", "东方奇幻", 120,
                    2500, 0, "DRAFT");
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return nodes.stream().filter(node -> node.nodeCode().equals(nodeCode))
                    .findFirst().orElse(null);
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            return List.copyOf(nodes);
        }

        @Override
        public int nextSequence(String projectCode, String parentNodeCode) {
            return nodes.stream()
                    .filter(node -> java.util.Objects.equals(node.parentNodeCode(), parentNodeCode))
                    .mapToInt(OutlineNodeVO::sequenceNo).max().orElse(0) + 1;
        }

        @Override
        public OutlineNodeVO saveOutline(String projectCode, OutlineNodeVO outline) {
            saved = outline;
            nodes.add(outline);
            return outline;
        }

        @Override
        public OutlineNodeVO updateOutline(String projectCode, OutlineNodeVO outline) {
            int index = nodes.indexOf(findOutline(projectCode, outline.nodeCode()));
            if (index >= 0) {
                nodes.set(index, outline);
            } else {
                nodes.add(outline);
            }
            saved = outline;
            return outline;
        }

        @Override
        public OutlineNodeVO finalizeCurrentVolumeAndCreateNext(
                String projectCode,
                String currentVolumeNodeCode,
                OutlineNodeVO nextVolume
        ) {
            OutlineNodeVO current = findOutline(projectCode, currentVolumeNodeCode);
            int actualEndChapter = nodes.stream()
                    .filter(node -> node.nodeKind() == OutlineNodeKindEnum.ARC
                            && java.util.Objects.equals(
                            node.parentNodeCode(), currentVolumeNodeCode))
                    .mapToInt(OutlineNodeVO::startChapter)
                    .max()
                    .orElseThrow();
            OutlineNodeVO finalized = new OutlineNodeVO(
                    current.nodeCode(), current.parentNodeCode(), current.nodeKind(),
                    current.sequenceNo(), current.title(), current.summary(),
                    current.startChapter(), actualEndChapter, current.status()
            );
            nodes.set(nodes.indexOf(current), finalized);
            OutlineNodeVO book = nodes.stream()
                    .filter(node -> node.nodeKind() == OutlineNodeKindEnum.BOOK)
                    .findFirst()
                    .orElseThrow();
            saved = new OutlineNodeVO(
                    nextVolume.nodeCode(), book.nodeCode(), OutlineNodeKindEnum.VOLUME,
                    nextVolume.sequenceNo(), nextVolume.title(), nextVolume.summary(),
                    actualEndChapter + 1, book.endChapter(), nextVolume.status()
            );
            nodes.add(saved);
            return saved;
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return false;
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return false;
        }
    }
}
