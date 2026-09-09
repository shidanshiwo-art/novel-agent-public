package cn.ninth.novel.domain.chapter.service.session;

import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.DRAFT_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.DRAFT_CHUNK;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.DRAFT_STARTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_COMPLETED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_CANCELLED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_ABORTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.GENERATION_FAILED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.HUMAN_REVIEW_REQUIRED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.COMPRESSION_STARTED;
import static cn.ninth.novel.domain.chapter.service.session.ChapterGenerationEventType.REVIEW_FAILED;

/**
 * 章节生成会话的轻量内存事件登记表。
 *
 * <p>只保存当前进程内的事件历史和 SSE 监听器，不引入独立 Event Bus。</p>
 */
@Component
@Slf4j
public class ChapterGenerationSessionRegistry {

    private static final Duration TERMINAL_SESSION_RETENTION = Duration.ofMinutes(5);
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public ChapterGenerationSessionRegistry() {
        this(Clock.systemUTC());
    }

    ChapterGenerationSessionRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void register(String workflowId) {
        register(workflowId, null, 0);
    }

    public void register(String workflowId, String projectCode, int chapterNumber) {
        evictExpiredTerminalSessions(clock.instant());
        sessions.put(workflowId, new Session(projectCode, chapterNumber, clock));
    }

    /**
     * 查询指定项目和章节的活动会话。
     *
     * <p>终态会话仍保留在登记表中供 SSE 回放，但不会被恢复查询返回。</p>
     */
    public Optional<String> findActiveWorkflowId(String projectCode, int chapterNumber) {
        return findActiveSession(projectCode, chapterNumber)
                .map(ChapterGenerationSessionSnapshot::workflowId);
    }

    public Optional<ChapterGenerationSessionSnapshot> findActiveSession(
            String projectCode,
            int chapterNumber
    ) {
        if (projectCode == null || projectCode.isBlank()) {
            return Optional.empty();
        }
        evictExpiredTerminalSessions(clock.instant());
        return sessions.entrySet().stream()
                .filter(entry -> entry.getValue().matches(projectCode, chapterNumber))
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .findFirst();
    }

    /**
     * 查询指定项目和章节可供页面恢复的会话。
     *
     * <p>活动会话优先；没有活动会话时，返回五分钟内最近结束的会话快照，
     * 用于页面刷新后短时间内展示 COMPLETED、FAILED 或 CANCELLED 结果。</p>
     */
    public Optional<ChapterGenerationSessionSnapshot> findRecoverableSession(
            String projectCode,
            int chapterNumber
    ) {
        if (projectCode == null || projectCode.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        evictExpiredTerminalSessions(now);

        Optional<Map.Entry<String, Session>> activeSession = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().matches(projectCode, chapterNumber))
                .findFirst();
        if (activeSession.isPresent()) {
            Map.Entry<String, Session> entry = activeSession.get();
            return Optional.of(entry.getValue().snapshot(entry.getKey()));
        }

        return sessions.entrySet().stream()
                .filter(entry -> entry.getValue().matchesRetainedTerminal(projectCode, chapterNumber, now))
                .max(Comparator.comparing((Map.Entry<String, Session> entry) -> entry.getValue().terminalAt()))
                .map(entry -> entry.getValue().snapshot(entry.getKey()));
    }

    /**
     * 将工作流结果中的当前正文、阶段和 Review 问题补充到会话快照。
     */
    public void updateSnapshot(
            String workflowId,
            String accumulatedContent,
            List<String> completedStages,
            List<String> reviewIssues
    ) {
        Session session = sessions.get(workflowId);
        if (session != null) {
            session.updateSnapshot(accumulatedContent, completedStages, reviewIssues);
        }
    }

    public boolean publish(String workflowId, ChapterGenerationEventType eventType) {
        return publish(workflowId, eventType, null);
    }

    public boolean publish(
            String workflowId,
            ChapterGenerationEventType eventType,
            String content
    ) {
        return publish(workflowId, eventType, content, null);
    }

    public boolean publish(
            String workflowId,
            ChapterGenerationEventType eventType,
            String content,
            List<String> reviewIssues
    ) {
        Session session = sessions.get(workflowId);
        return session != null && session.publish(
                workflowId,
                new ChapterGenerationSessionEvent(eventType, content, reviewIssues)
        );
    }

    public StopResult requestStop(String workflowId) {
        Session session = sessions.get(workflowId);
        if (session == null) {
            return new StopResult(false, false, null, false);
        }
        return session.requestStop();
    }

    public AcceptResult requestAccept(String workflowId) {
        Session session = sessions.get(workflowId);
        if (session == null) {
            return new AcceptResult(false, false, false, null);
        }
        return session.requestAccept();
    }

    public boolean isCancellationRequested(String workflowId) {
        Session session = sessions.get(workflowId);
        return session != null && session.isCancellationRequested();
    }

    public boolean isAcceptRequested(String workflowId) {
        Session session = sessions.get(workflowId);
        return session != null && session.isAcceptRequested();
    }

    public boolean registerDraftSubscription(
            String workflowId,
            Disposable subscription
    ) {
        Session session = sessions.get(workflowId);
        return session != null && session.registerDraftSubscription(subscription);
    }

    public void clearDraftSubscription(String workflowId) {
        Session session = sessions.get(workflowId);
        if (session != null) {
            session.clearDraftSubscription();
        }
    }

    public Optional<ChapterWorkflowStatusEnum> status(String workflowId) {
        Session session = sessions.get(workflowId);
        return session == null ? Optional.empty() : session.status();
    }

    public SubscribeResult subscribe(
            String workflowId,
            Consumer<ChapterGenerationSessionEvent> listener
    ) {
        evictExpiredTerminalSessions(clock.instant());
        Session session = sessions.get(workflowId);
        if (session == null) {
            return new SubscribeResult(false, false);
        }
        return new SubscribeResult(true, session.subscribe(listener));
    }

    public void unsubscribe(
            String workflowId,
            Consumer<ChapterGenerationSessionEvent> listener
    ) {
        Session session = sessions.get(workflowId);
        if (session != null) {
            session.unsubscribe(listener);
        }
    }

    public record SubscribeResult(boolean found, boolean terminal) {
    }

    public record StopResult(
            boolean found,
            boolean accepted,
            ChapterWorkflowStatusEnum status,
            boolean executionActive
    ) {
    }

    public record AcceptResult(
            boolean found,
            boolean accepted,
            boolean alreadyAccepted,
            ChapterWorkflowStatusEnum status
    ) {
    }

    private static final class Session {
        private final String projectCode;
        private final int chapterNumber;
        private final Clock clock;
        private final List<ChapterGenerationSessionEvent> events = new ArrayList<>();
        private final List<Consumer<ChapterGenerationSessionEvent>> listeners = new ArrayList<>();
        private boolean terminal;
        private boolean draftStreaming;
        private boolean draftCompleted;
        private boolean cancellationRequested;
        private boolean acceptRequested;
        private boolean compressionStarted;
        private Disposable draftSubscription;
        private String workflowStatus = "DRAFTING";
        private String currentNode = "";
        private ChapterWorkflowStatusEnum status;
        private final StringBuilder accumulatedContent = new StringBuilder();
        private final List<String> completedStages = new ArrayList<>();
        private final List<String> reviewIssues = new ArrayList<>();
        private String failureMessage = "";
        private Instant terminalAt;
        private long draftChunkCount;
        private long draftContentChars;
        private long draftStartedAtNanos;

        private Session(String projectCode, int chapterNumber, Clock clock) {
            this.projectCode = projectCode;
            this.chapterNumber = chapterNumber;
            this.clock = clock;
        }

        private synchronized boolean matches(String projectCode, int chapterNumber) {
            return !terminal
                    && matchesProjectChapter(projectCode, chapterNumber);
        }

        private synchronized boolean matchesRetainedTerminal(
                String projectCode,
                int chapterNumber,
                Instant now
        ) {
            return terminal
                    && matchesProjectChapter(projectCode, chapterNumber)
                    && terminalAt != null
                    && terminalAt.plus(TERMINAL_SESSION_RETENTION).isAfter(now);
        }

        private boolean matchesProjectChapter(String projectCode, int chapterNumber) {
            return Objects.equals(this.projectCode, projectCode)
                    && this.chapterNumber == chapterNumber;
        }

        private synchronized boolean isExpired(Instant now) {
            return terminalAt != null
                    && !terminalAt.plus(TERMINAL_SESSION_RETENTION).isAfter(now);
        }

        private synchronized Instant terminalAt() {
            return terminalAt;
        }

        private synchronized ChapterGenerationSessionSnapshot snapshot(String workflowId) {
            return new ChapterGenerationSessionSnapshot(
                    workflowId,
                    chapterNumber,
                    workflowStatus,
                    currentNode,
                    accumulatedContent.toString(),
                    completedStages,
                    reviewIssues,
                    failureMessage
            );
        }

        private synchronized void updateSnapshot(
                String currentContent,
                List<String> stages,
                List<String> issues
        ) {
            if (currentContent != null) {
                accumulatedContent.setLength(0);
                accumulatedContent.append(currentContent);
            }
            if (stages != null) {
                for (String stage : stages) {
                    addCompletedStage(stage);
                }
            }
            if (issues != null) {
                reviewIssues.clear();
                reviewIssues.addAll(issues);
            }
        }

        private void addCompletedStage(String stage) {
            if (stage != null && !stage.isBlank() && !completedStages.contains(stage)) {
                completedStages.add(stage);
            }
        }

        private synchronized boolean publish(ChapterGenerationSessionEvent event) {
            return publish(null, event);
        }

        private synchronized boolean publish(
                String workflowId,
                ChapterGenerationSessionEvent event
        ) {
            if (terminal) {
                return false;
            }
            if (event.type() == COMPRESSION_STARTED && compressionStarted) {
                return false;
            }
            events.add(event);
            switch (event.type()) {
                case GENERATION_STARTED, DRAFT_STARTED, DRAFT_CHUNK, DRAFT_COMPLETED -> {
                    workflowStatus = "DRAFTING";
                    if (event.type() == DRAFT_STARTED) {
                        currentNode = "DRAFT";
                        status = null;
                        draftStreaming = true;
                        draftChunkCount = 0;
                        draftContentChars = 0;
                        draftStartedAtNanos = System.nanoTime();
                    } else if (event.type() == DRAFT_CHUNK && event.content() != null) {
                        accumulatedContent.append(event.content());
                        draftChunkCount++;
                        draftContentChars += event.content().length();
                    } else if (event.type() == DRAFT_COMPLETED) {
                        draftStreaming = false;
                        draftCompleted = true;
                        draftSubscription = null;
                        addCompletedStage("DRAFT");
                        log.info(
                                "[SSE] draft completed workflowId={} chapter={} chunks={} chars={} durationMs={}",
                                workflowId,
                                chapterNumber,
                                draftChunkCount,
                                draftContentChars,
                                draftDurationMillis()
                        );
                    }
                }
                case REVIEW_STARTED -> {
                    workflowStatus = "REVIEWING";
                    currentNode = "REVIEW";
                    status = null;
                    failureMessage = "";
                }
                case REVIEW_COMPLETED -> {
                    workflowStatus = "REVIEWING";
                    currentNode = "REVIEW";
                    if (event.reviewIssues() != null) {
                        reviewIssues.clear();
                        reviewIssues.addAll(event.reviewIssues());
                    }
                    addCompletedStage("REVIEW");
                }
                case REVISION_STARTED -> {
                    workflowStatus = "REVISING";
                    currentNode = "REVISE";
                    status = null;
                    failureMessage = "";
                }
                case REVISION_COMPLETED -> {
                    workflowStatus = "REVISING";
                    currentNode = "REVISE";
                    addCompletedStage("REVISE");
                }
                case COMPRESSION_STARTED -> {
                    compressionStarted = true;
                    workflowStatus = "COMPRESSING";
                    currentNode = "COMPRESSION";
                    status = null;
                    failureMessage = "";
                }
                case COMPRESSION_COMPLETED -> {
                    workflowStatus = "COMPRESSING";
                    currentNode = "COMPRESSION";
                    addCompletedStage("COMPRESSION");
                }
                case PERSIST_STARTED -> {
                    workflowStatus = "PERSISTING";
                    currentNode = "PERSIST";
                    status = null;
                    failureMessage = "";
                }
                case PERSIST_COMPLETED -> {
                    workflowStatus = "PERSISTING";
                    currentNode = "PERSIST";
                    addCompletedStage("PERSIST");
                }
                case HUMAN_REVIEW_REQUIRED -> {
                    workflowStatus = "WAITING_HUMAN";
                    currentNode = "HUMAN";
                    status = ChapterWorkflowStatusEnum.WAITING_HUMAN;
                }
                case REVIEW_FAILED -> {
                    workflowStatus = "REVIEW_FAILED";
                    currentNode = "REVIEW";
                    status = ChapterWorkflowStatusEnum.REVIEW_FAILED;
                    addCompletedStage("REVIEW");
                    failureMessage = event.content();
                }
                case GENERATION_COMPLETED -> {
                    terminal = true;
                    draftStreaming = false;
                    workflowStatus = "COMPLETED";
                    currentNode = "END";
                    status = ChapterWorkflowStatusEnum.COMPLETED;
                    terminalAt = clock.instant();
                }
                case GENERATION_ABORTED -> {
                    terminal = true;
                    draftStreaming = false;
                    workflowStatus = "ABORTED";
                    status = ChapterWorkflowStatusEnum.ABORTED;
                    failureMessage = event.content();
                    terminalAt = clock.instant();
                }
                case GENERATION_CANCELLED -> {
                    terminal = true;
                    draftStreaming = false;
                    workflowStatus = "CANCELLED";
                    status = ChapterWorkflowStatusEnum.CANCELLED;
                    failureMessage = event.content();
                    terminalAt = clock.instant();
                }
                case GENERATION_FAILED -> {
                    terminal = true;
                    draftStreaming = false;
                    workflowStatus = "FAILED";
                    failureMessage = event.content();
                    terminalAt = clock.instant();
                }
            }
            for (Consumer<ChapterGenerationSessionEvent> listener : List.copyOf(listeners)) {
                try {
                    listener.accept(event);
                } catch (RuntimeException ignored) {
                    // 单个 SSE 连接异常不应影响章节工作流或其他订阅者。
                }
            }
            if (terminal) {
                // 终态快照继续保留用于刷新回放，但监听器和流式订阅已经没有继续存在的意义。
                // 这里不移除 Session，避免刷新后丢失 FAILED/COMPLETED/CANCELLED 结果。
                listeners.clear();
                Disposable activeSubscription = draftSubscription;
                draftSubscription = null;
                if (activeSubscription != null) {
                    activeSubscription.dispose();
                }
            }
            return true;
        }

        private long draftDurationMillis() {
            if (draftStartedAtNanos == 0) {
                return 0;
            }
            return (System.nanoTime() - draftStartedAtNanos) / 1_000_000;
        }

        private synchronized StopResult requestStop() {
            if (terminal || acceptRequested || (!draftStreaming
                    && status != ChapterWorkflowStatusEnum.REVIEW_FAILED)) {
                return new StopResult(true, false, status, false);
            }

            boolean executionActive = draftStreaming;
            cancellationRequested = true;
            draftStreaming = false;
            Disposable activeSubscription = draftSubscription;
            draftSubscription = null;
            if (activeSubscription != null) {
                activeSubscription.dispose();
            }
            publish(new ChapterGenerationSessionEvent(
                    GENERATION_CANCELLED,
                    "用户已停止章节生成流程"
            ));
            return new StopResult(true, true, ChapterWorkflowStatusEnum.CANCELLED, executionActive);
        }

        private synchronized AcceptResult requestAccept() {
            if (acceptRequested) {
                return new AcceptResult(true, false, true, status);
            }
            if (terminal) {
                return new AcceptResult(true, false, false, status);
            }
            if (!draftCompleted) {
                return new AcceptResult(true, false, false, status);
            }
            acceptRequested = true;
            return new AcceptResult(true, true, false, status);
        }

        private synchronized boolean isCancellationRequested() {
            return cancellationRequested;
        }

        private synchronized boolean isAcceptRequested() {
            return acceptRequested;
        }

        private synchronized boolean registerDraftSubscription(Disposable subscription) {
            if (terminal || !draftStreaming || cancellationRequested) {
                subscription.dispose();
                return false;
            }
            draftSubscription = subscription;
            return true;
        }

        private synchronized void clearDraftSubscription() {
            draftSubscription = null;
        }

        private synchronized Optional<ChapterWorkflowStatusEnum> status() {
            return Optional.ofNullable(status);
        }

        private synchronized boolean subscribe(Consumer<ChapterGenerationSessionEvent> listener) {
            if (!terminal) {
                // 先登记再回放。回放期间若 SSE 已断开，unsubscribe 才能移除本次监听器。
                listeners.add(listener);
            }
            for (ChapterGenerationSessionEvent event : events) {
                try {
                    listener.accept(event);
                } catch (Throwable ignored) {
                    // 连接可能在回放历史事件期间关闭。
                }
            }
            return terminal;
        }

        private synchronized void unsubscribe(Consumer<ChapterGenerationSessionEvent> listener) {
            listeners.remove(listener);
        }
    }

    private void evictExpiredTerminalSessions(Instant now) {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
