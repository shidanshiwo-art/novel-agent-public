package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ChapterPlanEditingTest {

    @Test
    void shouldListPersistedChapterPlansWithoutCallingModel() {
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.chapterPlans = List.of(
                new ChapterOutlineVO(3, "ARC_001", "第三章", "第三章摘要", "READY"),
                new ChapterOutlineVO(4, "ARC_002", "第四章", "第四章摘要", "PLANNED")
        );
        RecordingModelPort model = new RecordingModelPort();
        PlanningService service = service(model, repository);

        List<ChapterOutlineVO> result = service.listChapterPlans("novel-001");

        System.out.printf("listed chapter plans: count=%d, chapterNumbers=%s%n",
                result.size(), result.stream().map(ChapterOutlineVO::chapterNumber).toList());
        assertThat(result)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::title,
                        ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(
                        tuple(3, "第三章", "第三章摘要", "READY"),
                        tuple(4, "第四章", "第四章摘要", "PLANNED")
                );
        assertThat(repository.findChapterPlanOutlinesCalls).containsExactly("novel-001");
        assertThat(model.calls).isZero();
    }

    @Test
    void shouldUpdateTitleAndSummaryUsingPathChapterNumberAndPreserveStatus() {
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.updatedPlan = new ChapterOutlineVO(
                3, "ARC_001", "第三章", "人工修订摘要", "COMPLETED"
        );
        RecordingModelPort model = new RecordingModelPort();
        PlanningService service = service(model, repository);

        ChapterOutlineVO result = service.updateChapterPlan(
                "novel-001", 3,
                new ChapterOutlineVO(999, "IGNORED", "第三章", "人工修订摘要", "COMPLETED")
        );

        System.out.printf("updated chapter plan: chapter=%d, status=%s%n",
                result.chapterNumber(), result.status());
        assertThat(repository.updatedPlanRequest)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::title,
                        ChapterOutlineVO::summary, ChapterOutlineVO::status)
                .containsExactly(3, "第三章", "人工修订摘要", null);
        assertThat(result).isEqualTo(repository.updatedPlan);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(model.calls).isZero();
    }

    @Test
    void shouldRejectInvalidChapterPlanEditsBeforeRepositoryUpdate() {
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = service(new RecordingModelPort(), repository);

        assertThatThrownBy(() -> service.listChapterPlans(" "))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.updateChapterPlan(
                " ", 3, new ChapterOutlineVO(3, null, "标题", "摘要", null)
        )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.updateChapterPlan(
                "novel-001", 0, new ChapterOutlineVO(0, null, "标题", "摘要", null)
        )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.updateChapterPlan(
                "novel-001", 3, null
        )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.updateChapterPlan(
                "novel-001", 3, new ChapterOutlineVO(3, null, " ", "摘要", null)
        )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.updateChapterPlan(
                "novel-001", 3, new ChapterOutlineVO(3, null, "标题", " ", null)
        )).isInstanceOf(RuntimeException.class);

        System.out.println("invalid chapter-plan edits rejected before repository update");
        assertThat(repository.updatedPlanRequest).isNull();
    }

    @Test
    void shouldNotCallModelWhenChapterPlanDoesNotExist() {
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        RecordingModelPort model = new RecordingModelPort();
        PlanningService service = service(model, repository);

        assertThatThrownBy(() -> service.updateChapterPlan(
                "novel-001", 3, new ChapterOutlineVO(3, null, "标题", "摘要", null)
        )).isInstanceOf(RuntimeException.class);

        System.out.println("missing chapter plan rejected without model call");
        assertThat(model.calls).isZero();
        assertThat(repository.updatedPlanRequest).isNull();
    }

    private static PlanningService service(
            RecordingModelPort model,
            RecordingPlanningRepository repository
    ) {
        return new PlanningService(
                model, new EmptyDraftRepository(), repository
        );
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private int calls;

        @Override
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            calls++;
            throw new AssertionError("manual chapter-plan editing must not call the model");
        }
    }

    private static final class EmptyDraftRepository implements IPlanningDraftRepository {
        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return Optional.empty();
        }

        @Override
        public void remove(String projectCode, String draftId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeByProject(String projectCode) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingPlanningRepository implements IPlanningRepository {
        private final List<String> findChapterPlanOutlinesCalls = new ArrayList<>();
        private final List<OutlineNodeVO> outlines = List.of(
                new OutlineNodeVO(
                        "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                        "全书", "全书方向", 1, 100, "READY"
                ),
                new OutlineNodeVO(
                        "VOLUME_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                        "当前卷", "当前卷方向", 1, 20, "READY"
                ),
                new OutlineNodeVO(
                        "ARC_001", "VOLUME_001", OutlineNodeKindEnum.ARC, 1,
                        "第三章", "第三章摘要", 3, 3, "READY"
                ),
                new OutlineNodeVO(
                        "ARC_002", "VOLUME_001", OutlineNodeKindEnum.ARC, 2,
                        "第四章", "第四章摘要", 4, 4, "READY"
                )
        );
        private List<ChapterOutlineVO> chapterPlans = List.of();
        private ChapterOutlineVO updatedPlan;
        private ChapterOutlineVO updatedPlanRequest;

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return outlines.stream()
                    .filter(outline -> outline.nodeCode().equals(nodeCode))
                    .findFirst()
                    .orElse(null);
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
        public List<ChapterOutlineVO> findChapterPlanOutlines(String projectCode) {
            findChapterPlanOutlinesCalls.add(projectCode);
            return chapterPlans;
        }

        @Override
        public ChapterOutlineVO updateChapterPlan(
                String projectCode,
                Integer chapterNumber,
                ChapterOutlineVO chapterPlan
        ) {
            updatedPlanRequest = chapterPlan;
            if (updatedPlan == null) {
                throw new IllegalArgumentException("章节计划不存在");
            }
            return updatedPlan;
        }
    }
}
