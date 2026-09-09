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

class RootOutlineConfirmationTest {

    @Test
    void shouldConfirmEditedRootOutlineAndBindProjectTitle() {
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-root", "novel-001", "ROOT_OUTLINE", rootDraftNode(),
                Instant.parse("2026-08-28T01:00:00Z")
        ));
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = newService(repository, drafts);

        service.confirmRootOutline(
                "novel-001",
                "draft-root",
                "修订后的概要"
        );

        System.out.printf(
                "confirmed root outline: node=%s, title=%s, status=%s; default volume=%s %s-%s; removed=%s%n",
                repository.savedRoot.nodeCode(), repository.savedRoot.title(),
                repository.savedRoot.status(), repository.savedVolume.title(),
                repository.savedVolume.startChapter(), repository.savedVolume.endChapter(),
                drafts.removedIds
        );
        assertThat(repository.savedRoot).extracting(
                OutlineNodeVO::nodeCode,
                OutlineNodeVO::parentNodeCode,
                OutlineNodeVO::nodeKind,
                OutlineNodeVO::sequenceNo,
                OutlineNodeVO::title,
                OutlineNodeVO::summary,
                OutlineNodeVO::startChapter,
                OutlineNodeVO::endChapter,
                OutlineNodeVO::status
        ).containsExactly(
                "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                "归途", "修订后的概要", 1, 120, "READY"
        );
        assertThat(repository.savedVolume)
                .extracting(
                        OutlineNodeVO::nodeCode,
                        OutlineNodeVO::parentNodeCode,
                        OutlineNodeVO::nodeKind,
                        OutlineNodeVO::sequenceNo,
                        OutlineNodeVO::title,
                        OutlineNodeVO::summary,
                        OutlineNodeVO::startChapter,
                        OutlineNodeVO::endChapter,
                        OutlineNodeVO::status
                ).containsExactly(
                        "VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                        "卷一", "", 1, 120, "UNPLANNED"
                );
        assertThat(drafts.removedIds).containsExactly("draft-root");
        assertThat(drafts.find("novel-001", "draft-root")).isEmpty();
    }

    @Test
    void shouldRejectDraftWithUnexpectedTypeBeforeWritingOrDeleting() {
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-other", "novel-001", "OTHER_DRAFT", rootDraftNode(),
                Instant.parse("2026-08-28T01:00:00Z")
        ));
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = newService(repository, drafts);

        assertThatThrownBy(() -> service.confirmRootOutline(
                "novel-001", "draft-other", "原始概要"
        )).isInstanceOf(RuntimeException.class);

        System.out.println("unexpected draft type rejected before persistence");
        assertThat(repository.savedRoot).isNull();
        assertThat(drafts.removedIds).isEmpty();
        assertThat(drafts.find("novel-001", "draft-other")).isPresent();
    }

    @Test
    void shouldRejectConfirmationWhenProjectAlreadyHasBook() {
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        drafts.seed(new PlanningDraftVO(
                "draft-root", "novel-001", "ROOT_OUTLINE", rootDraftNode(),
                Instant.parse("2026-08-28T01:00:00Z")
        ));
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.rootOutline = rootDraftNode();
        PlanningService service = newService(repository, drafts);

        assertThatThrownBy(() -> service.confirmRootOutline(
                "novel-001", "draft-root", "原始概要"
        )).isInstanceOf(RuntimeException.class);

        System.out.println("existing BOOK prevented root confirmation");
        assertThat(repository.savedRoot).isNull();
        assertThat(drafts.removedIds).isEmpty();
        assertThat(drafts.find("novel-001", "draft-root")).isPresent();
    }

    private PlanningService newService(
            RecordingPlanningRepository repository,
            RecordingDraftRepository drafts
    ) {
        IPlanningModelPort model = new IPlanningModelPort() {
            @Override
            public <T> T call(
                    String systemPrompt,
                    String userPrompt,
                    Class<T> responseType
            ) {
                throw new AssertionError("confirm must not call the model");
            }
        };
        return new PlanningService(model, drafts, repository);
    }

    private static OutlineNodeVO rootDraftNode() {
        return new OutlineNodeVO(
                "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                "原始标题", "原始概要", 1, 120, "PLANNED"
        );
    }

    private static final class RecordingDraftRepository
            implements IPlanningDraftRepository {
        private final List<PlanningDraftVO> values = new ArrayList<>();
        private final List<String> removedIds = new ArrayList<>();

        private void seed(PlanningDraftVO draft) {
            values.add(draft);
        }

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            throw new AssertionError("confirm must not save a draft");
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return values.stream()
                    .filter(value -> value.projectCode().equals(projectCode)
                            && value.draftId().equals(draftId))
                    .findFirst();
        }

        @Override
        public void remove(String projectCode, String draftId) {
            removedIds.add(draftId);
            values.removeIf(value -> value.projectCode().equals(projectCode)
                    && value.draftId().equals(draftId));
        }

        @Override
        public void removeByProject(String projectCode) {
            throw new AssertionError("confirm must remove only the confirmed draft");
        }
    }

    private static final class RecordingPlanningRepository
            implements IPlanningRepository {
        private List<OutlineNodeVO> outlines = List.of();
        private OutlineNodeVO rootOutline;
        private OutlineNodeVO savedRoot;
        private OutlineNodeVO savedVolume;

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(
                    projectCode, "归途", "东方奇幻", 120,
                    2500, 0, "DRAFT"
            );
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return null;
        }

        @Override
        public OutlineNodeVO findRootOutline(String projectCode) {
            return rootOutline;
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            return outlines;
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
        public OutlineNodeVO saveOutline(String projectCode, OutlineNodeVO outline) {
            if (outline.nodeKind() == OutlineNodeKindEnum.BOOK) {
                savedRoot = outline;
            } else if (outline.nodeKind() == OutlineNodeKindEnum.VOLUME) {
                savedVolume = outline;
            }
            outlines = new ArrayList<>(outlines);
            outlines.add(outline);
            return outline;
        }
    }
}
