package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineDeleteReorderUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseMessageBoxForDeleteAndSameLevelMoveMenu() throws IOException {
        String view = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue")
        );
        String api = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts")
        );

        assertThat(view)
                .contains("确定删除「${node.title}」？")
                .contains("删除后无法恢复。")
                .contains("<el-tooltip")
                .contains("command=\"delete\"")
                .contains("ElMessageBox.confirm")
                .contains("请先删除下级大纲")
                .contains("command=\"move-up\"")
                .contains(">上移</el-dropdown-item>")
                .contains("command=\"move-down\"")
                .contains(">下移</el-dropdown-item>")
                .contains("reorderOutlineNode")
                .doesNotContain("window.confirm");
        assertThat(api)
                .contains("export const reorderOutlineNode")
                .contains("/outlines/nodes/${nodeCode}/reorder")
                .contains("http.post");

        System.out.println("OutlineDeleteReorderUiContractTest verified MessageBox delete, disabled Tooltip and sibling move menu using POST reorder API");
    }

    @Test
    void shouldUsePopconfirmInsteadOfWindowConfirmForChapterDelete() throws IOException {
        String readView = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue")
        );

        assertThat(readView)
                .contains("<el-popconfirm")
                .contains("删除后无法恢复。")
                .contains("@confirm=\"removeChapter\"")
                .doesNotContain("window.confirm");

        System.out.println("OutlineDeleteReorderUiContractTest verified chapter deletion also uses Element Plus Popconfirm");
    }
}
