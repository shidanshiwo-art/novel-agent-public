package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PageHeaderContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseTheSameChineseHeaderStructureAcrossSettingsAndCharacters() throws IOException {
        String setup = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));

        System.out.printf("设定/角色页头契约检查：source=%d%n", setup.length());

        assertThat(setup)
                .contains(
                        "<header class=\"editor-page-header\">",
                        "<h1>设定</h1>",
                        "保持故事的世界边界和关键规则一致。",
                        "<h1>角色库</h1>",
                        "查看故事里已经存在的人物，快速掌握他们的定位与状态。",
                        "class=\"page-header-actions\""
                )
                .doesNotContain(
                        "STORY BIBLE",
                        "CHARACTERS",
                        "EDIT CHARACTER",
                        "NEW CHARACTER"
                );

        System.out.println("设定/角色页头契约通过：中文标题、辅助说明和右侧操作结构统一");
    }

    @Test
    void shouldUseChineseOutlineAndProjectPageHeaders() throws IOException {
        String outline = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String project = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ProjectView.vue"));

        System.out.printf("大纲/项目页头契约检查：outline=%d, project=%d%n", outline.length(), project.length());

        assertThat(outline)
                .contains(
                        "<h1>故事大纲</h1>",
                        "按故事进度组织内容，逐步推进每一卷和每一章。",
                        "class=\"directory-head-actions\"",
                        "@click=\"refreshWorkspace\""
                )
                .doesNotContain(
                        "OUTLINE WORKBENCH",
                        "CURRENT NODE",
                        "ROOT OUTLINE",
                        "AI NEXT OUTLINE"
                );
        assertThat(project)
                .contains(
                        "<header class=\"project-page-head editor-page-header\">",
                        "<h1>项目设置</h1>",
                        "创建作品、加载已有项目，或调整当前项目的写作规模。"
                )
                .doesNotContain("PROJECT SETTINGS", "WORKSPACE", "CURRENT PROJECT");

        System.out.println("大纲/项目页头契约通过：移除非产品必需的英文 eyebrow，并保留中文说明与操作区");
    }
}
