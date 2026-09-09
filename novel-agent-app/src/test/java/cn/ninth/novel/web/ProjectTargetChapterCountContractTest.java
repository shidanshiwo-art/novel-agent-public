package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTargetChapterCountContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeTargetChapterCountAdjustmentEntry() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-web/src/views/ProjectView.vue"));
        String app = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-web/src/App.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-web/src/api/project.ts"));

        System.out.printf("预计章节数调整入口检查：view=%d app=%d api=%d%n",
                view.length(), app.length(), api.length());
        assertThat(view)
                .contains("预计章节数", "调整预计章节数", "targetChapterDialogVisible", "saveTargetChapterCount")
                .doesNotContain("<input", "<button");
        assertThat(app)
                .contains("项目设置", "openProjectSettings", "router.push('/project')");
        assertThat(api)
                .contains("updateTargetChapterCount", "/target-chapter-count", "http.post")
                .doesNotContain("http.put", "http.patch");
        System.out.println("项目页已提供 Element Plus 调整入口，前端使用 POST 同步章节上限");
    }
}
