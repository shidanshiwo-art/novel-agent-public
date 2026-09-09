package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineViewContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldPresentGeneratedOutlineAsHierarchyInsteadOfManualChapterForm() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        assertThat(view)
                .contains("class=\"outline-directory\"")
                .contains("总纲")
                .contains("AI 生成总纲")
                .contains("{{ selectedNodeNextGenerationButtonLabel }}")
                .contains("+ 手动添加下一{{ selectedNodeNextKindLabel }}")
                .contains("if (node.nodeKind === 'VOLUME' && isVolumeUnplanned(node)) return '生成卷纲'")
                .contains("const isCurrentVolumePlanning = computed")
                .contains("class=\"detail-primary-actions\"")
                .contains("class=\"detail-more\"")
                .contains("detail-node-meta")
                .contains("title=\"该卷尚未完成卷纲\"")
                .contains("description=\"先生成卷纲后再规划章节\"")
                .contains("volumePlanStatusLabel(data)")
                .contains("function canManuallyCreateChild(node")
                .contains("if (nodeKind === 'BOOK') return 'VOLUME'")
                .contains("if (nodeKind === 'VOLUME') return 'ARC'")
                .contains("v-else-if=\"selectedNode.nodeKind === 'ARC'\"")
                .contains("@click=\"goToGeneration\"")
                .contains("if (!node || node.nodeKind !== 'ARC' || node.startChapter == null) return")
                .contains("router.push({ path: '/generate', query: { chapter: node.startChapter } })")
                .contains("function isVolumeUnplanned(node")
                .contains("function ensureVolumePlanned(node")
                .contains("请先完成当前卷规划")
                .doesNotContain("volumePlanStatusLabel(selectedNode)")
                .doesNotContain("NEXT STEP")
                .doesNotContain("node-next")
                .doesNotContain("JSON");

        System.out.println("OutlineViewContractTest verified header node-driven next outline actions");
    }

    @Test
    void shouldShowChineseOutlineLabelsInsteadOfInternalKindsAndStatuses() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        assertThat(view)
                .contains("{{ outlineTreeLabel(data) }}")
                .contains(":title=\"outlineTreeLabel(data)\"")
                .contains("{{ outlineKindLabel(selectedNode) }}")
                .contains("{{ outlineDisplayTitle(selectedNode) }}")
                .contains("function outlineKindLabel(node: Pick<OutlineNode, 'nodeKind' | 'sequenceNo' | 'startChapter'>)")
                .contains("function outlineDisplayTitle(node: Pick<OutlineNode, 'nodeKind' | 'title' | 'sequenceNo'>)")
                .contains("function volumeStoryTitle(")
                .contains("function volumeStructureLabel(sequenceNo: number)")
                .contains("return statusLabel(isVolumeUnplanned(node) ? 'UNPLANNED' : 'PLANNED')")
                .contains("projectStore.active?.title ?? node.title")
                .contains("node.nodeKind === 'BOOK'", ": node.title")
                .contains("return node.startChapter == null ? '章节' : `第${node.startChapter}章`")
                .contains("function outlineTreeLabel(node: WorkbenchTreeNode)")
                .contains("return `${kind} ${title}`")
                .contains("function toChineseNumber(value: number)")
                .doesNotContain("chapterRange(selectedNode)")
                .doesNotContain("class=\"tree-range\"")
                .doesNotContain("arcChapterPosition")
                .doesNotContain("return kindLabel(node.nodeKind)")
                .doesNotContain("书一", "书二", "书三")
                .doesNotContain("{{ data.nodeKind }}")
                .doesNotContain("{{ selectedNode.nodeKind }}")
                .doesNotContain("{{ selectedNode.status }}")
                .doesNotContain("类型名称")
                .doesNotContain("kindTagType(", "`${kind} · ${title}`", "卷 ·", "章 ·", "{{ kindLabel(data.nodeKind) }}");

        int treeNodeStart = view.indexOf("<div class=\"tree-node-main\">");
        int treeNodeEnd = view.indexOf("</div>", treeNodeStart);
        int detailHeaderStart = view.indexOf("<header class=\"detail-head\">");
        int detailHeaderEnd = view.indexOf("</header>", detailHeaderStart);
        assertThat(treeNodeStart).isGreaterThanOrEqualTo(0);
        assertThat(treeNodeEnd).isGreaterThan(treeNodeStart);
        assertThat(detailHeaderStart).isGreaterThanOrEqualTo(0);
        assertThat(detailHeaderEnd).isGreaterThan(detailHeaderStart);
        assertThat(view.substring(treeNodeStart, treeNodeEnd))
                .contains("{{ outlineTreeLabel(data) }}")
                .doesNotContain("tree-range");
        assertThat(view.substring(detailHeaderStart, detailHeaderEnd))
                .contains("{{ outlineDisplayTitle(selectedNode) }}")
                .contains("{{ outlineKindLabel(selectedNode) }}")
                .doesNotContain("chapterRange")
                .doesNotContain("章节范围");

        System.out.println("OutlineViewContractTest verified Chinese outline labels and ARC startChapter chapter numbers");
    }

    @Test
    void shouldLoadPersistedOutlineNodesFromPlanningApi() throws IOException {
        String controller = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/NovelPlanningController.java"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts"));

        assertThat(controller)
                .contains("@GetMapping(\"/outlines/tree\")")
                .contains("planningService.listOutlineTree(projectCode)");
        assertThat(api)
                .contains("export const listOutlineNodes")
                .contains("`${base(projectCode)}/outlines/tree`");

        System.out.println("OutlineViewContractTest verified persisted outline tree query");
    }

    @Test
    void shouldBlockRootOutlineGenerationUntilCharactersExist() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        System.out.println("Outline 根大纲前置状态检查：确认人物为空时入口禁用并显示引导文案");
        assertThat(view)
                .contains("import { listCharacters } from '../api/project'")
                .contains("const characters = ref<StoryCharacterResponse[]>([])")
                .contains("const charactersReady = computed(() => characters.value.length > 0)")
                .contains("async function loadCharacters(projectCode?: string)")
                .contains("void loadCharacters(projectCode)")
                .contains(":disabled=\"!charactersReady\"")
                .contains("请先完成核心人物设定")
                .contains("if (!charactersReady.value) return");
    }

    @Test
    void shouldExposeEditableOutlineNodeAndNodeDrivenNextActions() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        assertThat(view)
                .contains("v-model=\"editForm.title\"")
                .contains("v-model=\"editForm.summary\"")
                .contains("@click=\"saveNode\"")
                .contains("updateOutlineNode")
                .contains("handleSelectedNodeCommand")
                .contains("startChapter: node.startChapter ?? null")
                .contains("endChapter: node.endChapter ?? null")
                .contains("goToGeneration")
                .doesNotContain("v-model=\"editForm.startChapter\"")
                .doesNotContain("v-model=\"editForm.endChapter\"")
                .doesNotContain("const chapterRangeLocked = computed")
                .doesNotContain("章节范围由系统分配，无法修改")
                .doesNotContain("class=\"node-next-actions\"")
                .doesNotContain("生成章纲")
                .doesNotContain("查看章纲")
                .doesNotContain("saveSelected")
                .doesNotContain("updateChapterCard");

        System.out.println("OutlineViewContractTest verified editable outline node and header next-step actions");
    }

    @Test
    void shouldRenderMainOutlineTitleAsBorderlessDocumentInput() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        int editorStart = view.indexOf("<div class=\"editor-surface\">");
        int formStart = view.indexOf("<div class=\"editor-form\"", editorStart);
        assertThat(editorStart).isGreaterThanOrEqualTo(0);
        assertThat(formStart).isGreaterThan(editorStart);

        String titleField = view.substring(editorStart, formStart);
        System.out.printf("Outline 标题无边框检查：editor=%d, titleField=%d%n", editorStart, titleField.length());
        assertThat(titleField)
                .contains("<div class=\"outline-title-field\">")
                .contains("class=\"outline-title-editor\"")
                .contains("aria-label=\"标题\"")
                .contains("placeholder=\"输入标题\"")
                .contains("v-model=\"editForm.title\"")
                .doesNotContain("<el-form-item label=\"标题\">");
        assertThat(view)
                .contains(".outline-title-editor :deep(.el-input__wrapper)")
                .contains(".outline-title-editor :deep(.el-input__wrapper.is-focus)")
                .contains("font-size: 28px;")
                .contains("line-height: 1.35;")
                .contains("font-weight: 600;")
                .contains("box-shadow: none !important;")
                .doesNotContain(".editor-form :deep(.el-form-item:first-child .el-input__wrapper)")
                .doesNotContain("border-bottom: 1px solid var(--border2)");

        System.out.println("OutlineViewContractTest verified main outline title uses a borderless document-style input");
    }

    @Test
    void shouldRenderMobileOutlineTitleWithTheSameBorderlessEditor() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        int drawerStart = view.indexOf("<div class=\"drawer-form\">");
        int drawerFormStart = view.indexOf("<div class=\"editor-form\"", drawerStart);

        System.out.printf("Outline 移动端标题无边框检查：drawer=%d, form=%d%n", drawerStart, drawerFormStart);

        assertThat(drawerStart).isGreaterThanOrEqualTo(0);
        assertThat(drawerFormStart).isGreaterThan(drawerStart);
        assertThat(view.substring(drawerStart, drawerFormStart))
                .contains(
                        "class=\"outline-title-field\"",
                        "v-model=\"editForm.title\"",
                        "class=\"outline-title-editor\"",
                        "aria-label=\"标题\"")
                .doesNotContain("<el-form-item label=\"标题\">");

        System.out.println("Outline 移动端标题契约通过：标题移出表单 label，并复用文档式无边框编辑器");
    }

    @Test
    void shouldHideChapterRangesFromTreeAndSelectedNodeEditors() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        assertThat(view)
                .doesNotContain("class=\"tree-range\"")
                .doesNotContain("chapterRange(data)")
                .doesNotContain("chapterRange(selectedNode)")
                .contains("return node.startChapter == null ? '章节' : `第${node.startChapter}章`")
                .doesNotContain("v-model=\"editForm.startChapter\"")
                .doesNotContain("v-model=\"editForm.endChapter\"");

        int editorStart = view.indexOf("<div class=\"editor-surface\">");
        int editorEnd = view.indexOf("<footer class=\"detail-foot\">", editorStart);
        int drawerFormStart = view.indexOf("<div class=\"drawer-form\">");
        int drawerFormEnd = view.indexOf("<div class=\"drawer-actions\">", drawerFormStart);
        assertThat(editorStart).isGreaterThanOrEqualTo(0);
        assertThat(editorEnd).isGreaterThan(editorStart);
        assertThat(drawerFormStart).isGreaterThanOrEqualTo(0);
        assertThat(drawerFormEnd).isGreaterThan(drawerFormStart);
        assertThat(view.substring(editorStart, editorEnd))
                .doesNotContain("章节范围")
                .doesNotContain("startChapter")
                .doesNotContain("endChapter")
                .doesNotContain("第 ");
        assertThat(view.substring(drawerFormStart, drawerFormEnd))
                .doesNotContain("章节范围")
                .doesNotContain("startChapter")
                .doesNotContain("endChapter")
                .doesNotContain("第 ");

        System.out.println("OutlineViewContractTest verified tree/detail chapter-range removal");
    }

    @Test
    void shouldRemoveAllOutlineChapterRangeUiButKeepBackendFields() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        List<String> rangeUiTokens = List.of(
                "<el-form-item label=\"章节范围\"",
                "class=\"tree-range\"",
                "chapterRange(",
                "class=\"range-editor\"",
                "class=\"range-divider\"",
                "addChildRangeHint",
                "isValidChildRange("
        );
        long residualRangeUiTokens = rangeUiTokens.stream()
                .filter(view::contains)
                .count();

        System.out.printf(
                "Outline 章节范围 UI 清理检查：rangeUiTokens=%d%n",
                residualRangeUiTokens
        );
        assertThat(view)
                .doesNotContain(
                        "<el-form-item label=\"章节范围\"",
                        "class=\"tree-range\"",
                        "chapterRange(",
                        "class=\"range-editor\"",
                        "class=\"range-divider\"",
                        "addChildRangeHint",
                        "isValidChildRange("
                )
                .contains(
                        "startChapter: node.startChapter ?? null",
                        "endChapter: node.endChapter ?? null",
                        "item.startChapter",
                        "item.endChapter"
                );

        System.out.println("OutlineViewContractTest verified all outline chapter-range UI is removed while payload fields remain");
    }

    @Test
    void shouldGenerateAndConfirmRootDraftThroughCurrentPlanningApi() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts"));

        assertThat(view)
                .contains("v-model=\"rootGenerationForm.requirement\"")
                .contains("await generateRootOutline(projectCode, { requirement })")
                .contains("draft.payload")
                .contains("await confirmRootOutline(projectCode")
                .contains("await loadOutline(projectCode)")
                .doesNotContain("initialize(code()")
                .doesNotContain("confirmInitialization");
        assertThat(api)
                .contains("export const generateRootOutline")
                .contains("export const confirmRootOutline")
                .contains("/outlines/root/generate")
                .contains("/outlines/root/confirm")
                .doesNotContain("export const updateBookOutline");

        System.out.println("OutlineViewContractTest verified real root outline generate, payload preview and confirmation");
    }
}
