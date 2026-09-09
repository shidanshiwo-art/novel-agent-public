package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CreationCanvasVisualContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepStoryBibleEditingLowBoundary() throws IOException {
        String setup = read("novel-agent-web/src/views/SetupView.vue");

        System.out.printf("设定页低边界编辑检查：setup=%d%n", setup.length());

        assertThat(setup)
                .contains(
                        "<h2>世界设定</h2>",
                        "<h2>不可违反的规则</h2>",
                        "<el-input v-model=\"bf.hardRules[index]\"",
                        "<el-button text type=\"danger\" class=\"local-delete-button\"",
                        "<el-button text type=\"primary\" class=\"add-rule-button\"",
                        "v-if=\"activeBibleSection === 'world'\"",
                        "v-if=\"activeBibleSection === 'rules'\"",
                        "v-if=\"activeBibleSection === 'power'\"",
                        "v-if=\"activeBibleSection === 'theme'\"",
                        "v-if=\"activeBibleSection === 'ending'\"",
                        "v-if=\"activeBibleSection === 'style'\"",
                        "<el-tag v-else-if=\"hasConfirmedBible\" type=\"success\" effect=\"plain\">已确认</el-tag>",
                        "{{ hasConfirmedBible ? 'AI 调整' : 'AI 生成初始设定' }}",
                        "type=\"primary\" :loading=\"savingBible\""
                )
                .doesNotContain("<button", "<input", "<textarea", "section-eyebrow", "03&nbsp;", "04&nbsp;", "05&nbsp;",
                        "<el-collapse", "scrollIntoView", "IntersectionObserver");

        System.out.println("设定页分区编辑检查通过：各分区按 activeBibleSection 独立渲染，顶部操作保持统一层级");
    }

    @Test
    void shouldUseSectionModeForStoryBible() throws IOException {
        String setup = read("novel-agent-web/src/views/SetupView.vue");

        System.out.printf("设定页分区菜单检查：setup=%d%n", setup.length());

        assertThat(setup)
                .contains(
                        "class=\"bible-content-layout\"",
                        "aria-label=\"设定目录\"",
                        "v-for=\"section in bibleSectionNavigation\"",
                        ":key=\"section.key\"",
                        "@click=\"selectBibleSection(section.key)\"",
                        "class=\"bible-section-nav-select\"",
                        "v-model=\"activeBibleSection\"",
                        "const bibleSectionNavigation: BibleSectionNavigationItem[] = [",
                        "type BibleSectionKey = 'world' | 'rules' | 'power' | 'theme' | 'ending' | 'style'",
                        "const activeBibleSection = ref<BibleSectionKey>('world')",
                        "function selectBibleSection(sectionKey: BibleSectionKey)",
                        "activeBibleSection.value = sectionKey",
                        "v-if=\"activeBibleSection === 'world'\"",
                        "v-if=\"activeBibleSection === 'rules'\"",
                        "v-if=\"activeBibleSection === 'power'\"",
                        "v-if=\"activeBibleSection === 'theme'\"",
                        "v-if=\"activeBibleSection === 'ending'\"",
                        "v-if=\"activeBibleSection === 'style'\"",
                        ".bible-section-nav { position: sticky;",
                        ".bible-content-layout { width: min(100%, 1260px);",
                        "grid-template-columns: clamp(200px, 18vw, 220px) minmax(0, 900px)",
                        "justify-content: start;",
                        "gap: clamp(32px, 3vw, 48px)",
                        ".story-bible-canvas { width: 100%; min-width: 0; min-height: 0; background: transparent; }",
                        ".bible-section-nav-list { display: none; }",
                        ".bible-section-nav-select { display: block;",
                        ".setup-tabs :deep(.el-tabs__content) { overflow: visible; }"
                )
                .doesNotContain(
                        "scrollToBibleSection",
                        "scrollIntoView",
                        "IntersectionObserver",
                        "bible-world-section",
                        "bible-rules-section",
                        "bible-power-section",
                        "bible-theme-section",
                        "bible-ending-section",
                        "bible-style-section",
                        "expandedBibleSections",
                        "<el-collapse",
                        "border-top: 2px solid var(--primary)"
                );

        System.out.println("设定页分区菜单检查通过：目录只切换 activeBibleSection，右侧不再同时渲染整份文档");
    }

    @Test
    void shouldUseSharedChapterWorkbenchComponents() throws IOException {
        String read = read("novel-agent-web/src/views/ReadView.vue");
        String generate = read("novel-agent-web/src/views/GenerateView.vue");
        String directory = read("novel-agent-web/src/components/workbench/ChapterDirectory.vue");
        String toolbar = read("novel-agent-web/src/components/workbench/ChapterToolbar.vue");
        String title = read("novel-agent-web/src/components/workbench/ChapterTitle.vue");
        String navigation = read("novel-agent-web/src/components/workbench/ChapterNavigation.vue");
        String canvas = read("novel-agent-web/src/components/workbench/ChapterCanvas.vue");

        System.out.printf("章节工作台公共组件检查：read=%d, generate=%d, directory=%d, canvas=%d%n",
                read.length(), generate.length(), directory.length(), canvas.length());

        assertThat(read)
                .contains("<ChapterDirectory", "<ChapterToolbar", "<ChapterCanvas v-if=\"selectedChapter\"", "v-model=\"editTitle\"")
                .doesNotContain("<aside class=\"chapter-directory\"", ".chapter-toolbar {");
        assertThat(generate)
                .contains("<ChapterDirectory", "<ChapterToolbar", "<ChapterCanvas", "class=\"contextual-action-bar\"")
                .doesNotContain("<aside class=\"chapter-directory\"", "class=\"results-content");
        assertThat(directory)
                .contains("@update:model-value", "emit('toggle-volume'", "emit('chapter-contextmenu'", "故事总纲 ·", "chapterLabel(chapter)");
        assertThat(toolbar)
                .contains("<ChapterNavigation", "<ChapterTitle", "class=\"chapter-toolbar\"");
        assertThat(title)
                .contains("class=\"chapter-title\"", "class=\"chapter-title-input\"");
        assertThat(navigation)
                .contains("ArrowLeft", "ArrowRight", "上一章", "下一章");
        assertThat(canvas)
                .contains("class=\"chapter-canvas\"", "class=\"chapter-edit-area\"", "class=\"content-preview\"", "defineExpose({ getElement })")
                .doesNotContain("<textarea");

        System.out.println("章节工作台公共组件检查通过：目录、工具栏、标题、导航和正文画布均由 Read/Generate 复用");
    }

    @Test
    void shouldKeepGenerateWorkflowActionsInThePageOnly() throws IOException {
        String generate = read("novel-agent-web/src/views/GenerateView.vue");

        System.out.printf("Generate 工作流操作栏检查：generate=%d%n", generate.length());

        assertThat(generate)
                .contains(
                        "class=\"contextual-action-bar\"",
                        "v-if=\"contextualActionBarVisible\"",
                        "const contextualActionBarStatuses: WorkflowStatus[] = [\n  'IDLE',\n  'DRAFTING',\n  'REVIEW_FAILED',\n  'WAITING_HUMAN',\n  'COMPLETED',\n  'FAILED',\n  'CANCELLED',\n]",
                        "workflow-review-summary",
                        "workflowStatus === 'WAITING_HUMAN'",
                        "statusLabel(workflowStatus)",
                        "hasReviewIssues",
                        "审稿通过，当前没有需要处理的问题。",
                        "自动审稿失败，但正文已保留。",
                        "正在根据以上问题修改正文。",
                        "停止生成",
                        "重新生成",
                        "重新审稿",
                        "采用当前正文",
                        "驳回重写",
                        "回到底部",
                        "继续下一章",
                        "function generateNextChapter()"
                )
                .doesNotContain(
                        "class=\"workflow-progress\"",
                        "class=\"workflow-processing\"",
                        "statusDescription(workflowStatus)",
                        "class=\"review-controls-copy\"",
                        "class=\"review-copy\"",
                        "当前没有可展示的审阅问题",
                        "<section v-else-if=\"workflowStatus === 'REVIEWING'\""
                );

        System.out.println("Generate 工作流操作栏检查通过：纯状态不渲染底部栏，可执行动作与正文回底部操作继续保留");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
