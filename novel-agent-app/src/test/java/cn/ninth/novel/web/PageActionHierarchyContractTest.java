package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PageActionHierarchyContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepOutlinePageActionsAtOnePrimaryLevel() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        System.out.println("大纲按钮层级契约检查：生成操作为主按钮，手动添加/保存/更多为次级入口");
        assertThat(view)
                .contains(
                        "<el-button type=\"primary\" @click=\"openSelectedNodeNextGeneration\">",
                        "plain @click=\"openSelectedNodeChild\"",
                        "<el-button text>更多</el-button>",
                        "<el-button plain :loading=\"savingNode\" @click=\"saveNode\">保存修改</el-button>"
                )
                .doesNotContain(
                        "text type=\"primary\" :loading=\"loading\" @click=\"refreshWorkspace\"",
                        "text type=\"primary\" @click=\"goToGeneration\""
                );
    }

    @Test
    void shouldUseElementPlusLayersForContextAndShortTasks() throws IOException {
        String outline = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String setup = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));

        System.out.println("浮层组件契约检查：上下文编辑使用 Drawer，短任务使用 Dialog");
        assertThat(outline)
                .contains("<el-drawer", "title=\"编辑节点\"", "<el-dialog", "title=\"生成故事总纲\"");
        assertThat(setup)
                .contains("<el-drawer", "title=\"AI 补充人物\"", "<el-dialog", ":title=\"hasConfirmedBible ? '调整故事设定' : '生成故事设定'\"");
    }
}
