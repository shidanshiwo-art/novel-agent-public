package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadableContentWidthContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseTheSharedReadableWidthForLongTextAreas() throws IOException {
        String theme = read("novel-agent-web/src/assets/theme.css");
        String canvas = read("novel-agent-web/src/components/workbench/ChapterCanvas.vue");
        String setup = read("novel-agent-web/src/views/SetupView.vue");
        String outline = read("novel-agent-web/src/views/OutlineView.vue");
        String generate = read("novel-agent-web/src/views/GenerateView.vue");

        System.out.printf("长文本可读宽度检查：theme=%d, canvas=%d, setup=%d, outline=%d, generate=%d%n",
                theme.length(), canvas.length(), setup.length(), outline.length(), generate.length());

        assertThat(theme)
                .contains("--content-max-width:820px;")
                .contains("--workbench-content-width: var(--content-max-width);");
        assertThat(canvas).contains(".chapter-paper { width: min(var(--workbench-content-width), 100%);");
        assertThat(setup)
                .contains(".bible-content-layout { width: min(100%, 1260px); min-width: 0;")
                .contains("grid-template-columns: clamp(200px, 18vw, 220px) minmax(0, 900px)")
                .contains("gap: clamp(32px, 3vw, 48px)")
                .contains(".bible-section { width: 100%; box-sizing: border-box; min-width: 0; padding: 24px 0 34px; }")
                .contains(".story-bible-canvas { width: 100%; min-width: 0; min-height: 0; background: transparent; }")
                .contains(".bible-draft-banner > div:first-child { min-width: 0; }")
                .doesNotContain(".bible-secondary-sections", "border-top: 2px solid var(--primary)", "--workbench-content-width); }");
        assertThat(outline)
                .contains(".detail-canvas {\n  width: min(var(--workbench-content-width), 100%);")
                .contains(".editor-form {\n  width: min(var(--workbench-content-width), 100%);");
        assertThat(generate)
                .contains(".workflow-review-summary, .workflow-message-panel { width: min(var(--workbench-content-width), calc(100% - 48px));");

        System.out.println("长文本可读宽度检查通过：正文、设定、大纲和审稿结果均限制在共享可读宽度内");
    }

    @Test
    void shouldKeepManagementPagesFullWidth() throws IOException {
        String theme = read("novel-agent-web/src/assets/theme.css");
        String setup = read("novel-agent-web/src/views/SetupView.vue");
        String project = read("novel-agent-web/src/views/ProjectView.vue");

        System.out.printf("管理页全宽检查：theme=%d, setup=%d, project=%d%n", theme.length(), setup.length(), project.length());

        assertThat(theme).contains(".editor-page-shell { width: 100%; min-width: 0; }");
        assertThat(setup).contains("<div class=\"character-editor management-page\">");
        assertThat(project).contains("<div class=\"proj-wrap editor-page-shell\">");

        System.out.println("管理页全宽检查通过：角色库和项目设置继续使用可用空间，不套用正文窄栏");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
