package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterGenerationSessionApiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseGenerationSessionAsTheSingleChapterFrontendEntry() throws IOException {
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/chapter.ts"));
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        System.out.printf("章节 Session 前端入口契约检查：api=%d, view=%d%n", api.length(), view.length());

        assertThat(api)
                .contains(
                        "GenerationSessionResponse",
                        "export const createGenerationSession = (projectCode: string, chapterNumber: number)",
                        "`/v1/novels/projects/${projectCode}/chapters/${chapterNumber}/generation-sessions`"
                )
                .contains(
                        "export const getActiveGenerationSession = (projectCode: string, chapterNumber: number)",
                        "http.get<any, GenerationSessionSnapshotResponse | null>("
                )
                .contains(
                        "export const stopGenerationSession = (workflowId: string)",
                        "`/v1/novels/generation-sessions/${workflowId}/commands`",
                        "{ command: 'STOP' }"
                )
                .contains(
                        "export const acceptGenerationSession = (workflowId: string)",
                        "{ command: 'ACCEPT' }"
                )
                .doesNotContain("export const generateChapter", "GenerateChapterRequest");
        assertThat(view)
                .contains(
                        "createGenerationSession(",
                        "generationSession.value = await createGenerationSession(",
                        "getActiveGenerationSession(",
                        "accumulatedContent",
                        "currentNode",
                        "completedStages",
                        "reviewIssues",
                        "failureMessage",
                        "async function loadActiveGenerationSession()",
                        "void loadActiveGenerationSession()",
                        "connectGenerationEventStream(activeSession.workflowId)"
                )
                .doesNotContain("generateChapter({");

        System.out.println("章节 Session 前端入口契约通过：单章生成只创建会话并展示 workflowId");
    }

    @Test
    void shouldSeparateAutomaticAcceptControlsFromHumanReviewDecisions() throws IOException {
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/chapter.ts"));
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        System.out.printf("Review 人工控制语义检查：api=%d, view=%d%n", api.length(), view.length());

        assertThat(api)
                .contains(
                        "export const acceptGenerationSession = (workflowId: string)",
                        "{ command: 'ACCEPT' }"
                );
        assertThat(view)
                .contains(
                        "await acceptGenerationSession(workflowId)",
                        ":disabled=\"resuming || acceptingRequested\"",
                        ":loading=\"resuming || acceptingRequested\"",
                        "采用当前正文",
                        "停止流程",
                         "REVIEW_FAILED",
                         "@click=\"resume('REVIEW')\"",
                         "重新审稿",
                         "自动审稿失败，但正文已保留。",
                         "workflowFailureMessage",
                         "event.content?.trim()",
                         "工作流消息",
                         "workflowStatusByEvent",
                         "acceptingRequested.value = true",
                         "statusLabel(workflowStatus)",
                         "HUMAN_REVIEW_REQUIRED: 'WAITING_HUMAN'",
                         "人工修改意见",
                        "@click=\"resume('PASS')\"",
                        "@click=\"resume('REVISE')\"",
                        "@click=\"resume('ABORT')\"",
                        "const workflowId = result.value?.workflowId ?? generationSession.value?.workflowId",
                         "await resumeChapter(workflowId"
                 )
                 .doesNotContain(
                         "@click=\"resume('ACCEPT')\"",
                         "humanReviewRequired",
                         "reviewFailed",
                         "workflowStatus.value = 'COMPRESSING'",
                         "workflowStageCode.value = 'COMPRESSION'",
                         "markWorkflowStageCompleted('REVIEW')"
                 );

        int reviewFailedStart = view.indexOf("<section v-else-if=\"workflowStatus === 'REVIEW_FAILED'\"");
        assertThat(view)
                .doesNotContain(
                        "<section v-else-if=\"workflowStatus === 'REVIEWING'\"",
                        "class=\"workflow-processing\"",
                        "statusDescription(workflowStatus)",
                        "class=\"review-controls-copy\"",
                        "class=\"review-copy\""
                );
        assertThat(reviewFailedStart).isGreaterThanOrEqualTo(0);

        int waitingHumanStart = view.indexOf("<section v-else-if=\"workflowStatus === 'WAITING_HUMAN'\"");
        String reviewFailedControls = reviewFailedStart >= 0 && waitingHumanStart >= 0
                ? view.substring(reviewFailedStart, waitingHumanStart)
                : "";
        assertThat(reviewFailedControls)
                .contains("重新审稿", "采用当前正文", "停止流程", "@click=\"resume('PASS')\"")
                .doesNotContain("直接采用当前正文");
        assertThat(waitingHumanStart).isGreaterThan(reviewFailedStart);

        int completedStart = view.indexOf(
                "<section v-else-if=\"workflowStatus === 'COMPLETED'\"",
                waitingHumanStart
        );
        assertThat(completedStart).isGreaterThan(waitingHumanStart);

        System.out.println("Review 控制语义检查通过：采用当前正文仅出现在 REVIEW_FAILED 和 WAITING_HUMAN");
    }

    @Test
    void shouldKeepAcceptSkippingReviewAndReviseBeforeCompression() throws IOException {
        String service = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-domain/src/main/java/cn/ninth/novel/domain/chapter/service/ChapterService.java"
        ));
        int reviewRouteStart = service.indexOf("private Command routeAfterReview(");
        int reviseRouteStart = service.indexOf("private Command routeAfterRevise(");
        int draftNodeStart = service.indexOf("private AsyncNodeActionWithConfig<ChapterGraphState> draftNodeWithEvents(");
        String reviewRoute = reviewRouteStart >= 0 && reviseRouteStart >= 0
                ? service.substring(reviewRouteStart, reviseRouteStart)
                : "";
        String reviseRoute = reviseRouteStart >= 0 && draftNodeStart >= 0
                ? service.substring(reviseRouteStart, draftNodeStart)
                : "";

        System.out.printf(
                "ACCEPT 跳过 Review/Revise 路由检查：service=%d, review=%d, revise=%d%n",
                service.length(),
                reviewRoute.length(),
                reviseRoute.length()
        );

        assertThat(reviewRoute)
                .contains("if (isAcceptRequested(config.threadId().orElse(null)))", "return new Command(COMPRESSION);");
        assertThat(reviseRoute)
                .contains("isAcceptRequested(config.threadId().orElse(null))", "COMPRESSION");
        assertThat(service).contains(".addEdge(\"COMPRESSION\", \"PERSIST\")");

        System.out.println("ACCEPT 路由语义通过：跳过剩余 Review/Revise 后进入 COMPRESSION，再沿既有边进入 PERSIST");
    }

    @Test
    void shouldKeepWaitingHumanControlsToReviewDecisionsOnly() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));
        int waitingHumanStart = view.indexOf("<section v-else-if=\"workflowStatus === 'WAITING_HUMAN'\"");
        int completedStart = view.indexOf(
                "<section v-else-if=\"workflowStatus === 'COMPLETED'\"",
                waitingHumanStart
        );
        String waitingHumanControls = waitingHumanStart >= 0 && completedStart >= 0
                ? view.substring(waitingHumanStart, completedStart)
                : "";

        System.out.printf(
                "WAITING_HUMAN Review 决策检查：view=%d, controls=%d%n",
                view.length(),
                waitingHumanControls.length()
        );

        assertThat(waitingHumanControls)
                .contains(
                        "人工修改意见",
                        "采用当前正文",
                        "驳回重写",
                        "放弃",
                        "@click=\"resume('PASS')\"",
                        "@click=\"resume('REVISE')\"",
                        "@click=\"resume('ABORT')\""
                )
                .doesNotContain(
                        "停止流程",
                        "接受当前正文",
                        "跳过审稿并采用",
                        "class=\"review-copy\"",
                        "请选择通过、驳回重写或放弃本章。",
                        "@click=\"acceptCurrentDraft\"",
                        "@click=\"stopCurrentGeneration\""
                );
        assertThat(waitingHumanStart).isGreaterThanOrEqualTo(0);
        System.out.println("WAITING_HUMAN 控制语义通过：移除等待状态文案，仅保留修改输入和通过、驳回重写、放弃三个 Review 决策");
    }

    @Test
    void shouldRestoreActiveSessionSnapshotBeforeReconnectingSse() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        int restoreStart = view.indexOf("function restoreGenerationSessionSnapshot(");
        int streamingRestore = view.indexOf("streamingContent.value = activeSession.accumulatedContent", restoreStart);
        int statusRestore = view.indexOf("workflowStatus.value = restoredStatus", streamingRestore);
        int timelineRestore = view.indexOf("completedWorkflowStages.value = activeSession.completedStages", statusRestore);
        int reviewRestore = view.indexOf("reviewIssues: activeSession.reviewIssues", timelineRestore);
        int restoreCall = view.indexOf("restoreGenerationSessionSnapshot(activeSession, projectCode)");
        int sseReconnect = view.indexOf("connectGenerationEventStream(activeSession.workflowId)");

        System.out.printf(
                "活动 Session 恢复顺序检查：restore=%d, streaming=%d, status=%d, timeline=%d, review=%d, call=%d, sse=%d%n",
                restoreStart,
                streamingRestore,
                statusRestore,
                timelineRestore,
                reviewRestore,
                restoreCall,
                sseReconnect
        );

        assertThat(restoreStart).isGreaterThanOrEqualTo(0);
        assertThat(streamingRestore).isGreaterThan(restoreStart);
        assertThat(statusRestore).isGreaterThan(streamingRestore);
        assertThat(timelineRestore).isGreaterThan(statusRestore);
        assertThat(reviewRestore).isGreaterThan(timelineRestore);
        assertThat(restoreCall).isGreaterThanOrEqualTo(0);
        assertThat(sseReconnect).isGreaterThan(restoreCall);

        System.out.println("活动 Session 恢复顺序契约通过：streamingContent、workflowStatus、timeline、reviewIssues 恢复后才重新连接 SSE");
    }

    @Test
    void shouldRefreshSessionSnapshotWhenSseConnectionFails() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        int errorHandler = view.indexOf("source.onerror = () => {");
        int refreshFunction = view.indexOf("async function refreshActiveGenerationSessionAndReconnect()");
        int refreshQuery = view.indexOf("getActiveGenerationSession(projectCode, chapterNumber)", refreshFunction);
        int refreshRestore = view.indexOf("restoreGenerationSessionSnapshot(activeSession, projectCode)", refreshFunction);
        int refreshSse = view.indexOf("connectGenerationEventStream(activeSession.workflowId)", refreshFunction);

        System.out.printf(
                "SSE 断线快照补齐检查：errorHandler=%d, refreshFunction=%d, query=%d, restore=%d, reconnect=%d%n",
                errorHandler,
                refreshFunction,
                refreshQuery,
                refreshRestore,
                refreshSse
        );

        assertThat(view)
                .contains(
                        "void refreshActiveGenerationSessionAndReconnect()",
                        "generationSessionReconnectTimer",
                        "GENERATION_SESSION_RECONNECT_DELAY_MS",
                        "setTimeout(() =>",
                        "generationConnectionVersion"
                );
        assertThat(errorHandler).isGreaterThanOrEqualTo(0);
        assertThat(refreshFunction).isGreaterThan(errorHandler);
        assertThat(refreshQuery).isGreaterThan(refreshFunction);
        assertThat(refreshRestore).isGreaterThan(refreshQuery);
        assertThat(refreshSse).isGreaterThan(refreshRestore);

        System.out.println("SSE 断线快照补齐契约通过：重新查询服务端 Session 状态后再恢复页面并订阅 SSE");
    }

    @Test
    void shouldUseServerWorkflowStatusForPageStateAndAvoidDuplicateGenerationOnRefresh() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        int mountedStart = view.indexOf("onMounted(() => {");
        int mountedEnd = view.indexOf("})", mountedStart);
        int singleGenerationStart = view.indexOf("async function generateSingleChapter()");
        int stopStart = view.indexOf("async function stopCurrentGeneration()");
        int resumeStart = view.indexOf("async function resume(");
        int resumeEnd = view.indexOf("\nfunction goReadChapter", resumeStart);
        int acceptStart = view.indexOf("if (decision === 'PASS')", resumeStart);

        String mountedLifecycle = mountedStart >= 0 && mountedEnd >= 0
                ? view.substring(mountedStart, mountedEnd)
                : "";
        String singleGeneration = singleGenerationStart >= 0 && stopStart >= 0
                ? view.substring(singleGenerationStart, stopStart)
                : "";
        String acceptAction = acceptStart >= 0 && resumeEnd >= 0
                ? view.substring(acceptStart, resumeEnd)
                : "";
        String stopAction = stopStart >= 0 && resumeStart >= 0
                ? view.substring(stopStart, resumeStart)
                : "";
        String resumeAction = resumeStart >= 0 && resumeEnd >= 0
                ? view.substring(resumeStart, resumeEnd)
                : "";

        System.out.printf(
                "服务端状态源检查：view=%d, mounted=%d, single=%d, accept=%d, stop=%d, resume=%d%n",
                view.length(),
                mountedLifecycle.length(),
                singleGeneration.length(),
                acceptAction.length(),
                stopAction.length(),
                resumeAction.length()
        );

        assertThat(view)
                .contains(
                        "const workflowStateReady = ref(false)",
                        "const contextualActionBarVisible = computed(() => workflowStateReady.value",
                        "workflowStatus.value = restoredStatus",
                        "workflowStatus.value = workflowStatusFromApi(result.value.status, workflowStatus.value)",
                        "await loadActiveGenerationSession()",
                        "await refreshActiveGenerationSessionAndReconnect()"
                )
                .doesNotContain(
                        "generateChapterRange",
                        "generateBatch",
                        "batchResult",
                        "workflowStatus.value = 'DRAFTING'",
                        "workflowStatus.value = 'REVIEWING'",
                        "workflowStatus.value = 'COMPRESSING'",
                        "workflowStatus.value = 'CANCELLED'",
                        "generationEventSource && workflowStatus"
                );
        assertThat(mountedLifecycle)
                .contains("void initializeGenerationPage()")
                .doesNotContain("createGenerationSession(", "workflowStatus.value =");
        assertThat(singleGeneration)
                .contains("createGenerationSession(", "await loadActiveGenerationSession()")
                .doesNotContain("connectGenerationEventStream", "workflowStatus.value =");
        assertThat(acceptAction)
                .contains("await acceptGenerationSession(workflowId)", "acceptingRequested.value = true")
                .doesNotContain("workflowStatus.value = 'COMPRESSING'", "workflowStageCode.value = 'COMPRESSION'");
        assertThat(stopAction)
                .contains("await stopGenerationSession(session.workflowId)", "await refreshActiveGenerationSessionAndReconnect()")
                .doesNotContain("workflowStatus.value =", "result.value = {");
        assertThat(resumeAction)
                .contains("result.value = await resumeChapter(workflowId")
                .doesNotContain("workflowStatus.value = 'REVIEWING'");

        System.out.println("服务端状态源契约通过：刷新只查询/恢复 Session，不创建新生成；用户命令成功后不由前端硬编码 workflowStatus");
    }

    @Test
    void shouldSubscribeToDraftChunksAndPauseAutoScrollWhenReaderMovesUp() throws IOException {
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/chapter.ts"));
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        System.out.printf("章节正文流式显示契约检查：api=%d, types=%d, view=%d%n", api.length(), types.length(), view.length());

        assertThat(types)
                .contains(
                        "export type GenerationSessionEventType =",
                        "| 'DRAFT_CHUNK'",
                        "export interface GenerationSessionEvent",
                        "content: string | null",
                        "reviewIssues?: string[] | null"
                );
        assertThat(api)
                .contains(
                        "export const subscribeGenerationSessionEvents = (",
                        "new EventSource(",
                        "DRAFT_CHUNK",
                        "source.addEventListener(eventType, handleEvent)",
                        "JSON.parse(message.data)",
                        "source.onopen",
                        "import.meta.env.DEV",
                        "SSE connected",
                        "reviewIssues: Array.isArray(payload.reviewIssues) ? payload.reviewIssues : null"
                )
                .doesNotContain(
                        "console.debug('SSE event'",
                        "chunkLength",
                        "accumulatedLength"
                );
        assertThat(view)
                .contains(
                        "onBeforeUnmount",
                        "subscribeGenerationSessionEvents",
                        "const streamingContent = ref('')",
                        "const receivedBuffer = ref('')",
                        "const STREAM_DISPLAY_INTERVAL_MS = 30",
                        "const STREAM_DISPLAY_CHARS_PER_TICK = 4",
                        "const STREAM_BOTTOM_THRESHOLD_PX = 24",
                        "streamingDisplayTimer",
                        "const streamingContentScrollable = ref(false)",
                        "ref=\"generationCanvas\"",
                        "@scroll=\"handleStreamingScroll\"",
                        "receivedBuffer.value += content",
                        "const nextChunk = receivedBuffer.value.slice(0, STREAM_DISPLAY_CHARS_PER_TICK)",
                        "streamingContent.value += nextChunk",
                        "streamingDisplayTimer = setInterval(drainReceivedBuffer, STREAM_DISPLAY_INTERVAL_MS)",
                        "clearInterval(streamingDisplayTimer)",
                        "enqueueReceivedContent(event.content)",
                        "reviewIssuesPanelVisible",
                        "const hasReviewIssues = computed(() => Boolean(result.value?.reviewIssues?.length))",
                        "const reviewIssuesPanelVisible = computed(() =>",
                        "if (workflowStatus.value === 'REVISING' && !hasReviewIssues.value) return false",
                        "return hasReviewIssues.value || isReviewFailed.value || reviewCompleted.value",
                        "workflowStatus === 'REVISING' && result?.reviewIssues?.length",
                        "reviewIssues: event.reviewIssues ?? []",
                        "审稿通过 · 0 项",
                        "自动审稿失败，但正文已保留。",
                        "正在根据以上问题修改正文。",
                        "v-if=\"workflowStatus === 'REVISING' && result?.reviewIssues?.length\"",
                        "v-else-if=\"isReviewFailed && !result?.reviewIssues?.length\"",
                        "v-else-if=\"reviewCompleted && !result?.reviewIssues?.length\"",
                        "const shouldAutoScroll = streamingAtBottom.value",
                        "function isStreamingContentNearBottom(container: HTMLElement)",
                        "container.scrollHeight - container.scrollTop - container.clientHeight <= STREAM_BOTTOM_THRESHOLD_PX",
                        "streamingAtBottom.value = isStreamingContentNearBottom(container)",
                        "if (shouldAutoScroll && streamingAtBottom.value)",
                        "streamingAtBottom.value",
                        "scrollStreamingContentToBottom",
                        "scrollStreamingContentToTop",
                        "toggleStreamingContentScroll",
                        "streamingContentScrollable",
                        "{{ streamingAtBottom ? '回到顶部' : '回到底部' }}",
                        "回到底部",
                        "回到顶部",
                        "<el-button text type=\"primary\" size=\"small\" @click=\"toggleStreamingContentScroll\">",
                        ".streaming-scroll-actions",
                        ".streaming-scroll-actions { position: sticky; bottom: 12px;",
                        ".streaming-scroll-actions .el-button { pointer-events: auto; }",
                        ":content=\"result?.content || streamingContent\""
                );
        assertThat(view)
                .doesNotContain("streamingContent.value += event.content")
                .doesNotContain("当前没有可展示的审阅问题")
                .contains(
                        "onBeforeUnmount(() =>",
                        "stopStreamingDisplayTimer()",
                        "resetStreamingContent()"
                );

        System.out.println("章节正文流式显示契约通过：DRAFT_CHUNK 追加正文，用户上滑后暂停自动滚动并可回到底部");
    }

    @Test
    void shouldBindStreamingAutoScrollToTheGenerationPageContainer() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));
        int drainStart = view.indexOf("function drainReceivedBuffer()");
        int enqueueStart = view.indexOf("function enqueueReceivedContent(", drainStart);
        int handleStart = view.indexOf("function handleStreamingScroll(");
        int nearBottomStart = view.indexOf("function isStreamingContentNearBottom(", handleStart);
        int scrollStart = view.indexOf("function scrollStreamingContentToBottom(");
        int loadPlanStart = view.indexOf("async function loadCurrentChapterPlan(", scrollStart);

        String drainFunction = drainStart >= 0 && enqueueStart >= 0
                ? view.substring(drainStart, enqueueStart)
                : "";
        String handleFunction = handleStart >= 0 && nearBottomStart >= 0
                ? view.substring(handleStart, nearBottomStart)
                : "";
        String scrollFunction = scrollStart >= 0 && loadPlanStart >= 0
                ? view.substring(scrollStart, loadPlanStart)
                : "";

        System.out.printf(
                "Generate 流式滚动容器绑定检查：view=%d, drain=%d, handle=%d, scroll=%d%n",
                view.length(),
                drainFunction.length(),
                handleFunction.length(),
                scrollFunction.length()
        );

        assertThat(view)
                .contains(
                        "<div class=\"generation-page workbench-shell\">",
                        "<ChapterCanvas",
                        "ref=\"generationCanvas\"",
                        "@scroll=\"handleStreamingScroll\""
                )
                .doesNotContain("ref=\"streamingContentContainer\"");
        assertThat(drainFunction)
                .contains(
                        "const container = generationCanvas.value?.getElement() ?? null",
                        "streamingAtBottom.value = isStreamingContentNearBottom(container)",
                        "const shouldAutoScroll = streamingAtBottom.value",
                        "if (shouldAutoScroll && streamingAtBottom.value)",
                        "scrollStreamingContentToBottom('auto')"
                );
        assertThat(handleFunction)
                .contains(
                        "function handleStreamingScroll(container: HTMLElement | null)",
                        "updateStreamingScrollState(container)"
                );
        assertThat(scrollFunction)
                .contains(
                        "const container = generationCanvas.value?.getElement() ?? null",
                        "container.scrollTo({",
                        "top: container.scrollHeight",
                        "behavior,",
                        "function scrollStreamingContentToTop(behavior: ScrollBehavior = 'smooth')",
                        "top: 0",
                        "function toggleStreamingContentScroll()"
                );
        assertThat(view)
                .contains(
                        "streamingContentScrollable.value = container.scrollHeight - container.clientHeight > STREAM_BOTTOM_THRESHOLD_PX"
                )
                .doesNotContain(".streaming-scroll-actions { position: fixed;");
        assertThat(drainStart).isGreaterThanOrEqualTo(0);
        assertThat(handleStart).isGreaterThan(drainStart);
        assertThat(scrollStart).isGreaterThan(handleStart);

        System.out.println("Generate 流式滚动容器绑定通过：直接操作 ChapterCanvas 正文容器，自动跟随可暂停，单按钮在回顶/回底之间切换");
    }

    @Test
    void shouldDriveGenerateViewWithExplicitWorkflowStatus() throws IOException {
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        System.out.printf("章节工作流状态收口契约检查：types=%d, view=%d%n", types.length(), view.length());

        assertThat(types)
                .contains(
                        "export type WorkflowStatus =",
                        "| 'IDLE'",
                        "| 'DRAFTING'",
                        "| 'REVIEWING'",
                        "| 'REVIEW_FAILED'",
                        "| 'REVISING'",
                        "| 'COMPRESSING'",
                        "| 'PERSISTING'",
                        "| 'WAITING_HUMAN'",
                        "| 'COMPLETED'",
                        "| 'FAILED'",
                        "| 'CANCELLED'"
                );
        assertThat(view)
                .contains(
                        "const workflowStatus = ref<WorkflowStatus>('IDLE')",
                        "const workflowStatusByEvent",
                        "GENERATION_ABORTED: 'CANCELLED'",
                        "workflowStatus.value = workflowStatusFromApi(result.value.status, workflowStatus.value)",
                        "const isWorkflowBlocked = computed(() =>",
                        "<ChapterCanvas",
                        ":content=\"result?.content || streamingContent\"",
                        ":empty-busy=\"isWorkflowBusy\""
                )
                .doesNotContain(
                        "Boolean(generationSession && !result)",
                        "generationSession.value && !result.value",
                        "result?.status === 'WAITING_HUMAN'",
                        "result?.status === 'REVIEW_FAILED'",
                        "v-else-if=\"isWorkflowBusy\""
                );

        System.out.println("章节工作流状态收口契约通过：页面状态完全由 workflowStatus 驱动，旧的 Session/Result 推断已移除");
    }
}
