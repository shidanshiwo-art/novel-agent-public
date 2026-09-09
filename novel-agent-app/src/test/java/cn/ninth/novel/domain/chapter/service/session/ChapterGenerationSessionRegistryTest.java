package cn.ninth.novel.domain.chapter.service.session;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.DRAFT_STARTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.DRAFT_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_CANCELLED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_FAILED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_STARTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.COMPRESSION_STARTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.COMPRESSION_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.PERSIST_STARTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.PERSIST_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterGenerationSessionRegistryTest {

    @Test
    void shouldReplayHistoryAndDeliverFollowingEvents() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-001");
        registry.publish("workflow-001", GENERATION_STARTED);

        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        ChapterGenerationSessionRegistry.SubscribeResult subscription =
                registry.subscribe("workflow-001", received::add);

        registry.publish("workflow-001", DRAFT_STARTED);
        registry.publish("workflow-001", GENERATION_COMPLETED);

        assertThat(subscription.found()).isTrue();
        assertThat(subscription.terminal()).isFalse();
        assertThat(received)
                .extracting(event -> event.type().name())
                .containsExactly(
                GENERATION_STARTED.name(),
                DRAFT_STARTED.name(),
                GENERATION_COMPLETED.name()
                );

        List<ChapterGenerationSessionEvent> replayed = new ArrayList<>();
        ChapterGenerationSessionRegistry.SubscribeResult terminalSubscription =
                registry.subscribe("workflow-001", replayed::add);
        assertThat(terminalSubscription.terminal()).isTrue();
        assertThat(replayed).containsExactlyElementsOf(received);
        System.out.println("Session 事件登记表已回放历史事件并推送后续事件，终态后可再次订阅完整历史");
    }

    @Test
    void shouldReportUnknownWorkflowAsNotFound() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();

        ChapterGenerationSessionRegistry.SubscribeResult result =
                registry.subscribe("workflow-missing", event -> { });

        assertThat(result.found()).isFalse();
        assertThat(result.terminal()).isFalse();
        System.out.println("不存在的 workflowId 不会创建隐式 Session");
    }

    @Test
    void shouldFindOnlyNonTerminalSessionForProjectAndChapter() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-active", "novel-001", 3);
        registry.register("workflow-other-chapter", "novel-001", 4);
        registry.register("workflow-other-project", "novel-002", 3);
        registry.register("workflow-completed", "novel-001", 3);
        registry.publish("workflow-completed", GENERATION_COMPLETED);

        assertThat(registry.findActiveWorkflowId("novel-001", 3))
                .contains("workflow-active");
        assertThat(registry.findActiveWorkflowId("novel-001", 4))
                .contains("workflow-other-chapter");
        assertThat(registry.findActiveWorkflowId("novel-002", 3))
                .contains("workflow-other-project");
        assertThat(registry.findActiveWorkflowId("novel-001", 5))
                .isEmpty();
        System.out.println("活动 Session 查询按 projectCode + chapterNumber 匹配，并排除已结束 Session");
    }

    @Test
    void shouldBuildRecoverableSnapshotFromEventsAndWorkflowResult() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-snapshot", "novel-001", 3);
        registry.publish("workflow-snapshot", GENERATION_STARTED);
        registry.publish("workflow-snapshot", DRAFT_STARTED);
        registry.publish("workflow-snapshot", ChapterGenerationEventType.DRAFT_CHUNK, "第一段");
        registry.publish("workflow-snapshot", ChapterGenerationEventType.DRAFT_CHUNK, "第二段");
        registry.publish("workflow-snapshot", DRAFT_COMPLETED);
        registry.updateSnapshot(
                "workflow-snapshot",
                "完整正文",
                List.of("DRAFT"),
                List.of("时间线冲突")
        );
        registry.publish("workflow-snapshot", ChapterGenerationEventType.REVIEW_STARTED);
        registry.publish(
                "workflow-snapshot",
                ChapterGenerationEventType.REVIEW_FAILED,
                "审稿报告校验失败"
        );

        assertThat(registry.findActiveSession("novel-001", 3)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.workflowId()).isEqualTo("workflow-snapshot");
            assertThat(snapshot.chapterNumber()).isEqualTo(3);
            assertThat(snapshot.status()).isEqualTo("REVIEW_FAILED");
            assertThat(snapshot.accumulatedContent()).isEqualTo("完整正文");
            assertThat(snapshot.completedStages()).containsExactly("DRAFT", "REVIEW");
            assertThat(snapshot.reviewIssues()).containsExactly("时间线冲突");
            assertThat(snapshot.failureMessage())
                    .isEqualTo(ChapterGenerationSessionEvent.REVIEW_FAILURE_MESSAGE);
        });
        System.out.println("Session 快照已汇总当前正文、工作流阶段、Review 问题和安全失败文案");
    }

    @Test
    void shouldReplaceReviewIssuesWhenASecondReviewCompletes() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-review-issues", "novel-001", 3);
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe("workflow-review-issues", received::add);

        registry.publish("workflow-review-issues", DRAFT_COMPLETED);
        registry.publish("workflow-review-issues", ChapterGenerationEventType.REVIEW_STARTED);
        registry.publish(
                "workflow-review-issues",
                ChapterGenerationEventType.REVIEW_COMPLETED,
                null,
                List.of("人物动机不足", "结尾冲突偏弱", "时间线需要确认")
        );
        registry.publish("workflow-review-issues", ChapterGenerationEventType.REVISION_STARTED);
        registry.publish("workflow-review-issues", ChapterGenerationEventType.REVISION_COMPLETED);
        registry.publish("workflow-review-issues", ChapterGenerationEventType.REVIEW_STARTED);
        registry.publish(
                "workflow-review-issues",
                ChapterGenerationEventType.REVIEW_COMPLETED,
                null,
                List.of("结尾冲突偏弱")
        );

        assertThat(received.get(2).reviewIssues())
                .containsExactly("人物动机不足", "结尾冲突偏弱", "时间线需要确认");
        assertThat(received.get(6).reviewIssues())
                .containsExactly("结尾冲突偏弱");
        assertThat(registry.findActiveSession("novel-001", 3)).hasValueSatisfying(snapshot ->
                assertThat(snapshot.reviewIssues()).containsExactly("结尾冲突偏弱")
        );
        System.out.println("Review issues 覆盖检查：第二次 REVIEW_COMPLETED 只保留最新 1 个问题，不与首轮列表累加");
    }

    @Test
    void shouldSyncRecoverySnapshotAndCloseCompletedSession() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-recovery", "novel-001", 3);
        registry.publish("workflow-recovery", DRAFT_COMPLETED);
        registry.updateSnapshot(
                "workflow-recovery",
                "采用当前正文",
                List.of("DRAFT"),
                List.of("审稿问题")
        );
        registry.publish(
                "workflow-recovery",
                ChapterGenerationEventType.REVIEW_FAILED,
                "自动审稿失败"
        );

        assertThat(registry.findActiveSession("novel-001", 3)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("REVIEW_FAILED");
            assertThat(snapshot.currentNode()).isEqualTo("REVIEW");
            assertThat(snapshot.failureMessage())
                    .isEqualTo(ChapterGenerationSessionEvent.REVIEW_FAILURE_MESSAGE);
        });

        assertThat(registry.publish("workflow-recovery", COMPRESSION_STARTED)).isTrue();
        assertThat(registry.publish("workflow-recovery", COMPRESSION_STARTED)).isFalse();
        assertThat(registry.findActiveSession("novel-001", 3)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("COMPRESSING");
            assertThat(snapshot.currentNode()).isEqualTo("COMPRESSION");
            assertThat(snapshot.failureMessage()).isEmpty();
            assertThat(snapshot.accumulatedContent()).isEqualTo("采用当前正文");
            assertThat(snapshot.completedStages()).containsExactly("DRAFT", "REVIEW");
        });

        registry.publish("workflow-recovery", COMPRESSION_COMPLETED);
        registry.publish("workflow-recovery", PERSIST_STARTED);
        assertThat(registry.findActiveSession("novel-001", 3)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("PERSISTING");
            assertThat(snapshot.currentNode()).isEqualTo("PERSIST");
            assertThat(snapshot.failureMessage()).isEmpty();
        });

        registry.publish("workflow-recovery", PERSIST_COMPLETED);
        registry.publish("workflow-recovery", GENERATION_COMPLETED);
        assertThat(registry.findRecoverableSession("novel-001", 3)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("COMPLETED");
            assertThat(snapshot.currentNode()).isEqualTo("END");
            assertThat(snapshot.failureMessage()).isEmpty();
            assertThat(snapshot.completedStages())
                    .containsExactly("DRAFT", "REVIEW", "COMPRESSION", "PERSIST");
            assertThat(snapshot.accumulatedContent()).isEqualTo("采用当前正文");
        });
        System.out.println("恢复快照检查：REVIEW_FAILED → COMPRESSING → PERSISTING → COMPLETED，失败文案清空且阶段不重复");
    }

    @Test
    void shouldRejectStopAfterAcceptHasEnteredCompression() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-accept-stop-race", "novel-001", 3);
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe("workflow-accept-stop-race", received::add);
        registry.publish("workflow-accept-stop-race", DRAFT_COMPLETED);
        registry.publish("workflow-accept-stop-race", HUMAN_REVIEW_REQUIRED);

        ChapterGenerationSessionRegistry.AcceptResult accepted =
                registry.requestAccept("workflow-accept-stop-race");
        assertThat(accepted.accepted()).isTrue();
        assertThat(registry.publish("workflow-accept-stop-race", COMPRESSION_STARTED)).isTrue();

        ChapterGenerationSessionRegistry.StopResult stopped =
                registry.requestStop("workflow-accept-stop-race");

        assertThat(stopped.found()).isTrue();
        assertThat(stopped.accepted()).isFalse();
        assertThat(stopped.status()).isNull();
        assertThat(received)
                .extracting(ChapterGenerationSessionEvent::type)
                .containsExactly(DRAFT_COMPLETED, HUMAN_REVIEW_REQUIRED, COMPRESSION_STARTED)
                .doesNotContain(GENERATION_CANCELLED);
        assertThat(registry.findActiveSession("novel-001", 3)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("COMPRESSING");
            assertThat(snapshot.currentNode()).isEqualTo("COMPRESSION");
            assertThat(snapshot.failureMessage()).isEmpty();
        });
        System.out.println("ACCEPT/STOP 竞态检查：进入 COMPRESSION 后旧 STOP 被拒绝，未发出 GENERATION_CANCELLED");
    }

    @Test
    void shouldRetainRecentTerminalSnapshotsForShortTermRecovery() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-03T06:00:00Z"));
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry(clock);
        registerDraft(registry, "workflow-completed", GENERATION_COMPLETED, null);
        assertThat(registry.findRecoverableSession("novel-001", 3))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.status()).isEqualTo("COMPLETED"));
        clock.advance(Duration.ofSeconds(1));

        registerDraft(registry, "workflow-failed", GENERATION_FAILED, "模型调用失败");
        assertThat(registry.findRecoverableSession("novel-001", 3))
                .hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.status()).isEqualTo("FAILED");
                    assertThat(snapshot.failureMessage())
                            .isEqualTo(cn.ninth.novel.types.enums.ResponseCode.UN_ERROR.getMessage());
                });
        clock.advance(Duration.ofSeconds(1));

        registerDraft(registry, "workflow-stopped", GENERATION_CANCELLED, "用户已停止章节生成流程");
        assertThat(registry.findRecoverableSession("novel-001", 3))
                .hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.workflowId()).isEqualTo("workflow-stopped");
                    assertThat(snapshot.status()).isEqualTo("CANCELLED");
                    assertThat(snapshot.accumulatedContent()).isEqualTo("已生成正文");
                    assertThat(snapshot.failureMessage()).isEqualTo("用户已停止章节生成流程");
                });

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(registry.findRecoverableSession("novel-001", 3)).isEmpty();
        System.out.println("COMPLETED、FAILED、CANCELLED 终态 Session 在五分钟内可恢复查询，过期后自动清理");
    }

    private static void registerDraft(
            ChapterGenerationSessionRegistry registry,
            String workflowId,
            ChapterGenerationEventType terminalEvent,
            String terminalMessage
    ) {
        registry.register(workflowId, "novel-001", 3);
        registry.publish(workflowId, GENERATION_STARTED);
        registry.publish(workflowId, ChapterGenerationEventType.DRAFT_CHUNK, "已生成正文");
        registry.publish(workflowId, terminalEvent, terminalMessage);
    }

    @Test
    void shouldDeliverDraftChunkContentWithItsBusinessEvent() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-chunk");
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe("workflow-chunk", received::add);

        assertThat(registry.publish(
                "workflow-chunk",
                ChapterGenerationEventType.DRAFT_CHUNK,
                "第一段正文"
        )).isTrue();

        assertThat(received).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ChapterGenerationEventType.DRAFT_CHUNK);
            assertThat(event.content()).isEqualTo("第一段正文");
        });
        System.out.println("DRAFT_CHUNK 已携带正文片段，Session 事件仍保持轻量登记表实现");
    }

    @Test
    void shouldLogOneDraftSummaryAfterStreamingCompletes() {
        Logger logger = (Logger) LoggerFactory.getLogger(ChapterGenerationSessionRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
            registry.register("workflow-draft-log", "novel-001", 2);
            registry.publish("workflow-draft-log", DRAFT_STARTED);
            registry.publish("workflow-draft-log", ChapterGenerationEventType.DRAFT_CHUNK, "第一段");
            registry.publish("workflow-draft-log", ChapterGenerationEventType.DRAFT_CHUNK, "第二段");
            registry.publish("workflow-draft-log", DRAFT_COMPLETED);

            assertThat(appender.list)
                    .hasSize(1)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getFormattedMessage())
                                .contains(
                                        "[SSE] draft completed",
                                        "workflowId=workflow-draft-log",
                                        "chapter=2",
                                        "chunks=2",
                                        "chars=6",
                                        "durationMs="
                                );
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        System.out.println("DRAFT_COMPLETED 日志契约通过：两条正文 chunk 只汇总为一条 INFO，包含 chunks/chars/durationMs");
    }

    @Test
    void shouldCancelActiveDraftAndDisposeItsSubscription() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-stop");
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe("workflow-stop", received::add);
        registry.publish("workflow-stop", DRAFT_STARTED);

        AtomicBoolean disposed = new AtomicBoolean();
        Disposable subscription = () -> disposed.set(true);
        assertThat(registry.registerDraftSubscription("workflow-stop", subscription))
                .isTrue();

        ChapterGenerationSessionRegistry.StopResult result =
                registry.requestStop("workflow-stop");

        assertThat(result.found()).isTrue();
        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(
                cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum.CANCELLED
        );
        assertThat(result.executionActive()).isTrue();
        assertThat(disposed).isTrue();
        assertThat(registry.status("workflow-stop")).contains(
                cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum.CANCELLED
        );
        assertThat(received)
                .extracting(event -> event.type().name())
                .containsExactly(
                DRAFT_STARTED.name(),
                GENERATION_CANCELLED.name()
                );
        assertThat(registry.publish(
                "workflow-stop",
                cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.PERSIST_STARTED
        )).isFalse();
        System.out.println(
                "STOP 已取消 DRAFT 订阅、标记 CANCELLED 并阻止后续 PERSIST 事件"
        );
    }

    @Test
    void shouldReleaseStreamingResourcesAfterTerminalFailureButRetainSnapshot() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-terminal-cleanup", "novel-001", 3);
        registry.publish("workflow-terminal-cleanup", DRAFT_STARTED);

        AtomicBoolean disposed = new AtomicBoolean();
        assertThat(registry.registerDraftSubscription(
                "workflow-terminal-cleanup",
                () -> disposed.set(true)
        )).isTrue();
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe("workflow-terminal-cleanup", received::add);

        assertThat(registry.publish(
                "workflow-terminal-cleanup",
                GENERATION_FAILED,
                "正文生成失败，请重新生成本章。"
        )).isTrue();
        assertThat(disposed).isTrue();
        assertThat(registry.publish(
                "workflow-terminal-cleanup",
                ChapterGenerationEventType.PERSIST_STARTED
        )).isFalse();
        assertThat(received)
                .extracting(ChapterGenerationSessionEvent::type)
                .containsExactly(DRAFT_STARTED, GENERATION_FAILED);
        assertThat(registry.findRecoverableSession("novel-001", 3))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.status()).isEqualTo("FAILED"));
        System.out.println("终态资源释放通过：失败后 Disposable/listener 已清理，FAILED 快照仍可回放");
    }

    @Test
    void shouldAcceptOnlyAfterDraftCompleted() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-accept");

        ChapterGenerationSessionRegistry.AcceptResult beforeDraft =
                registry.requestAccept("workflow-accept");
        assertThat(beforeDraft.accepted()).isFalse();
        assertThat(beforeDraft.alreadyAccepted()).isFalse();

        registry.publish("workflow-accept", DRAFT_STARTED);
        ChapterGenerationSessionRegistry.AcceptResult whileStreaming =
                registry.requestAccept("workflow-accept");
        assertThat(whileStreaming.accepted()).isFalse();

        registry.publish("workflow-accept", DRAFT_COMPLETED);
        ChapterGenerationSessionRegistry.AcceptResult accepted =
                registry.requestAccept("workflow-accept");
        ChapterGenerationSessionRegistry.AcceptResult repeated =
                registry.requestAccept("workflow-accept");

        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.alreadyAccepted()).isFalse();
        assertThat(repeated.accepted()).isFalse();
        assertThat(repeated.alreadyAccepted()).isTrue();
        assertThat(registry.isAcceptRequested("workflow-accept")).isTrue();

        registry.publish("workflow-accept", GENERATION_COMPLETED);
        ChapterGenerationSessionRegistry.AcceptResult afterCompletion =
                registry.requestAccept("workflow-accept");
        assertThat(afterCompletion.accepted()).isFalse();
        assertThat(afterCompletion.alreadyAccepted()).isTrue();
        System.out.println("ACCEPT 仅在 DRAFT_COMPLETED 后生效，重复命令保持幂等");
    }

    @Test
    void shouldKeepReviewFailureRecoverableAndAllowStop() {
        ChapterGenerationSessionRegistry registry = new ChapterGenerationSessionRegistry();
        registry.register("workflow-review-failed");
        List<ChapterGenerationSessionEvent> received = new ArrayList<>();
        registry.subscribe("workflow-review-failed", received::add);
        registry.publish("workflow-review-failed", DRAFT_COMPLETED);
        registry.publish(
                "workflow-review-failed",
                ChapterGenerationEventType.REVIEW_FAILED,
                "章节审稿模型响应无效：evidence 不在当前正文中"
        );

        assertThat(registry.status("workflow-review-failed"))
                .contains(cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum.REVIEW_FAILED);
        assertThat(received).last().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ChapterGenerationEventType.REVIEW_FAILED);
            assertThat(event.content())
                    .isEqualTo(ChapterGenerationSessionEvent.REVIEW_FAILURE_MESSAGE)
                    .doesNotContain("evidence", "JSON", "exception");
        });
        assertThat(registry.subscribe("workflow-review-failed", event -> { }).terminal())
                .isFalse();
        ChapterGenerationSessionRegistry.StopResult result =
                registry.requestStop("workflow-review-failed");

        assertThat(result.accepted()).isTrue();
        assertThat(result.executionActive()).isFalse();
        assertThat(result.status())
                .isEqualTo(cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum.CANCELLED);
        System.out.println("REVIEW_FAILED Session 保持非终态，并允许 STOP 转为 CANCELLED");
    }

    @Test
    void shouldNormalizeEveryGenerationFailureToApprovedUserMessage() {
        assertThat(ChapterGenerationSessionEvent.safeFailureMessageForStage(
                ChapterGenerationEventType.DRAFT_STARTED
        )).isEqualTo(ChapterGenerationSessionEvent.DRAFT_FAILURE_MESSAGE);
        assertThat(ChapterGenerationSessionEvent.safeFailureMessageForStage(
                ChapterGenerationEventType.REVIEW_STARTED
        )).isEqualTo(ChapterGenerationSessionEvent.REVIEW_FAILURE_MESSAGE);
        assertThat(ChapterGenerationSessionEvent.safeFailureMessageForStage(
                ChapterGenerationEventType.COMPRESSION_STARTED
        )).isEqualTo(ChapterGenerationSessionEvent.COMPRESSION_FAILURE_MESSAGE);
        assertThat(ChapterGenerationSessionEvent.safeFailureMessageForStage(
                ChapterGenerationEventType.PERSIST_STARTED
        )).isEqualTo(ChapterGenerationSessionEvent.PERSIST_FAILURE_MESSAGE);
        assertThat(new ChapterGenerationSessionEvent(
                ChapterGenerationEventType.REVIEW_FAILED,
                "MismatchedInputException: JSON parse failed"
        ).content()).isEqualTo(ChapterGenerationSessionEvent.REVIEW_FAILURE_MESSAGE);
        assertThat(new ChapterGenerationSessionEvent(
                GENERATION_FAILED,
                ChapterGenerationSessionEvent.DRAFT_FAILURE_MESSAGE
        ).content()).isEqualTo(ChapterGenerationSessionEvent.DRAFT_FAILURE_MESSAGE);
        assertThat(new ChapterGenerationSessionEvent(
                GENERATION_FAILED,
                ChapterGenerationSessionEvent.REVISION_FAILURE_MESSAGE
        ).content()).isEqualTo(ChapterGenerationSessionEvent.REVISION_FAILURE_MESSAGE);
        assertThat(new ChapterGenerationSessionEvent(
                GENERATION_FAILED,
                ChapterGenerationSessionEvent.COMPRESSION_FAILURE_MESSAGE
        ).content()).isEqualTo(ChapterGenerationSessionEvent.COMPRESSION_FAILURE_MESSAGE);
        assertThat(new ChapterGenerationSessionEvent(
                GENERATION_FAILED,
                ChapterGenerationSessionEvent.PERSIST_FAILURE_MESSAGE
        ).content()).isEqualTo(ChapterGenerationSessionEvent.PERSIST_FAILURE_MESSAGE);
        assertThat(new ChapterGenerationSessionEvent(
                GENERATION_FAILED,
                "Flux error: structured response is blank"
        ).content()).isEqualTo(
                cn.ninth.novel.types.enums.ResponseCode.UN_ERROR.getMessage()
        );

        System.out.println("SSE 失败事件文案契约通过：审稿、正文生成、整理、保存和未知异常均不泄漏技术详情");
    }

    @Test
    void shouldRejectFailureEventWithoutReadableContent() {
        assertThatThrownBy(() -> new ChapterGenerationSessionEvent(GENERATION_FAILED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须携带可读 content");
        System.out.println("失败事件内容契约通过：GENERATION_FAILED 不允许空 content");
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
