package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.service.agent.CompressChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.DraftChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.PersistChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviewChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviseChapterNode;
import cn.ninth.novel.domain.chapter.service.data.IDataService;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionRegistry;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.ChapterGraphStateSerializer;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.infrastructure.checkpoint.ChapterCheckpointStateCodec;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证工作流节点失败后清理 checkpoint，下一次生成可以立即使用新会话重试。 */
class ChapterServiceRetryTest {

    @Test
    void shouldReleaseFailedWorkflowBeforeImmediateRetry() throws InterruptedException {
        IDataService dataService = mock(IDataService.class);
        ChapterContextAggregate context = readyContext();
        when(dataService.loadContext("retry-project", 1))
                .thenThrow(new IllegalStateException("模拟 LOAD_CONTEXT 失败"))
                .thenReturn(context);

        DraftChapterNode draftNode = mock(DraftChapterNode.class);
        when(draftNode.applyStreaming(any(), any(), any(), any()))
                .thenReturn(Map.of(
                        ChapterGraphKeys.DRAFT, "可重试正文",
                        ChapterGraphKeys.CURRENT_NODE, "DRAFT",
                        ChapterGraphKeys.COMPLETED_STAGES, List.of("DRAFT"),
                        ChapterGraphKeys.RETRY_COUNT, 0,
                        ChapterGraphKeys.GENERATION_METRICS_DELTA,
                        GenerationMetricsDelta.empty()
                ));

        ReviewChapterNode reviewNode = mock(ReviewChapterNode.class);
        when(reviewNode.apply(any())).thenReturn(Map.of(
                ChapterGraphKeys.REVIEW_REPORT,
                ReviewReportVO.builder().reviewIssueVOList(List.of()).build(),
                ChapterGraphKeys.CURRENT_NODE, "REVIEW",
                ChapterGraphKeys.COMPLETED_STAGES, List.of("REVIEW"),
                ChapterGraphKeys.RETRY_COUNT, 0,
                ChapterGraphKeys.FAILURE_MESSAGE, "",
                ChapterGraphKeys.GENERATION_METRICS_DELTA,
                GenerationMetricsDelta.empty()
        ));

        CompressChapterNode compressNode = mock(CompressChapterNode.class);
        when(compressNode.apply(any())).thenReturn(Map.of(
                ChapterGraphKeys.MEMORY,
                ChapterMemoryVO.builder()
                        .chapterNumber(1)
                        .shortSummary("摘要")
                        .keyEvents(List.of())
                        .unresolved(List.of())
                        .endingHook("")
                        .build(),
                ChapterGraphKeys.STORY_STATE_SNAPSHOT, StoryStateSnapshot.empty(),
                ChapterGraphKeys.CURRENT_NODE, "COMPRESSION",
                ChapterGraphKeys.COMPLETED_STAGES, List.of("COMPRESSION"),
                ChapterGraphKeys.RETRY_COUNT, 0,
                ChapterGraphKeys.GENERATION_METRICS_DELTA,
                GenerationMetricsDelta.empty()
        ));

        PersistChapterNode persistNode = mock(PersistChapterNode.class);
        when(persistNode.apply(any())).thenReturn(Map.of(
                ChapterGraphKeys.CURRENT_NODE, "PERSIST",
                ChapterGraphKeys.COMPLETED_STAGES, List.of("PERSIST")
        ));

        TrackingCheckpointSaver checkpointSaver = new TrackingCheckpointSaver();
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        ChapterService service = new ChapterService(
                dataService,
                mock(INovelProjectRepository.class),
                draftNode,
                reviewNode,
                mock(ReviseChapterNode.class),
                new ReviewRouter(),
                compressNode,
                persistNode,
                mock(IChapterPersistRepository.class),
                registry,
                new HumanDecisionRouter(),
                checkpointSaver,
                new ChapterGraphStateSerializer(
                        new ChapterCheckpointStateCodec(new ObjectMapper())
                )
        );

        String firstWorkflowId = service.createGenerationSession("retry-project", 1);
        awaitSnapshotStatus(registry, firstWorkflowId, "FAILED");
        assertThat(checkpointSaver.activeThreadCount()).isZero();

        String retryWorkflowId = service.createGenerationSession("retry-project", 1);
        awaitSnapshotStatus(registry, retryWorkflowId, ChapterWorkflowStatusEnum.COMPLETED.name());
        ChapterGenerationSessionSnapshot retrySnapshot = registry
                .findRecoverableSession("retry-project", 1)
                .orElseThrow();

        assertThat(retrySnapshot.workflowId()).isEqualTo(retryWorkflowId);
        assertThat(retrySnapshot.status()).isEqualTo(ChapterWorkflowStatusEnum.COMPLETED.name());
        assertThat(retrySnapshot.accumulatedContent()).isEqualTo("可重试正文");
        awaitNoActiveCheckpoint(checkpointSaver);
        assertThat(checkpointSaver.activeThreadCount()).isZero();
        assertThat(checkpointSaver.releaseCount()).isGreaterThanOrEqualTo(2);
        registry.register("workflow-review-stop", "retry-project", 1);
        registry.publish("workflow-review-stop", cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.DRAFT_COMPLETED);
        registry.publish(
                "workflow-review-stop",
                cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.REVIEW_FAILED,
                "自动审稿失败"
        );
        int releasesBeforeStoppedSession = checkpointSaver.releaseCount();
        service.stopGenerationSession("workflow-review-stop");
        assertThat(registry.status("workflow-review-stop"))
                .contains(ChapterWorkflowStatusEnum.CANCELLED);
        assertThat(checkpointSaver.releaseCount()).isEqualTo(releasesBeforeStoppedSession + 1);
        System.out.printf(
                "章节失败后立即重试通过：firstStatus=FAILED，retryStatus=%s，checkpointReleases=%d；暂停态 STOP 释放通过%n",
                retrySnapshot.status(),
                checkpointSaver.releaseCount()
        );
    }

    private static void awaitSnapshotStatus(
            ChapterGenerationSessionRegistry registry,
            String workflowId,
            String expected
    ) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Optional<ChapterGenerationSessionSnapshot> snapshot = registry
                    .findRecoverableSession("retry-project", 1)
                    .filter(value -> workflowId.equals(value.workflowId()));
            if (snapshot.map(ChapterGenerationSessionSnapshot::status)
                    .filter(expected::equals)
                    .isPresent()) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("workflowId=" + workflowId + " 未在限定时间内进入 " + expected);
    }

    private static void awaitNoActiveCheckpoint(
            TrackingCheckpointSaver checkpointSaver
    ) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (checkpointSaver.activeThreadCount() == 0) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("checkpoint thread 未在限定时间内释放");
    }

    private static ChapterContextAggregate readyContext() {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder()
                        .projectCode("retry-project")
                        .title("重试测试")
                        .build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(1)
                        .outlineNodeCode("ARC-001")
                        .title("第一章")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC-001",
                        "VOL-001",
                        OutlineNodeKindEnum.ARC,
                        1,
                        "第一章",
                        "摘要",
                        1,
                        1,
                        "PLANNED"
                ))
                .build();
    }

    private static final class TrackingCheckpointSaver implements BaseCheckpointSaver {
        private final ConcurrentMap<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();
        private final AtomicInteger releases = new AtomicInteger();

        @Override
        public Collection<Checkpoint> list(RunnableConfig config) {
            Checkpoint checkpoint = checkpoints.get(configuredThreadId(config));
            return checkpoint == null ? List.of() : List.of(checkpoint);
        }

        @Override
        public Optional<Checkpoint> get(RunnableConfig config) {
            return Optional.ofNullable(checkpoints.get(configuredThreadId(config)));
        }

        @Override
        public RunnableConfig put(
                RunnableConfig config,
                Checkpoint checkpoint
        ) {
            checkpoints.put(configuredThreadId(config), checkpoint);
            return checkpoint.getId() == null
                    ? config
                    : config.withCheckPointId(checkpoint.getId());
        }

        @Override
        public Tag release(RunnableConfig config) {
            releases.incrementAndGet();
            Checkpoint checkpoint = checkpoints.remove(configuredThreadId(config));
            return new Tag(
                    configuredThreadId(config),
                    checkpoint == null ? List.of() : List.of(checkpoint)
            );
        }

        private String configuredThreadId(RunnableConfig config) {
            return config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT);
        }

        private int activeThreadCount() {
            return checkpoints.size();
        }

        private int releaseCount() {
            return releases.get();
        }
    }
}
