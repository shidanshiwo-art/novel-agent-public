package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VisualButtonSpecContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepCreationPageButtonsOnNormalElementPlusSizing() throws IOException {
        String setup = read("novel-agent-web/src/views/SetupView.vue");
        String outline = read("novel-agent-web/src/views/OutlineView.vue");
        String generate = read("novel-agent-web/src/views/GenerateView.vue");
        String app = read("novel-agent-web/src/App.vue");
        String readView = read("novel-agent-web/src/views/ReadView.vue");
        String theme = read("novel-agent-web/src/assets/theme.css");

        System.out.printf(
                "按钮视觉规格检查：setup=%d outline=%d generate=%d app=%d read=%d%n",
                setup.length(), outline.length(), generate.length(), app.length(), readView.length()
        );

        assertThat(setup)
                .contains("<el-button type=\"primary\"", "class=\"local-delete-button\"")
                .doesNotContain(
                        ".rule-row .el-button { margin-left: 0; padding-inline",
                        ".list-heading .el-button { height:",
                        ".list-heading .el-button { margin-left: 0; padding:"
                );
        assertThat(outline)
                .contains("<el-button text class=\"tree-more\"", "<el-button text type=\"primary\" :loading=\"loading\"")
                .doesNotContain("<el-button link");
        assertThat(generate)
                .containsPattern("<el-button\\s+text\\s+type=\"primary\"\\s+class=\"directory-link\"")
                .contains("<div v-if=\"workflowStatus === 'COMPLETED' && (result?.content || streamingContent)\" class=\"read-chapter-actions\">")
                .doesNotContain("<button");
        assertThat(app)
                .contains("<el-button text class=\"studio-brand\"")
                .contains("<el-button text type=\"primary\" class=\"studio-switch-button\"")
                .doesNotContain("<button");
        assertThat(readView)
                .contains("text circle", "class=\"clear-search\"")
                .contains("<el-button type=\"primary\" :disabled=\"saving\"")
                .doesNotContain("<button");
        assertThat(theme)
                .contains(".el-button--primary.is-text", "color: var(--primary) !important", ".el-button.local-delete-button",
                        ".el-button--default:not(.is-text):not(.is-link):not(.is-plain)")
                .contains("color: var(--text-faint) !important", "color: var(--danger) !important");
        assertThat(setup)
                .contains(".rule-row { grid-template-columns: 24px minmax(0, 1fr) auto; gap: 6px; }")
                .doesNotContain(".rule-row .el-button { overflow:");

        System.out.println("VisualButtonSpecContractTest 已验证主按钮保留正常 Element Plus 尺寸，局部操作使用 text");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
