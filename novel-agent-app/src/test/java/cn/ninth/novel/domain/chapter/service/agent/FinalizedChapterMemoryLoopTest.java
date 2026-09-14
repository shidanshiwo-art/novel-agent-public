package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.adapter.repository.MemoryCommitRepository;
import cn.ninth.novel.domain.memory.model.CanonicalEvent;
import cn.ninth.novel.domain.memory.model.CanonicalFact;
import cn.ninth.novel.domain.memory.model.CanonicalProjection;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryCommitPlan;
import cn.ninth.novel.domain.memory.model.MemoryCommitResult;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.domain.memory.service.MemoryCommitGate;
import cn.ninth.novel.domain.memory.service.MemoryReconciler;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 FINALIZED 正文完成 Extract → Reconcile → Gate → Commit 后，
 * 下一章能继续从 Canonical Context 读取上一章的新证据。
 */
class FinalizedChapterMemoryLoopTest {

    @Test
    void shouldCommitChapters11To16AndRecallEachPreviousCanonicalChapter() {
        RecordingCommitRepository commitRepository = new RecordingCommitRepository(10);
        MemoryCommitGate gate = new MemoryCommitGate(commitRepository);
        IChapterPersistRepository persistence = new GatePersistence(gate);
        PersistChapterNode persistNode = new PersistChapterNode(persistence);

        assertThat(commitRepository.commitCount()).isEqualTo(10);
        MemoryContextPack previousCanonical = null;
        List<String> recalledChapters = new ArrayList<>();
        for (int chapter = 11; chapter <= 16; chapter++) {
            String content = content(chapter);
            ChapterContextAggregate context = context(chapter, previousCanonical);
            ChapterGraphState state = new ChapterGraphState(Map.of(
                    ChapterGraphKeys.PROJECT_CODE, "loop-project",
                    ChapterGraphKeys.CHAPTER_NUMBER, chapter,
                    ChapterGraphKeys.MEMORY_MODE, MemoryMode.V1,
                    ChapterGraphKeys.CONTEXT, context,
                    ChapterGraphKeys.DRAFT, content,
                    ChapterGraphKeys.SOURCE_VERSION, MemorySourceVersion.create(
                            "final:loop-project:" + chapter, content),
                    ChapterGraphKeys.MEMORY, chapterMemory(chapter)
            ));

            if (previousCanonical != null) {
                String previousChapter = "第" + (chapter - 1) + "章";
                String nextPrompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
                recalledChapters.add(previousChapter + "=" + nextPrompt.contains(previousChapter));
                assertThat(nextPrompt)
                        .as("Chapter %d 必须召回上一章 Canonical Memory", chapter)
                        .contains(previousChapter)
                        .contains("[CURRENT_STATE]")
                        .contains("[BACKGROUND_ONLY]");
            }

            persistNode.apply(state);
            MemoryCommitPlan plan = commitRepository.lastPlan();
            System.out.printf(
                    "FINALIZED Memory loop：chapter=%d, candidates=%d, events=%d, facts=%d, "
                            + "projections=%d, outbox=%d%n",
                    chapter,
                    plan.events().size() + plan.facts().size() + plan.projections().size(),
                    plan.events().size(), plan.facts().size(),
                    plan.projections().size(), plan.outboxEntries().size());

            assertThat(plan.finalSourceVersion().matchesContent(content)).isTrue();
            assertThat(plan.events()).isNotEmpty()
                    .allMatch(event -> event.sourceVersion().matchesContent(content)
                            && event.evidenceRange().resolve(content).equals(event.description()));
            assertThat(plan.facts()).isNotEmpty();
            assertThat(plan.projections()).isNotEmpty();
            assertThat(plan.outboxEntries()).isNotEmpty();

            previousCanonical = contextFrom(plan);
        }

        System.out.printf(
                "Ch11~16 Canonical 闭环：commit=%d, accepted=%d, recalled=%s%n",
                commitRepository.commitCount(), commitRepository.acceptedCount(), recalledChapters);
        assertThat(commitRepository.commitCount()).isEqualTo(16);
        assertThat(commitRepository.acceptedCount()).isEqualTo(16);
        assertThat(recalledChapters).containsExactly(
                "第11章=true", "第12章=true", "第13章=true", "第14章=true", "第15章=true");
    }

    @Test
    void shouldReinforceAndSupersedeInsteadOfBlindlyAddingTheSameFact() {
        RecordingCommitRepository commitRepository = new RecordingCommitRepository();
        MemoryCommitGate gate = new MemoryCommitGate(commitRepository);
        PersistChapterNode persistNode = new PersistChapterNode(new GatePersistence(gate));

        MemoryContextPack previous = null;
        String currentFactId = null;
        for (int chapter : List.of(11, 12, 13)) {
            String content = chapter == 11
                    ? "沈夜进入旧钟楼。残晶位于旧钟楼。买家身份仍未查明。"
                    : chapter == 12
                    ? "沈夜离开旧钟楼。残晶位于北港。买家身份仍未查明。"
                    : "沈夜回到北港。残晶位于北港。买家身份仍未查明。";
            ChapterContextAggregate context = context(chapter, previous);
            persistNode.apply(new ChapterGraphState(Map.of(
                    ChapterGraphKeys.PROJECT_CODE, "reconcile-project",
                    ChapterGraphKeys.CHAPTER_NUMBER, chapter,
                    ChapterGraphKeys.MEMORY_MODE, MemoryMode.V1,
                    ChapterGraphKeys.CONTEXT, context,
                    ChapterGraphKeys.DRAFT, content,
                    ChapterGraphKeys.SOURCE_VERSION, MemorySourceVersion.create(
                            "final:reconcile-project:" + chapter, content),
                    ChapterGraphKeys.MEMORY, chapterMemory(chapter)
            )));
            MemoryCommitPlan plan = commitRepository.lastPlan();
            if (chapter == 12) {
                assertThat(plan.factStatusChanges()).as("Ch12 应归档旧位置 Fact")
                        .hasSize(1);
                assertThat(plan.facts()).as("Ch12 应加入新的位置 Fact").hasSize(1);
            }
            if (chapter == 13) {
                assertThat(plan.factStatusChanges()).as("Ch13 不应再次归档同一位置 Fact")
                        .isEmpty();
                assertThat(plan.facts()).as("Ch13 应 REINFORCE 原位置 Fact")
                        .hasSize(1);
                assertThat(plan.facts().get(0).factId()).isEqualTo(currentFactId);
            }
            if (chapter == 12) {
                currentFactId = plan.facts().get(0).factId();
            }
            previous = contextFrom(plan);
        }

        System.out.println("Reconcile 回放：Ch12=SUPERSEDE，Ch13=REINFORCE，未盲目新增重复 Fact");
        assertThat(commitRepository.commitCount()).isEqualTo(3);
    }

    private ChapterContextAggregate context(
            int chapter,
            MemoryContextPack memoryContextPack
    ) {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("loop-project").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(chapter)
                        .outlineNodeCode("ARC_1")
                        .title("闭环章节")
                        .summary("推进 Canonical Memory")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_1", "VOL_1", OutlineNodeKindEnum.ARC, 1,
                        "闭环章节", "推进 Canonical Memory", 1, 20, "READY"))
                .memoryContextPack(memoryContextPack)
                .build();
    }

    private MemoryContextPack contextFrom(MemoryCommitPlan plan) {
        List<MemoryContextItem> facts = plan.facts().stream()
                .map(fact -> MemoryContextItem.canonical(
                        "CANONICAL_FACT", fact.factId(), MemoryContextCategory.CURRENT_STATES,
                        fact.proposition(), plan.chapterNumber(), plan.finalSourceVersion().chapterVersion(),
                        fact.status().name(), null, null))
                .toList();
        List<MemoryContextItem> events = plan.events().stream()
                .map(event -> MemoryContextItem.canonical(
                        "CANONICAL_EVENT", event.eventId(), MemoryContextCategory.EPISODES,
                        event.description(), event.chapterNumber(), event.chapterVersion(),
                        "ACCEPTED", null, event.chapterNumber()))
                .toList();
        List<MemoryContextItem> projections = plan.projections().stream()
                .map(projection -> MemoryContextItem.canonical(
                        "CANONICAL_PROJECTION", projection.projectionId(),
                        projection.projectionId().startsWith("runtime:open-loop:")
                                ? MemoryContextCategory.OPEN_LOOPS
                                : MemoryContextCategory.CONSOLIDATED,
                        projection.content(), plan.chapterNumber(),
                        plan.finalSourceVersion().chapterVersion(), projection.status().name(),
                        null, plan.chapterNumber()))
                .toList();
        return new MemoryContextPack(
                cn.ninth.novel.domain.memory.model.MemoryProfile.DRAFT,
                List.of(),
                projections.stream()
                        .filter(item -> item.category() == MemoryContextCategory.OPEN_LOOPS)
                        .toList(),
                facts,
                projections.stream()
                        .filter(item -> item.category() == MemoryContextCategory.CONSOLIDATED)
                        .toList(),
                events,
                facts.size() + events.size() + projections.size());
    }

    private ChapterMemoryVO chapterMemory(int chapter) {
        return new ChapterMemoryVO(
                chapter, "章节摘要", List.of("关键事件"), List.of("未解决问题"), "结尾钩子");
    }

    private String content(int chapter) {
        return "沈夜进入第" + chapter + "章的旧钟楼。"
                + "残晶位于第" + chapter + "章的北港。"
                + "买家身份仍未查明。";
    }

    private static final class GatePersistence implements IChapterPersistRepository {
        private final MemoryCommitGate gate;

        private GatePersistence(MemoryCommitGate gate) {
            this.gate = gate;
        }

        @Override
        public void persist(
                String projectCode,
                int chapterNumber,
                String content,
                ChapterMemoryVO chapterMemory
        ) {
            throw new AssertionError("V1 FINALIZED 必须经过 Canonical Gate");
        }

        @Override
        public void persistWithMemoryCommit(
                String projectCode,
                int chapterNumber,
                String content,
                ChapterMemoryVO chapterMemory,
                StoryStateSnapshot storyStateSnapshot,
                cn.ninth.novel.domain.memory.model.MemoryCommitRequest request
        ) {
            gate.commit(request);
        }
    }

    private static final class RecordingCommitRepository implements MemoryCommitRepository {
        private final List<MemoryCommitPlan> plans = new ArrayList<>();
        private final Map<String, MemoryCommitPlan> byKey = new LinkedHashMap<>();
        private final int baselineCount;

        private RecordingCommitRepository() {
            this(0);
        }

        private RecordingCommitRepository(int baselineCount) {
            this.baselineCount = baselineCount;
        }

        @Override
        public MemoryCommitResult commit(MemoryCommitPlan plan) {
            if (byKey.putIfAbsent(plan.commitKey(), plan) != null) {
                return MemoryCommitResult.alreadyCommitted(plan.commitKey());
            }
            plans.add(plan);
            return new MemoryCommitResult(
                    plan.commitKey(), false, plan.events().size(), plan.facts().size(),
                    plan.projections().size(), plan.outboxEntries().size());
        }

        private MemoryCommitPlan lastPlan() {
            return plans.get(plans.size() - 1);
        }

        private int commitCount() {
            return baselineCount + plans.size();
        }

        private int acceptedCount() {
            return baselineCount + plans.size();
        }
    }
}
