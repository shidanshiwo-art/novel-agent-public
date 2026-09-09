package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DesignTokensAndScrollContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeCanonicalDesignTokensAndKeepCompatibilityAliases() throws IOException {
        String theme = read("novel-agent-web/src/assets/theme.css");

        System.out.printf("Design Token 检查：theme=%d%n", theme.length());

        assertThat(theme)
                .contains(
                        "--app-bg:",
                        "--surface:",
                        "--border:",
                        "--text-primary:",
                        "--text-secondary:",
                        "--primary:",
                        "--success:",
                        "--warning:",
                        "--danger:",
                        "--sidebar-width:",
                        "--toolbar-height:",
                        "--content-max-width:",
                        "--radius-sm:",
                        "--radius-md:",
                        "--bg:            var(--app-bg);",
                        "--text:          var(--text-primary);",
                        "--workbench-content-width: var(--content-max-width);"
                );

        System.out.println("Design Token 检查通过：颜色、布局尺寸、内容宽度和圆角均有统一命名，旧变量保留为兼容别名");
    }

    @Test
    void shouldKeepAppShellAsTheOnlyPageLevelScroller() throws IOException {
        String app = read("novel-agent-web/src/App.vue");
        String theme = read("novel-agent-web/src/assets/theme.css");
        String directory = read("novel-agent-web/src/components/workbench/ChapterDirectory.vue");
        String canvas = read("novel-agent-web/src/components/workbench/ChapterCanvas.vue");
        String outline = read("novel-agent-web/src/views/OutlineView.vue");

        System.out.printf("页面滚动边界检查：app=%d, theme=%d, directory=%d, canvas=%d, outline=%d%n",
                app.length(), theme.length(), directory.length(), canvas.length(), outline.length());

        assertThat(app)
                .contains(".studio-shell { height: 100vh;", "overflow: hidden;")
                .contains(".studio-main { flex: 1; min-width: 0; min-height: 0; height: 100%; display: flex; flex-direction: column; overflow: auto;")
                .contains(".studio-main.workbench-mode { padding: 0; overflow: hidden; }");
        assertThat(theme).contains(".workbench-shell { width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; }");
        assertThat(directory)
                .contains(".chapter-directory { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden;")
                .contains(".chapter-list { flex: 1; min-width: 0; min-height: 0; overflow-y: auto;");
        assertThat(canvas).contains(".chapter-canvas { flex: 1; min-width: 0; min-height: 0; overflow-y: auto;");
        assertThat(outline)
                .contains(".outline-detail {\n  min-width: 0;\n  min-height: 0;\n  overflow-y: auto;")
                .contains(":global(.ai-next-dialog .el-dialog__body) {\n  max-height: calc(100vh - 180px);\n  overflow-y: auto;")
                .contains("@media (max-width: 767px) {\n  .outline-workbench {\n    height: 100%;\n    min-height: 0;")
                .doesNotContain(".ai-next-draft-form {\n  max-height:", ".ai-next-draft-form {\n  max-height: min(58vh");

        System.out.println("页面滚动边界检查通过：AppShell 固定，普通页面由 studio-main 滚动，工作台和弹层使用自身滚动区域");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath)).replace("\r\n", "\n");
    }
}
