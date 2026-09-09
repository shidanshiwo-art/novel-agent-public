package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadViewCanvasStyleContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepReadCanvasAndToolbarStylesInSharedComponents() throws IOException {
        String read = read("novel-agent-web/src/views/ReadView.vue");
        String toolbar = read("novel-agent-web/src/components/workbench/ChapterToolbar.vue");
        String title = read("novel-agent-web/src/components/workbench/ChapterTitle.vue");
        String canvas = read("novel-agent-web/src/components/workbench/ChapterCanvas.vue");

        System.out.printf("Read 公共画布样式检查：read=%d, toolbar=%d, title=%d, canvas=%d%n",
                read.length(), toolbar.length(), title.length(), canvas.length());

        assertThat(read)
                .contains("<ChapterToolbar", "<ChapterCanvas v-if=\"selectedChapter\"", "v-model=\"editTitle\"")
                .doesNotContain(".paper-scroll {");
        assertThat(toolbar)
                .contains(
                        ".chapter-toolbar { position: sticky; top: 0;",
                        "display: flex;",
                        "gap: clamp(6px, 1vw, 12px);",
                        "white-space: nowrap;",
                        ".chapter-toolbar :deep(.chapter-toolbar-title)",
                        ".toolbar-meta :deep(.status-pill)",
                        ".toolbar-meta :deep(.el-button)"
                )
                .doesNotContain("grid-template-columns", ".toolbar-meta { display: none;");
        assertThat(title)
                .contains(
                        "<span v-if=\"chapterNumber !== null\" class=\"chapter-title-number\">第 {{ chapterNumber }} 章</span>",
                        ".chapter-title {",
                        "font-size: 20px;",
                        ".chapter-title-number,",
                        "font-weight: inherit;",
                        "chapter-title-input :deep(.el-input__wrapper)"
                )
                .doesNotContain("章 ·", "chapter-title-separator");
        assertThat(canvas)
                .contains(
                        ".chapter-canvas { flex: 1; min-width: 0; min-height: 0; overflow-y: auto;",
                        ".chapter-paper { width: min(var(--workbench-content-width), 100%);",
                        ".chapter-edit-area :deep(.el-textarea__inner)",
                        ":autosize=\"{ minRows: 3 }\"",
                        "border: 0 !important;",
                        "border-radius: 0 !important;",
                        "background: transparent !important;",
                        "box-shadow: none !important;",
                        "caret-color: var(--primary);",
                        "font-size: 16px; line-height: 1.9;"
                );

        System.out.println("Read 公共画布样式检查通过：工具栏与正文编辑画布 CSS 已集中到 workbench 组件");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
