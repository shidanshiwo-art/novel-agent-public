package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
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

class ChapterPlanSingleConfirmationTest {

    @Test
    void shouldInsertOneReadyPlanUsingDraftChapterAndOutlineOnly() {
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                chapterPlanDraft(17, "ARC_002")
        );
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = service(drafts, repository);

        service.confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan",
                "宗门大比", "用户确认摘要"
        );

        System.out.printf(
                "single chapter confirmed: chapter=%d, outline=%s, status=%s%n",
                repository.upserted.chapterNumber(), repository.upserted.outlineNodeCode(),
                repository.upserted.status()
        );
        assertThat(repository.upserted)
                .extracting(
                        ChapterOutlineVO::chapterNumber,
                        ChapterOutlineVO::outlineNodeCode,
                        ChapterOutlineVO::title,
                        ChapterOutlineVO::summary,
                        ChapterOutlineVO::status
                )
                .containsExactly(
                        17, "ARC_002", "宗门大比", "用户确认摘要", "READY"
                );
        assertThat(drafts.removed).containsExactly("novel-001/draft-chapter-plan");
    }

    @Test
    void shouldUpdateExistingPlannedOrReadyPlanWithoutBatchReplacement() {
        for (String existingStatus : List.of("PLANNED", "READY")) {
            RecordingDraftRepository drafts = new RecordingDraftRepository(
                    chapterPlanDraft(17, "ARC_002")
            );
            RecordingPlanningRepository repository = new RecordingPlanningRepository();
            repository.existingPlans = List.of(
                    new ChapterOutlineVO(17, "ARC_002", "旧标题", "旧摘要", existingStatus)
            );
            PlanningService service = service(drafts, repository);

            service.confirmChapterPlan(
                    "novel-001", 17, "draft-chapter-plan",
                    "宗门大比", "更新后的摘要"
            );

            System.out.printf(
                    "single chapter updated: existingStatus=%s, confirmedStatus=%s%n",
                    existingStatus, repository.upserted.status()
            );
            assertThat(repository.upserted.status()).isEqualTo("READY");
            assertThat(repository.upserted.title()).isEqualTo("宗门大比");
            assertThat(repository.upserted.summary()).isEqualTo("更新后的摘要");
            assertThat(drafts.removed).containsExactly("novel-001/draft-chapter-plan");
        }
    }

    @Test
    void shouldRejectAiOverwriteWhenTheChapterPlanIsCompleted() {
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                chapterPlanDraft(17, "ARC_002")
        );
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.existingPlans = List.of(
                new ChapterOutlineVO(17, "ARC_002", "已有计划", "已有正文", "COMPLETED")
        );
        PlanningService service = service(drafts, repository);

        assertThatThrownBy(() -> service.confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan", "覆盖标题", "覆盖摘要"
        )).isInstanceOf(RuntimeException.class);

        System.out.println("completed chapter plan rejected before single upsert");
        assertThat(repository.upserted).isNull();
        assertThat(drafts.removed).isEmpty();
    }

    @Test
    void shouldOnlyBlockCompletedPlanForTheCurrentChapter() {
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                chapterPlanDraft(17, "ARC_002")
        );
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.existingPlans = List.of(
                new ChapterOutlineVO(18, "ARC_002", "第十八章", "已有正文", "COMPLETED")
        );
        PlanningService service = service(drafts, repository);

        service.confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan", "宗门大比", "当前章摘要"
        );

        System.out.println("completed plan in another chapter did not block current chapter confirmation");
        assertThat(repository.upserted)
                .extracting(ChapterOutlineVO::chapterNumber, ChapterOutlineVO::status)
                .containsExactly(17, "READY");
        assertThat(drafts.removed).containsExactly("novel-001/draft-chapter-plan");
    }

    @Test
    void shouldValidateDraftIdentityAndCurrentOutlineRange() {
        assertThatThrownBy(() -> serviceWithDraft(chapterPlanDraft(16, "ARC_002"))
                .confirmChapterPlan(
                        "novel-001", 17, "draft-chapter-plan", "标题", "摘要"
                )).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> serviceWithDraft(chapterPlanDraft(17, "MISSING"))
                .confirmChapterPlan(
                        "novel-001", 17, "draft-chapter-plan", "标题", "摘要"
                )).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> serviceWithDraft(chapterPlanDraft(21, "ARC_002"))
                .confirmChapterPlan(
                        "novel-001", 21, "draft-chapter-plan", "标题", "摘要"
                )).isInstanceOf(RuntimeException.class);

        System.out.println("single confirmation validates draft chapter, project outline and range");
    }

    @Test
    void shouldRejectConfirmationWhenTheMostSpecificOutlineChangedAfterDraftGeneration() {
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                chapterPlanDraft(17, "VOLUME_001")
        );
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        OutlineNodeVO book = new OutlineNodeVO(
                "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                "全书", "全书方向", 1, 300, "READY"
        );
        OutlineNodeVO volume = new OutlineNodeVO(
                "VOLUME_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                "当前卷", "当前卷方向", 1, 20, "READY"
        );
        repository.outlines = new ArrayList<>(List.of(book, volume));
        PlanningService service = service(drafts, repository);

        repository.outlines = List.of(
                book,
                volume,
                new OutlineNodeVO(
                        "ARC_002", "VOLUME_001", OutlineNodeKindEnum.ARC, 1,
                        "新增剧情段", "第 17 章的新剧情段", 11, 20, "READY"
                )
        );

        assertThatThrownBy(() -> service.confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan", "标题", "摘要"
        )).isInstanceOf(RuntimeException.class)
                .satisfies(error -> assertThat(error.toString())
                        .contains("大纲结构已变化，请重新生成章节计划"));

        System.out.println("stale chapter plan draft rejected after a more-specific outline was added");
        assertThat(repository.upserted).isNull();
        assertThat(drafts.removed).isEmpty();
    }

    @Test
    void shouldRejectConfirmationWhenCurrentChapterHasNoArc() {
        RecordingDraftRepository drafts = new RecordingDraftRepository(
                chapterPlanDraft(17, "VOLUME_001")
        );
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.outlines = repository.outlines.stream()
                .filter(node -> node.nodeKind() != OutlineNodeKindEnum.ARC)
                .toList();
        PlanningService service = service(drafts, repository);

        assertThatThrownBy(() -> service.confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan", "标题", "摘要"
        )).isInstanceOf(RuntimeException.class)
                .satisfies(error -> assertThat(error.toString()).contains("当前章节尚未创建章纲"));

        System.out.println("chapter plan confirmation requires the current chapter ARC");
        assertThat(repository.upserted).isNull();
        assertThat(drafts.removed).isEmpty();
    }

    @Test
    void shouldRejectMissingDraftTypePayloadOrEditedText() {
        assertThatThrownBy(() -> serviceWithDraft(null).confirmChapterPlan(
                "novel-001", 17, "missing-draft", "标题", "摘要"
        )).isInstanceOf(RuntimeException.class);

        RecordingDraftRepository wrongTypeDrafts = new RecordingDraftRepository(
                new PlanningDraftVO(
                        "draft-chapter-plan", "novel-001", "CHAPTER_PLANS",
                        new ChapterOutlineVO(17, "ARC_002", "标题", "摘要", "PLANNED"),
                        Instant.parse("2026-08-28T01:00:00Z")
                )
        );
        PlanningService wrongTypeService = service(
                wrongTypeDrafts, new RecordingPlanningRepository()
        );
        assertThatThrownBy(() -> wrongTypeService.confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan", "标题", "摘要"
        )).isInstanceOf(RuntimeException.class);

        RecordingDraftRepository invalidPayloadDrafts = new RecordingDraftRepository(
                new PlanningDraftVO(
                        "draft-chapter-plan", "novel-001", "CHAPTER_PLAN", "invalid",
                        Instant.parse("2026-08-28T01:00:00Z")
                )
        );
        assertThatThrownBy(() -> service(
                invalidPayloadDrafts, new RecordingPlanningRepository()
        ).confirmChapterPlan(
                "novel-001", 17, "draft-chapter-plan", "标题", "摘要"
        )).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> serviceWithDraft(chapterPlanDraft(17, "ARC_002"))
                .confirmChapterPlan(
                        "novel-001", 17, "draft-chapter-plan", " ", "摘要"
                )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> serviceWithDraft(chapterPlanDraft(17, "ARC_002"))
                .confirmChapterPlan(
                        "novel-001", 17, "draft-chapter-plan", "标题", " "
                )).isInstanceOf(RuntimeException.class);

        System.out.println("single confirmation rejects wrong draft type and empty editable fields");
    }

    private static PlanningService serviceWithDraft(PlanningDraftVO draft) {
        return service(
                new RecordingDraftRepository(draft),
                new RecordingPlanningRepository()
        );
    }

    private static PlanningService service(
            RecordingDraftRepository drafts,
            RecordingPlanningRepository repository
    ) {
        return new PlanningService(new NoopModelPort(), drafts, repository);
    }

    private static PlanningDraftVO chapterPlanDraft(int chapterNumber, String outlineNodeCode) {
        return new PlanningDraftVO(
                "draft-chapter-plan", "novel-001", "CHAPTER_PLAN",
                new ChapterOutlineVO(
                        chapterNumber, outlineNodeCode, "模型标题", "模型摘要", "PLANNED"
                ),
                Instant.parse("2026-08-28T01:00:00Z")
        );
    }

    private static final class NoopModelPort implements IPlanningModelPort {
        @Override
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            throw new AssertionError("confirmChapterPlan must not call the model");
        }
    }

    private static final class RecordingDraftRepository implements IPlanningDraftRepository {
        private final PlanningDraftVO draft;
        private final List<String> removed = new ArrayList<>();

        private RecordingDraftRepository(PlanningDraftVO draft) {
            this.draft = draft;
        }

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return draft != null && projectCode.equals(draft.projectCode())
                    && draftId.equals(draft.draftId())
                    ? Optional.of(draft) : Optional.empty();
        }

        @Override
        public void remove(String projectCode, String draftId) {
            removed.add(projectCode + "/" + draftId);
        }

        @Override
        public void removeByProject(String projectCode) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingPlanningRepository implements IPlanningRepository {
        private final OutlineNodeVO target = new OutlineNodeVO(
                "ARC_002", "VOLUME_001", OutlineNodeKindEnum.ARC, 2,
                "宗门大比", "宗门大比中与赵无极正面冲突", 11, 20, "READY"
        );
        private List<OutlineNodeVO> outlines = new ArrayList<>(List.of(
                new OutlineNodeVO(
                        "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                        "全书", "全书方向", 1, 300, "READY"
                ),
                new OutlineNodeVO(
                        "VOLUME_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                        "当前卷", "当前卷方向", 1, 40, "READY"
                ),
                target
        ));
        private List<ChapterOutlineVO> existingPlans = List.of();
        private ChapterOutlineVO upserted;

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return "novel-001".equals(projectCode)
                    ? new NovelProjectVO(projectCode, "测试项目", "玄幻", 300, 2000, 16, "READY")
                    : null;
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return "novel-001".equals(projectCode)
                    ? outlines.stream()
                    .filter(outline -> outline != null && nodeCode.equals(outline.nodeCode()))
                    .findFirst()
                    .orElse(null)
                    : null;
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
            return existingPlans;
        }

        @Override
        public ChapterOutlineVO upsertChapterPlan(
                String projectCode,
                ChapterOutlineVO chapterPlan
        ) {
            upserted = chapterPlan;
            return chapterPlan;
        }

    }
}
