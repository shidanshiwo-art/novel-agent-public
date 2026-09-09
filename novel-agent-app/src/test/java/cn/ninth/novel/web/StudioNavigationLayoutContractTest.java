package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudioNavigationLayoutContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldRenderWorkspaceNavigationInSidebarInsteadOfTopNavigation() throws IOException {
        String app = read("novel-agent-web/src/App.vue");
        String readView = read("novel-agent-web/src/views/ReadView.vue");

        assertThat(app)
                .contains("class=\"studio-sidebar\"")
                .contains("path: '/setup', label: '设定'")
                .contains("path: '/read', label: '章节'")
                .contains(".studio-sidebar {")
                .doesNotContain("class=\"studio-nav\"");
        assertThat(readView).doesNotContain("class=\"tool-rail\"");
    }

    @Test
    void shouldKeepWorkspaceNavigationOnTheLeftInReadingMode() throws IOException {
        String app = read("novel-agent-web/src/App.vue");

        assertThat(app)
                .contains("<aside class=\"studio-sidebar\" aria-label=\"作品工具\"")
                .doesNotContain("<aside v-if=\"route.path !== '/read'\" class=\"studio-sidebar\"");
    }

    @Test
    void shouldDisableStudioMainScrollingForGenerateRoute() throws IOException {
        String app = read("novel-agent-web/src/App.vue");

        System.out.printf("Generate 父级滚动收口检查：app=%d%n", app.length());

        assertThat(app)
                .contains(
                        "'generate-mode': route.path === '/generate'",
                        ".studio-main { flex: 1; min-width: 0; min-height: 0; height: 100%; display: flex; flex-direction: column; overflow-y: auto;",
                        ".studio-main.generate-mode { overflow: hidden; }",
                        "<router-view v-slot=\"{ Component }\">"
                );

        System.out.println("Generate 父级滚动收口通过：Generate 路由关闭 studio-main 外层滚动，由 generation-page 承担唯一纵向滚动");
    }

    @Test
    void shouldProvideDefiniteHeightThroughStudioRouteView() throws IOException {
        String app = read("novel-agent-web/src/App.vue");

        System.out.printf("Generate 高度链路检查：app=%d%n", app.length());

        assertThat(app)
                .contains(
                        "<div class=\"studio-body\">",
                        "<router-view v-slot=\"{ Component }\">",
                        "<div class=\"studio-route-view\" :key=\"route.fullPath\">",
                        ".studio-body { flex: 1; height: 100%; min-height: 0; display: flex; }",
                        ".studio-main { flex: 1; min-width: 0; min-height: 0; height: 100%; display: flex; flex-direction: column; overflow-y: auto;",
                        ".studio-route-view { flex: 1; min-width: 0; min-height: 0; height: 100%; }"
                );

        System.out.println("Generate 高度链路通过：studio-body、studio-main、路由承载层均提供可继承高度和可收缩最小高度");
    }

    @Test
    void shouldExposeSettingsCharactersAndOutlineAsSeparateSidebarEntries() throws IOException {
        String app = read("novel-agent-web/src/App.vue");
        String router = read("novel-agent-web/src/router/index.ts");
        String setupView = read("novel-agent-web/src/views/SetupView.vue");

        assertThat(app)
                .contains("path: '/setup', label: '设定'")
                .contains("path: '/characters', label: '角色'")
                .contains("path: '/outline', label: '大纲'")
                .doesNotContain("path: '/planning', label: 'AI 规划'");
        assertThat(router)
                .contains("{ path: '/characters', component: SetupView, meta: { requiresProject: true } }")
                .contains("{ path: '/outline', component: OutlineView, meta: { requiresProject: true } }");
        assertThat(setupView)
                .contains("'/setup': 'bible'")
                .contains("'/characters': 'character'")
                .doesNotContain("'/outline': 'outline'")
                .doesNotContain("添加一章")
                .doesNotContain("保存章纲")
                .contains(".setup-tabs :deep(.el-tabs__header) { display: none;");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath)).replace("\r\n", "\n");
    }
}
