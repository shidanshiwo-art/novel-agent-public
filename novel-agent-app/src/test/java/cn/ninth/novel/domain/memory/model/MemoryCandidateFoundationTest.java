package cn.ninth.novel.domain.memory.model;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.service.agent.DraftChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviseChapterNode;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryCandidateFoundationTest {

    private static final String ORIGINAL = "沈夜将残晶放入消防栓箱。";
    private static final String REVISED = "沈夜将残晶藏入旧消防栓箱。";

    @Test
    void shouldRejectCandidateWithoutSourceVersionBinding() {
        MemoryEvidenceRef evidence = MemoryEvidenceRef.of(0, 2);

        assertThatThrownBy(() -> new MemoryCandidate(
                "candidate-1", null, evidence,
                MemoryCandidateType.EVENT, MemoryCandidateStatus.PROVISIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceVersion");
        System.out.println("Candidate 缺少 chapterVersion/contentHash 绑定时拒绝");
    }

    @Test
    void shouldMarkCandidateStaleWhenContentHashDoesNotMatch() {
        MemorySourceVersion version = MemorySourceVersion.create("chapter-9-v1", ORIGINAL);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                version, ORIGINAL, 0, 2, MemoryCandidateType.EVENT);

        MemoryCandidate stale = candidate.revalidate(
                MemorySourceVersion.create("chapter-9-v2", REVISED), REVISED);

        assertThat(stale.candidateStatus()).isEqualTo(MemoryCandidateStatus.STALE);
        assertThat(stale.contentHash()).isEqualTo(version.contentHash());
        System.out.printf("contentHash 变化自动 stale：old=%s, new=%s%n",
                version.contentHash(), stale.candidateStatus());
    }

    @Test
    void shouldRejectEvidenceOutsideTheBoundChapter正文() {
        MemorySourceVersion version = MemorySourceVersion.create("chapter-9-v1", ORIGINAL);

        assertThatThrownBy(() -> MemoryCandidate.provisional(
                version, ORIGINAL, 0, ORIGINAL.length() + 1, MemoryCandidateType.FACT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceRange");
        System.out.println("evidenceRange 超出正文边界时拒绝 Candidate");
    }

    @Test
    void shouldStaleOldCandidatesAfterStateReceivesRevised正文() {
        MemorySourceVersion originalVersion = MemorySourceVersion.create(
                "chapter-9-v1", ORIGINAL);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                originalVersion, ORIGINAL, 0, 2, MemoryCandidateType.FACT);
        MemorySourceVersion revisedVersion = MemorySourceVersion.create(
                "chapter-9-v2", REVISED);

        ChapterGraphState revisedState = new ChapterGraphState(Map.of(
                ChapterGraphKeys.DRAFT, REVISED,
                ChapterGraphKeys.SOURCE_VERSION, revisedVersion,
                ChapterGraphKeys.MEMORY_CANDIDATES, List.of(candidate)
        ));

        assertThat(revisedState.memoryCandidates())
                .singleElement()
                .extracting(MemoryCandidate::candidateStatus)
                .isEqualTo(MemoryCandidateStatus.STALE);
        System.out.println("REVISE 版本切换后旧 Candidate 自动标记 STALE");
    }

    @Test
    void shouldRoundTripCandidatesThroughCheckpointAsTemporaryState() {
        MemorySourceVersion version = MemorySourceVersion.create("chapter-9-v1", ORIGINAL);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                version, ORIGINAL, 0, 2, MemoryCandidateType.OPEN_LOOP_CHANGE);
        ChapterCheckpointStateCodec codec = new ChapterCheckpointStateCodec(new ObjectMapper());

        String encoded = codec.encode(Map.of(
                ChapterGraphKeys.DRAFT, ORIGINAL,
                ChapterGraphKeys.SOURCE_VERSION, version,
                ChapterGraphKeys.MEMORY_CANDIDATES, List.of(candidate)
        ));
        Map<String, Object> restored = codec.decode(encoded);

        assertThat(restored.get(ChapterGraphKeys.SOURCE_VERSION))
                .isInstanceOf(MemorySourceVersion.class);
        assertThat(restored.get(ChapterGraphKeys.MEMORY_CANDIDATES))
                .isEqualTo(List.of(candidate));
        System.out.println("Candidate 可进入 checkpoint 临时 State，未转换为 Canonical");
    }

    @Test
    void shouldNotReuseTheOldCandidateWhenDraftRetryCreatesNewVersion() {
        MemorySourceVersion originalVersion = MemorySourceVersion.create(
                "chapter-9-v1", ORIGINAL);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                originalVersion, ORIGINAL, 0, 2, MemoryCandidateType.EVENT);

        Map<String, Object> update = new DraftChapterNode(modelReturning(REVISED)).apply(
                new ChapterGraphState(Map.of(
                        ChapterGraphKeys.CONTEXT, context(),
                        ChapterGraphKeys.DRAFT, ORIGINAL,
                        ChapterGraphKeys.SOURCE_VERSION, originalVersion,
                        ChapterGraphKeys.MEMORY_CANDIDATES, List.of(candidate)
                ))
        );

        assertThat(update.get(ChapterGraphKeys.SOURCE_VERSION))
                .isInstanceOf(MemorySourceVersion.class)
                .isNotEqualTo(originalVersion);
        assertThat(update.get(ChapterGraphKeys.MEMORY_CANDIDATES))
                .asList()
                .singleElement()
                .extracting(value -> ((MemoryCandidate) value).candidateStatus())
                .isEqualTo(MemoryCandidateStatus.STALE);
        System.out.println("DRAFT retry 产生新正文版本，未复用旧版本 Candidate");
    }

    @Test
    void shouldStaleCandidatesWhenReviseNodeProducesNew正文() {
        MemorySourceVersion originalVersion = MemorySourceVersion.create(
                "chapter-9-v1", ORIGINAL);
        MemoryCandidate candidate = MemoryCandidate.provisional(
                originalVersion, ORIGINAL, 0, 2, MemoryCandidateType.FACT);

        Map<String, Object> update = new ReviseChapterNode(modelReturning(REVISED)).apply(
                new ChapterGraphState(Map.of(
                        ChapterGraphKeys.CONTEXT, context(),
                        ChapterGraphKeys.DRAFT, ORIGINAL,
                        ChapterGraphKeys.REVIEW_REPORT, new ReviewReportVO(),
                        ChapterGraphKeys.SOURCE_VERSION, originalVersion,
                        ChapterGraphKeys.MEMORY_CANDIDATES, List.of(candidate)
                ))
        );

        assertThat(update.get(ChapterGraphKeys.MEMORY_CANDIDATES))
                .asList()
                .singleElement()
                .extracting(value -> ((MemoryCandidate) value).candidateStatus())
                .isEqualTo(MemoryCandidateStatus.STALE);
        System.out.println("REVISE 产生新正文版本，旧 Candidate 不再有效");
    }

    private ChapterContextAggregate context() {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("memory-test").build())
                .storyBible(cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(9)
                        .title("旧渡口")
                        .summary("本章摘要")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_009", "VOL_001", OutlineNodeKindEnum.ARC, 9,
                        "旧渡口", "本章摘要", 9, 9, "READY"))
                .build();
    }

    private IChapterModelPort modelReturning(String content) {
        return new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return Flux.just(content);
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                return content;
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
