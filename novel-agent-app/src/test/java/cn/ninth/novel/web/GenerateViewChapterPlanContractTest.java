package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateViewChapterPlanContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static String readSource(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void shouldLoadGenerateAndConfirmCurrentChapterPlanWithSingleChapterControls() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        System.out.printf("Generate ChapterPlan 生成确认契约检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'",
                        "import { useRoute, useRouter } from 'vue-router'",
                        "import { acceptGenerationSession, createGenerationSession, getActiveGenerationSession, resumeChapter, stopGenerationSession, subscribeGenerationSessionEvents } from '../api/chapter'",
                        "import { confirmChapterPlan, generateChapterPlan, listChapterPlans, listOutlineNodes, updateChapterPlan } from '../api/planning'",
                        "import type { ChapterPlan, GenerateChapterResponse, GenerationSessionEvent, GenerationSessionResponse, OutlineNode, PlanningDraftResponse, WorkflowStatus } from '../types'",
                        "const chapterPlan = ref<ChapterPlan | null>(null)",
                        "const outlineNodes = ref<OutlineNode[]>([])",
                        "const currentChapterArc = computed(() =>",
                        "const route = useRoute()",
                        "const initializingGenerationPage = ref(true)",
                        "const generationPageInitialized = ref(false)",
                        "const chapterNum = ref<number | null>(null)",
                        "const requestedChapter = Number(route.query.chapter)",
                        "type ChapterPlanDraft = Pick<ChapterPlan, 'summary'>",
                        "const chapterPlanRequirement = ref('')",
                        "留空将按当前大纲和前文自然生成。",
                        "const chapterPlanDraft = ref<ChapterPlanDraft | null>(null)",
                        "const chapterPlanDraftId = ref('')",
                        "const chapterPlanConfirming = ref(false)",
                        "const canGenerateSingleChapter = computed(() =>",
                        "import ChapterDirectory, { type ChapterDirectoryVolume } from '../components/workbench/ChapterDirectory.vue'",
                        "<ChapterDirectory",
                        "class=\"generation-stage\"",
                        "<ChapterToolbar",
                        "@previous=\"goPreviousChapter\"",
                        "@next=\"goNextChapter\"",
                        "<span class=\"status-pill\">{{ statusLabel(workflowStatus) }}</span>",
                        "@click=\"openChapterPlanDrawer\"",
                        "章纲",
                        "ChapterPlan",
                        "const existingPlans = await listChapterPlans(project.projectCode)",
                        "const defaultChapter = resolveDefaultChapter(",
                        "chapterNum.value = defaultChapter",
                        "chapterPlanLoadPromise = loadCurrentChapterPlan(",
                        "await chapterPlanLoadPromise",
                        "initializingGenerationPage.value = false",
                        "onMounted(() => {",
                        "chapterPlanDraft.value = null",
                        "generateCurrentChapterPlan",
                        "const draft: PlanningDraftResponse<ChapterPlan> = await generateChapterPlan(",
                        "{ requirement: chapterPlanRequirement.value.trim() }",
                        "chapterPlanDraft.value = {",
                        "chapterPlanDraftId.value = draft.draftId",
                        "v-model=\"chapterPlanDraft.summary\"",
                        "aria-label=\"章节计划草稿预览\"",
                        "可编辑后确认，确认前不会替换当前计划",
                        "confirmCurrentChapterPlan",
                        "await confirmChapterPlan(projectCode, chapterNumber, {",
                        "draftId,",
                        "title: arc.title,",
                        "const summary = draft.summary.trim()",
                        "await loadCurrentChapterPlan()",
                        "章节计划已确认，现在可以生成正文",
                        "error instanceof ApiBusinessError",
                        "{{ chapterPlanStatusLabel }}",
                        "{{ chapterPlanStatusLabel }}",
                        "return statusLabel(chapterPlan.value.status)",
                        "status === 'READY'",
                        "status === 'COMPLETED'",
                        "@click=\"generateSingleChapter\"",
                        "生成本章",
                        "<ChapterCanvas",
                        "result?.chapterNumber",
                        "generationSession.value = await createGenerationSession(",
                        ":disabled=\"!workflowStateReady || !canGenerateSingleChapter || isWorkflowBlocked\"",
                        "v-if=\"chapterPlan?.status === 'COMPLETED'\"",
                        "@click=\"goReadChapter(chapterNum)\"",
                        "查看正文"
                )
                .doesNotContain(
                        "outlineNodeCode: ''",
                        "chapterPlan.value = chapterPlan.value",
                        "fakeVolume",
                        "defaultVolume = {",
                        "chapterPlan.status === 'PLANNED'",
                        "@click=\"confirmChapterPlan\"",
                        "chapterPlanDraft.status",
                        "chapterPlanDraft.outlineNodeCode",
                        "chapterPlanDraft.title",
                        "chapterPlanEditor.title",
                        "chapterPlan.title",
                        "const mode",
                        "modeOptions",
                        "el-segmented",
                        "批量生成",
                        "生成范围",
                        "连续章节",
                        "起始章节",
                        "生成 N 章",
                        "第 X 至 Y 章",
                        "batchStart",
                        "batchEnd",
                        "batchCount",
                        "batching",
                        "batchResult",
                        "completedBatchCount",
                        "GenerateChaptersResponse",
                        "generateBatch",
                        "generateChapterRange",
                        "submitGeneration",
                        "batch-overview",
                        "chapter-results",
                        "chapter-result-row",
                        "batch-result",
                        "row-number",
                        "row-main",
                        "result-placeholder",
                        "el-input-number",
                        "chapter-number-input"
                );

        System.out.println("Generate ChapterPlan 生成/确认契约通过：Draft 仅开放 summary，标题统一继承 ARC.title");
    }

    @Test
    void shouldResolveCurrentChapterArcOnlyThroughVolumeParent() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );
        int chapterArcsStart = view.indexOf("const chapterArcs = computed(() =>");
        int historyStart = view.indexOf("const chapterPlanHistoryVolumes = computed", chapterArcsStart);
        String currentArcResolution = chapterArcsStart >= 0 && historyStart >= 0
                ? view.substring(chapterArcsStart, historyStart)
                : "";

        assertThat(currentArcResolution)
                .contains("parent.nodeCode === node.parentNodeCode && parent.nodeKind === 'VOLUME'")
                .doesNotContain("parent.nodeCode === node.parentNodeCode && parent.nodeKind === 'BOOK'");
        System.out.println("Generate currentChapterArc hierarchy contract passed: BOOK -> ARC compatibility is absent");
    }

    @Test
    void shouldMoveToNextChapterPlanWithoutStartingGeneration() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );
        int nextStart = view.indexOf("async function generateNextChapter()");
        int singleStart = view.indexOf("async function generateSingleChapter()", nextStart);
        String nextAction = nextStart >= 0 && singleStart >= 0
                ? view.substring(nextStart, singleStart)
                : "";

        System.out.printf("Generate 下一章定位契约检查：next=%d%n", nextAction.length());

        assertThat(view)
                .contains(
                        "继续下一章",
                        "@click=\"generateNextChapter\"",
                        "function selectDirectoryChapter(targetChapter: number)"
                );
        assertThat(nextAction)
                .contains(
                        "const targetChapter = chapterNumberForArc(nextChapterArc.value, 1)",
                        "chapterNum.value = targetChapter",
                        "await nextTick()",
                        "await chapterPlanLoadPromise"
                )
                .doesNotContain(
                        "generateSingleChapter()",
                        "createGenerationSession(",
                        "canGenerateSingleChapter.value",
                        "ElMessage.warning("
                );

        System.out.println("Generate 下一章定位契约通过：只切换到下一章并加载 ChapterPlan，不自动生成正文");
    }

    @Test
    void shouldNavigateOnlyBetweenExistingArcsAndBlockSwitchingDuringWorkflow() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );
        int navigationStart = view.indexOf("function chapterNumberForArc(");
        int nextActionStart = view.indexOf("async function generateNextChapter()", navigationStart);
        String navigation = navigationStart >= 0 && nextActionStart >= 0
                ? view.substring(navigationStart, nextActionStart)
                : "";

        System.out.printf("Generate ARC 切章契约检查：navigation=%d%n", navigation.length());

        assertThat(view)
                .contains(
                        "const chapterArcs = computed(() =>",
                        "const previousChapterArc = computed(() =>",
                        "const nextChapterArc = computed(() =>",
                        "const canNavigatePreviousChapter = computed(() => previousChapterArc.value !== null)",
                        "const canNavigateNextChapter = computed(() => nextChapterArc.value !== null)",
                        "function chapterNumberForArc(arc: OutlineNode | null, direction: -1 | 1)",
                        "const activeWorkflowStatuses: WorkflowStatus[] = [",
                        "'DRAFTING',",
                        "'REVIEWING',",
                        "'REVISING',",
                        "|| isWorkflowBusy.value",
                        "|| workflowStatus.value === 'WAITING_HUMAN'",
                        "chapterNumberForArc(previousChapterArc.value, -1)",
                        "chapterNumberForArc(nextChapterArc.value, 1)",
                        ":previous-disabled=\"!canNavigatePreviousChapter || chapterNavigationBusy\"",
                        ":next-disabled=\"!canNavigateNextChapter || chapterNavigationBusy\"",
                        ":disabled=\"nextChapterPreparing || !canNavigateNextChapter\""
                );
        assertThat(navigation)
                .doesNotContain(
                        "chapterNum.value + 1",
                        "chapterNum.value - 1",
                        "chapterNumber + 1",
                        "chapterNumber - 1"
                );

        System.out.println("Generate ARC 切章契约通过：仅在已有相邻 ARC 间跳转，工作流进行时锁定切章");
    }

    @Test
    void shouldRenderGenerateChapterDirectoryAndCurrentPlanDrawer() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        System.out.printf("Generate 章节目录与 ChapterPlan 抽屉契约检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "查看章节计划",
                        "@click=\"openChapterPlanDrawer\"",
                        "const chapterPlans = ref<ChapterPlan[]>([])",
                        "const chapterPlanHistoryVolumes = computed<ChapterPlanHistoryVolume[]>(() =>",
                        "parent.nodeCode === node.parentNodeCode && parent.nodeKind === 'BOOK'",
                        "node.parentNodeCode === volume.nodeCode",
                        "node.nodeKind === 'ARC'",
                        "plan: plansByChapter.get(`${arc.nodeCode}:${arc.startChapter}`) ?? null",
                        "<ChapterDirectory",
                        ":volumes=\"chapterDirectoryVolumes\"",
                        "@select-chapter=\"selectDirectoryChapter\"",
                        "class=\"chapter-plan-drawer-head\"",
                        "章纲",
                        "ChapterPlan",
                        "{{ chapterPlanStatusLabel }}",
                        "function openChapterPlanEditor()",
                        "function openChapterPlanAi()",
                        "sequenceNo: volumeGroup.volume.sequenceNo",
                        "title: arc.title,",
                        "metaLabel: chapterPlanHistoryStatus(plan)",
                        "chapterNumber: arc.startChapter ?? null",
                        "'无计划'",
                        "return statusLabel(plan.status)"
                );

        assertThat(view)
                .contains(
                        "function selectDirectoryChapter(targetChapter: number)",
                        "chapterNum.value = targetChapter"
                )
                .doesNotContain("selectedChapterPlanHistoryKey", "历史章节计划浏览", "class=\"chapter-plan-history-drawer\"");

        System.out.println("Generate 章节目录与 ChapterPlan 抽屉契约通过：目录负责切章，当前 ChapterPlan 只在抽屉中编辑");
    }

    @Test
    void shouldKeepTheWholeGenerateWorkspaceHiddenUntilInitialPlanIsReady() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );
        int pageStart = view.indexOf("<div class=\"generation-page workbench-shell\">");
        int loadingStart = view.indexOf(
                "<div v-if=\"initializingGenerationPage\" class=\"generation-page-loading\"",
                pageStart
        );
        int workspaceGate = view.indexOf("<template v-else>", loadingStart);
        int workspaceStart = view.indexOf("<ChapterDirectory", workspaceGate);
        int initializationStart = view.indexOf("async function initializeGenerationPage()");
        int planList = view.indexOf("const existingPlans = await listChapterPlans(project.projectCode)", initializationStart);
        int chapterResolution = view.indexOf("const defaultChapter = resolveDefaultChapter(", planList);
        int chapterAssignment = view.indexOf("chapterNum.value = defaultChapter", chapterResolution);
        int planLoad = view.indexOf(
                "await chapterPlanLoadPromise",
                chapterAssignment
        );
        int initializationComplete = view.indexOf("initializingGenerationPage.value = false", planLoad);

        System.out.printf(
                "Generate 首屏 loading 门控检查：loading=%d, gate=%d, workspace=%d, plans=%d, resolve=%d, chapter=%d, load=%d, complete=%d%n",
                loadingStart,
                workspaceGate,
                workspaceStart,
                planList,
                chapterResolution,
                chapterAssignment,
                planLoad,
                initializationComplete
        );

        assertThat(view)
                .contains(
                        "class=\"generation-page-loading\"",
                        "<ChapterDirectory",
                        "role=\"status\" aria-live=\"polite\"",
                        ".generation-page-loading {"
                );
        assertThat(pageStart).isGreaterThanOrEqualTo(0);
        assertThat(loadingStart).isGreaterThan(pageStart);
        assertThat(workspaceGate).isGreaterThan(loadingStart);
        assertThat(workspaceStart).isGreaterThan(workspaceGate);
        assertThat(planList).isGreaterThan(initializationStart);
        assertThat(chapterResolution).isGreaterThan(planList);
        assertThat(chapterAssignment).isGreaterThan(chapterResolution);
        assertThat(planLoad).isGreaterThan(chapterAssignment);
        assertThat(initializationComplete).isGreaterThan(planLoad);

        System.out.println("Generate 首屏展示契约通过：ChapterPlan 加载、章节解析和当前计划加载完成前只显示轻量 loading");
    }

    @Test
    void shouldKeepGeneratePageChapterPlanInsideDrawerOnly() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        System.out.printf("Generate ChapterPlan Drawer 契约检查：view=%d%n", view.length());

        assertThat(view)
                .doesNotContain("class=\"chapter-plan-panel\"", "class=\"chapter-plan-summary\"", "{{ chapterPlan.summary }}")
                .contains(
                        "class=\"chapter-plan-drawer\" aria-label=\"章节计划编辑\"",
                        "class=\"chapter-plan-drawer-head\"",
                        "章纲",
                        "ChapterPlan",
                        "{{ chapterPlanStatusLabel }}",
                        "编辑计划",
                        "AI 生成/调整",
                        "@click=\"openChapterPlanDrawer\"",
                        "function openChapterPlanEditor()",
                        "function openChapterPlanAi()"
                );

        assertThat(view)
                .contains(
                        "import { confirmChapterPlan, generateChapterPlan, listChapterPlans, listOutlineNodes, updateChapterPlan } from '../api/planning'",
                        "<el-drawer",
                        "v-model=\"chapterPlanDrawerVisible\"",
                        ":title=\"chapterPlanDrawerTitle\"",
                        "aria-label=\"章节计划编辑\"",
                        "v-if=\"initializingGenerationPage\"",
                        "正在准备章节计划……",
                        "v-model=\"chapterPlanEditor.summary\"",
                        "章节计划草稿预览",
                        "@click=\"saveCurrentChapterPlan\"",
                        "await updateChapterPlan(projectCode, chapterNumber, {",
                        "title: arc.title,",
                        "确认计划"
                );

        System.out.println("Generate ChapterPlan Drawer 契约通过：主工作区不常驻摘要，编辑、AI 调整与 Draft 确认均位于 Drawer");
    }

    @Test
    void shouldGateSingleChapterGenerationByChapterPlanStatus() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        System.out.printf("Generate ChapterPlan 前置状态契约检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "const canGenerateSingleChapter = computed(() =>",
                        "chapterPlan.value?.status === 'READY'",
                        "!chapterPlanLoading.value",
                        "!chapterPlanLoadFailed.value,",
                        "v-if=\"chapterPlan?.status === 'COMPLETED'\"",
                        "@click=\"goReadChapter(chapterNum)\"",
                        "查看正文",
                        ":disabled=\"!workflowStateReady || !canGenerateSingleChapter || isWorkflowBlocked\""
                );

        System.out.println("Generate ChapterPlan 前置状态契约通过：无计划/PLANNED 禁用，READY 允许，COMPLETED 仅查看正文");
    }

    @Test
    void shouldResolveChapterOnlyAfterExistingPlansAreLoaded() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        int initializationStart = view.indexOf("async function initializeGenerationPage()");
        int projectStart = view.indexOf("const project = projectStore.active", initializationStart);
        int urlStart = view.indexOf("const requestedChapter = Number(route.query.chapter)", projectStart);
        int plansStart = view.indexOf("const existingPlans = await listChapterPlans(project.projectCode)", urlStart);
        int resolveStart = view.indexOf("const defaultChapter = resolveDefaultChapter(", plansStart);
        int chapterAssignment = view.indexOf("chapterNum.value = defaultChapter", resolveStart);
        int watcherStart = view.indexOf("watch(chapterNum, async", chapterAssignment);
        int planLoad = view.indexOf("await chapterPlanLoadPromise", watcherStart);
        int initializingEnd = view.indexOf("initializingGenerationPage.value = false", planLoad);

        System.out.printf(
                "Generate 初始化顺序检查：view=%d, project=%d, url=%d, plans=%d, resolve=%d, chapter=%d, watcher=%d, plan=%d, end=%d%n",
                view.length(),
                projectStart,
                urlStart,
                plansStart,
                resolveStart,
                chapterAssignment,
                watcherStart,
                planLoad,
                initializingEnd
        );

        assertThat(view)
                .contains(
                        "const initializingGenerationPage = ref(true)",
                        "const chapterNum = ref<number | null>(null)",
                        "const existingOutlines = await listOutlineNodes(project.projectCode)",
                        "class=\"generation-page-loading\"",
                        "<ChapterDirectory"
                )
                .doesNotContain(
                        "const chapterNum = ref(Number.isInteger(requestedChapter) && requestedChapter > 0 ? requestedChapter : 1)"
                );
        assertThat(projectStart).isGreaterThanOrEqualTo(initializationStart);
        assertThat(urlStart).isGreaterThan(projectStart);
        assertThat(plansStart).isGreaterThan(urlStart);
        assertThat(resolveStart).isGreaterThan(plansStart);
        assertThat(chapterAssignment).isGreaterThan(resolveStart);
        assertThat(watcherStart).isGreaterThan(chapterAssignment);
        assertThat(planLoad).isGreaterThan(chapterAssignment);
        assertThat(initializingEnd).isGreaterThan(planLoad);

        System.out.println("Generate 初始化顺序契约通过：现有 ChapterPlan 加载并解析默认章节后设置 chapterNum，由 watcher 负责加载当前计划并退出 initializing");
    }

    @Test
    void shouldUseTheSimplifiedDefaultChapterPriority() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        int resolverStart = view.indexOf("function resolveDefaultChapter(");
        int resolverEnd = view.indexOf("\n}\n\nasync function initializeGenerationPage", resolverStart);
        String resolver = resolverStart >= 0 && resolverEnd >= 0
                ? view.substring(resolverStart, resolverEnd)
                : "";

        System.out.printf("Generate 默认章节优先级检查：view=%d, resolver=%d%n", view.length(), resolver.length());

        assertThat(resolver)
                .contains(
                        "if (isValidChapterNumber(requestedChapter)) return requestedChapter",
                        ".sort((left, right) => right.chapterNumber - left.chapterNumber)[0]",
                        "const latestPlan",
                        "return latestPlan?.chapterNumber ?? 1"
                )
                .doesNotContain(
                        "recoverableSession",
                        "findRecoverableGenerationSession",
                        "latestUnfinishedPlan",
                        "status !== 'COMPLETED'",
                        "currentChapterNumber",
                        "latestPlanNumber + 1",
                        "chapterNumber + 1"
                );

        System.out.println("Generate 默认章节优先级契约通过：URL、最大 ChapterPlan、第一章依次兜底，COMPLETED 也按最新计划处理");
    }

    @Test
    void shouldKeepExplicitUrlChapterBeforeLatestPlanFallback() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        int initializationStart = view.indexOf("async function initializeGenerationPage()");
        int requestedChapter = view.indexOf("const requestedChapter = Number(route.query.chapter)", initializationStart);
        int defaultChapter = view.indexOf("const defaultChapter = resolveDefaultChapter(", requestedChapter);
        int chapterAssignment = view.indexOf("chapterNum.value = defaultChapter", defaultChapter);

        System.out.printf(
                "Generate URL 章节优先检查：init=%d, url=%d, resolve=%d, chapter=%d%n",
                initializationStart,
                requestedChapter,
                defaultChapter,
                chapterAssignment
        );

        assertThat(view)
                .contains(
                        "if (isValidChapterNumber(requestedChapter)) return requestedChapter",
                        "const defaultChapter = resolveDefaultChapter(",
                        "chapterNum.value = defaultChapter"
                );
        assertThat(initializationStart).isGreaterThanOrEqualTo(0);
        assertThat(requestedChapter).isGreaterThan(initializationStart);
        assertThat(defaultChapter).isGreaterThan(requestedChapter);
        assertThat(chapterAssignment).isGreaterThan(defaultChapter);

        System.out.println("Generate URL 章节优先契约通过：chapter=7 会直接作为默认章节，不会被最新 ChapterPlan 覆盖");
    }

    @Test
    void shouldLoadInitialPlanOnlyAfterChapterNumberIsResolved() throws IOException {
        String view = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        int initializationStart = view.indexOf("async function initializeGenerationPage()");
        int initializationEnd = view.indexOf("\n}\n\nonMounted", initializationStart);
        int watcherStart = view.indexOf("watch(chapterNum, async", initializationEnd);
        int watcherCleanupGuard = view.indexOf("if (generationPageInitialized.value)", watcherStart);
        int watcherSseCleanup = view.indexOf("closeGenerationEventStream()", watcherStart);
        int watcherPlanLoad = view.indexOf("await chapterPlanLoadPromise", watcherStart);

        String initialization = initializationStart >= 0 && initializationEnd >= 0
                ? view.substring(initializationStart, initializationEnd)
                : "";

        System.out.printf(
                "Generate 初始化重复请求检查：init=%d, watcher=%d, guard=%d, cleanup=%d, plan=%d%n",
                initialization.length(),
                watcherStart,
                watcherCleanupGuard,
                watcherSseCleanup,
                watcherPlanLoad
        );

        assertThat(initialization)
                .contains("const existingPlans = await listChapterPlans(project.projectCode)")
                .contains("const existingOutlines = await listOutlineNodes(project.projectCode)")
                .doesNotContain("loadCurrentChapterPlan(", "findRecoverableGenerationSession(");
        assertThat(view)
                .contains(
                        "const generationPageInitialized = ref(false)",
                        "const isInitialChapterSelection = !generationPageInitialized.value",
                        "if (generationPageInitialized.value)",
                        "chapterPlanLoadPromise = loadCurrentChapterPlan(",
                        "await chapterPlanLoadPromise",
                        "await loadCurrentChapterPlan()",
                        "await nextTick()",
                        "generationPageInitialized.value = true",
                        "initializingGenerationPage.value = false"
                )
                .doesNotContain("outlineNodeCode: ''");
        int loaderEntryCount = view.split("loadCurrentChapterPlan\\(", -1).length - 1;
        assertThat(loaderEntryCount).isEqualTo(3);
        assertThat(watcherStart).isGreaterThan(initializationEnd);
        assertThat(watcherCleanupGuard).isGreaterThan(watcherStart);
        assertThat(watcherSseCleanup).isGreaterThan(watcherCleanupGuard);
        assertThat(watcherPlanLoad).isGreaterThan(watcherSseCleanup);

        System.out.println("Generate 初始化重复请求契约通过：先加载 ChapterPlan 再解析 chapterNum，watcher 只负责加载解析后的当前计划");
    }

    @Test
    void shouldKeepChapterPlanRequirementOptionalInTheFrontendContract() throws IOException {
        String types = readSource(
                PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts")
        );

        System.out.printf("Generate ChapterPlan 可选 requirement 类型契约检查：types=%d%n", types.length());

        assertThat(types)
                .contains("export interface GenerateChapterPlanRequest {\n  requirement?: string\n}")
                .doesNotContain("export interface GenerateChapterPlanRequest {\n  requirement: string");
        System.out.println("Generate ChapterPlan 可选 requirement 类型契约通过");
    }
}
