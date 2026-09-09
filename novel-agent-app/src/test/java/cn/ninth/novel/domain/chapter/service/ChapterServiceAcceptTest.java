package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.agent.CompressChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.DraftChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.PersistChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviewChapterNode;
import cn.ninth.novel.domain.chapter.service.agent.ReviseChapterNode;
import cn.ninth.novel.domain.chapter.service.data.IDataService;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionEvent;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionRegistry;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.chapter.service.workflow.HumanDecisionRouter;
import cn.ninth.novel.domain.chapter.service.workflow.ReviewRouter;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ChapterServiceAcceptTest {

    @Test
    void shouldResumePassFromWaitingHumanAfterMarkingAccepted() {
        assertAcceptResumesFromCheckpoint(
                "workflow-accept-waiting-human",
                ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED,
                "WAITING_HUMAN"
        );
    }

    @Test
    void shouldResumePassFromReviewFailedAfterMarkingAccepted() {
        assertAcceptResumesFromCheckpoint(
                "workflow-accept-review-failed",
                ChapterGenerationEventType.REVIEW_FAILED,
                "REVIEW_FAILED"
        );
    }

    @Test
    void shouldResumeOnlyOnceWhenAcceptIsRequestedRepeatedly() {
        String workflowId = "workflow-accept-repeat";
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register(workflowId, "accept-test-project", 1);
        registry.publish(workflowId, ChapterGenerationEventType.DRAFT_COMPLETED);
        registry.publish(workflowId, ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED);
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe(workflowId, received::add);

        ChapterService service = spy(newService(registry));
        doAnswer(invocation -> {
            registry.publish(workflowId, ChapterGenerationEventType.COMPRESSION_COMPLETED);
            registry.publish(workflowId, ChapterGenerationEventType.PERSIST_STARTED);
            registry.publish(workflowId, ChapterGenerationEventType.PERSIST_COMPLETED);
            registry.publish(workflowId, ChapterGenerationEventType.GENERATION_COMPLETED);
            return completedResult(workflowId);
        }).when(service)
                .resumeChapter(workflowId, HumanDecisionEnum.PASS);

        service.acceptGenerationSession(workflowId);
        service.acceptGenerationSession(workflowId);

        verify(service, timeout(2000).times(1))
                .resumeChapter(workflowId, HumanDecisionEnum.PASS);
        assertThat(received)
                .extracting(ChapterGenerationSessionEvent::type)
                .containsExactly(
                        ChapterGenerationEventType.DRAFT_COMPLETED,
                        ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED,
                        ChapterGenerationEventType.COMPRESSION_STARTED,
                        ChapterGenerationEventType.COMPRESSION_COMPLETED,
                        ChapterGenerationEventType.PERSIST_STARTED,
                        ChapterGenerationEventType.PERSIST_COMPLETED,
                        ChapterGenerationEventType.GENERATION_COMPLETED
                );
        System.out.println("重复 ACCEPT 检查：第二次返回 alreadyAccepted，PASS 恢复仅触发一次");
    }

    @Test
    void shouldContinuePostReviewStagesWithoutReEmittingReviewEvents() {
        assertAcceptEventSequence(
                "workflow-accept-human-event-order",
                ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED
        );
        assertAcceptEventSequence(
                "workflow-accept-failed-event-order",
                ChapterGenerationEventType.REVIEW_FAILED
        );
    }

    private void assertAcceptEventSequence(
            String workflowId,
            ChapterGenerationEventType checkpointEvent
    ) {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register(workflowId, "accept-test-project", 1);
        registry.publish(workflowId, ChapterGenerationEventType.DRAFT_COMPLETED);
        registry.publish(workflowId, checkpointEvent, "自动审稿失败");
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe(workflowId, received::add);

        ChapterService service = spy(newService(registry));
        doAnswer(invocation -> {
            registry.publish(workflowId, ChapterGenerationEventType.COMPRESSION_COMPLETED);
            registry.publish(workflowId, ChapterGenerationEventType.PERSIST_STARTED);
            registry.publish(workflowId, ChapterGenerationEventType.PERSIST_COMPLETED);
            registry.publish(workflowId, ChapterGenerationEventType.GENERATION_COMPLETED);
            return completedResult(workflowId);
        }).when(service).resumeChapter(workflowId, HumanDecisionEnum.PASS);

        service.acceptGenerationSession(workflowId);

        verify(service, timeout(2000)).resumeChapter(workflowId, HumanDecisionEnum.PASS);
        List<ChapterGenerationEventType> expected = List.of(
                ChapterGenerationEventType.DRAFT_COMPLETED,
                checkpointEvent,
                ChapterGenerationEventType.COMPRESSION_STARTED,
                ChapterGenerationEventType.COMPRESSION_COMPLETED,
                ChapterGenerationEventType.PERSIST_STARTED,
                ChapterGenerationEventType.PERSIST_COMPLETED,
                ChapterGenerationEventType.GENERATION_COMPLETED
        );
        assertThat(received)
                .extracting(ChapterGenerationSessionEvent::type)
                .containsExactlyElementsOf(expected);
        assertThat(received)
                .extracting(ChapterGenerationSessionEvent::type)
                .doesNotContain(ChapterGenerationEventType.REVIEW_COMPLETED);
        if (checkpointEvent == ChapterGenerationEventType.REVIEW_FAILED) {
            assertThat(received)
                    .extracting(ChapterGenerationSessionEvent::type)
                    .doesNotContain(ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED);
        }
        System.out.printf(
                "人工采用事件顺序检查：checkpoint=%s，events=%s%n",
                checkpointEvent,
                received.stream().map(event -> event.type().name()).toList()
        );
    }

    private void assertAcceptResumesFromCheckpoint(
            String workflowId,
            ChapterGenerationEventType checkpointEvent,
            String checkpointStatus
    ) {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register(workflowId, "accept-test-project", 1);
        registry.publish(workflowId, ChapterGenerationEventType.DRAFT_COMPLETED);
        registry.publish(workflowId, checkpointEvent, "自动审稿失败");

        ChapterService service = spy(newService(registry));
        doReturn(completedResult(workflowId)).when(service)
                .resumeChapter(workflowId, HumanDecisionEnum.PASS);

        service.acceptGenerationSession(workflowId);

        verify(service, timeout(2000))
                .resumeChapter(workflowId, HumanDecisionEnum.PASS);
        assertThat(registry.isAcceptRequested(workflowId)).isTrue();
        assertThat(registry.findActiveSession("accept-test-project", 1)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("COMPRESSING");
            assertThat(snapshot.currentNode()).isEqualTo("COMPRESSION");
            assertThat(snapshot.failureMessage()).isEmpty();
        });
        System.out.printf(
                "ACCEPT checkpoint 恢复检查：status=%s → COMPRESSING，accepted=true，已触发 HumanDecision.PASS%n",
                checkpointStatus
        );
    }

    private ChapterService newService(ChapterGenerationSessionRegistry registry) {
        return new ChapterService(
                mock(IDataService.class),
                mock(INovelProjectRepository.class),
                mock(DraftChapterNode.class),
                mock(ReviewChapterNode.class),
                mock(ReviseChapterNode.class),
                new ReviewRouter(),
                mock(CompressChapterNode.class),
                mock(PersistChapterNode.class),
                mock(IChapterPersistRepository.class),
                registry,
                new HumanDecisionRouter(),
                mock(BaseCheckpointSaver.class),
                mock(StateSerializer.class)
        );
    }

    private ChapterGenerationResultVO completedResult(String workflowId) {
        return new ChapterGenerationResultVO(
                workflowId,
                ChapterWorkflowStatusEnum.COMPLETED,
                "accept-test-project",
                1,
                "已采用正文",
                null,
                null,
                List.of("COMPRESSION", "PERSIST"),
                false
        );
    }
}
