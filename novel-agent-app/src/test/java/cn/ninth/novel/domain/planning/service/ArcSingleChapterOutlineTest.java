package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChildOutlineDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArcSingleChapterOutlineTest {

    @Test
    void shouldAllocateOnlyTheNextAvailableChapter() {
        RecordingRepository repository = new RecordingRepository(List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                        "全书", "全书方向", 1, 120, "READY"),
                node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                        "第一卷", "卷方向", 1, 120, "READY"),
                node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                        "第一章", "已完成的章纲", 1, 1, "READY")
        ));
        RecordingModelPort model = new RecordingModelPort("下一章", "继续调查");
        PlanningDraftVO draft = new PlanningService(model, new RecordingDrafts(), repository)
                .generateNextOutline("novel-001", "VOL_001", "加强悬念");

        OutlineNodeVO next = (OutlineNodeVO) draft.payload();
        System.out.printf("next available ARC: code=%s, chapter=%s%n",
                next.nodeCode(), next.startChapter());
        assertThat(next.nodeKind()).isEqualTo(OutlineNodeKindEnum.ARC);
        assertThat(next.startChapter()).isEqualTo(2);
        assertThat(next.endChapter()).isEqualTo(2);
        assertThat(model.userPrompt).contains(
                        "【上一章大纲】", "【当前卷已有章节大纲】",
                        "【本次目标章节】", "第2章", "加强悬念"
                )
                .doesNotContain("建议数量", "children");
        System.out.println("ARC next-step allocation verified: chapter 2 follows chapter 1");
    }

    @Test
    void shouldGenerateChapterAfterLatestExistingArc() {
        RecordingRepository repository = new RecordingRepository(List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                        "全书", "全书方向", 1, 120, "READY"),
                node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                        "第一卷", "卷方向", 1, 60, "READY"),
                node("ARC_028", "VOL_001", OutlineNodeKindEnum.ARC, 28,
                        "第二十八章", "已完成", 28, 28, "READY"),
                node("ARC_029", "VOL_001", OutlineNodeKindEnum.ARC, 29,
                        "第二十九章", "已完成", 29, 29, "READY"),
                node("ARC_030", "VOL_001", OutlineNodeKindEnum.ARC, 30,
                        "第三十章", "已完成", 30, 30, "READY")
        ));
        PlanningDraftVO draft = new PlanningService(
                new RecordingModelPort("第三十一章", "继续调查"),
                new RecordingDrafts(),
                repository
        ).generateNextOutline("novel-001", "VOL_001", "推进真相");

        OutlineNodeVO next = (OutlineNodeVO) draft.payload();
        System.out.printf("next ARC after latest existing chapter: code=%s, chapter=%s%n",
                next.nodeCode(), next.startChapter());
        assertThat(next.startChapter()).isEqualTo(31);
        assertThat(next.endChapter()).isEqualTo(31);
        System.out.println("ARC latest-chapter allocation verified: chapter 31 follows chapters 28-30");
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

    private static final class RecordingModelPort implements IPlanningModelPort {
        private final String title;
        private final String summary;
        private String userPrompt;

        private RecordingModelPort(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            this.userPrompt = userPrompt;
            assertThat(responseType).isEqualTo(ChildOutlineDraftVO.class);
            return (T) new ChildOutlineDraftVO(title, summary);
        }
    }

    private static final class RecordingDrafts implements IPlanningDraftRepository {
        private final List<PlanningDraftVO> drafts = new ArrayList<>();
        private final List<String> removedIds = new ArrayList<>();

        @Override
        public PlanningDraftVO save(String projectCode, String type, Object payload) {
            PlanningDraftVO draft = new PlanningDraftVO(
                    "draft-" + (drafts.size() + 1), projectCode, type, payload,
                    Instant.parse("2026-09-04T01:00:00Z"));
            drafts.add(draft);
            return draft;
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return drafts.stream().filter(draft -> draft.draftId().equals(draftId)).findFirst();
        }

        @Override
        public void remove(String projectCode, String draftId) {
            removedIds.add(draftId);
            drafts.removeIf(draft -> draft.draftId().equals(draftId));
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }

    private static final class RecordingRepository implements IPlanningRepository {
        private final List<OutlineNodeVO> nodes;

        private RecordingRepository(List<OutlineNodeVO> nodes) {
            this.nodes = new ArrayList<>(nodes);
        }

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(projectCode, "归途", "东方奇幻", 120,
                    2500, 0, "DRAFT");
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            return new StoryBibleVO("少年开始调查", "真相与选择", "主角与谜案的对抗",
                    "主角找到真相", "边城", null, null, null, "CONFIRMED");
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            return List.of();
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
            nodes.add(outline);
            return outline;
        }

        @Override
        public OutlineNodeVO updateOutline(String projectCode, OutlineNodeVO outline) {
            for (int index = 0; index < nodes.size(); index++) {
                if (nodes.get(index).nodeCode().equals(outline.nodeCode())) {
                    nodes.set(index, outline);
                    return outline;
                }
            }
            throw new IllegalArgumentException("missing node: " + outline.nodeCode());
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return nodes.stream().anyMatch(node -> nodeCode.equals(node.parentNodeCode()));
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return false;
        }
    }
}
