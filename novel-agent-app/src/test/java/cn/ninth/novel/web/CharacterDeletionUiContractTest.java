package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterDeletionUiContractTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldCallOfficialDeleteApiWithBothCodes() throws IOException {
        String api = Files.readString(ROOT.resolve("novel-agent-web/src/api/project.ts"));
        assertThat(api).containsPattern("export const deleteCharacter = \\(projectCode: string, characterCode: string\\) =>\\s*http\\.delete<any, void>\\(`/v1/novels/projects/\\$\\{projectCode}/characters/\\$\\{characterCode}`\\)");
        System.out.println("人物删除 API 使用 HTTP DELETE，路径包含项目和人物编码");
    }

    @Test
    void shouldConfirmDeletionUsingElementPlusInExistingCharacterDrawer() throws IOException {
        String view = Files.readString(ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String drawer = view.substring(view.indexOf("<el-drawer"), view.indexOf("</el-drawer>"));
        assertThat(drawer)
                .containsPattern("<el-button[^>]*type=\"danger\"[^>]*:icon=\"Delete\"[^>]*aria-label=\"删除角色\"[^>]*@click=\"removeCharacter\"[^>]*/>")
                .contains("<el-tooltip", "v-if=\"editingCharacterCode\"")
                .contains(":loading=\"deletingChar\"", ":disabled=\"savingChar\"")
                .doesNotContain("<button");
        assertThat(view)
                .contains("await ElMessageBox.confirm(", "确认删除角色“${character.name}”吗？此操作无法撤销。")
                .contains("await apiDeleteCharacter(projectCode, characterCode)")
                .doesNotContain("window.confirm");
        assertThat(view.indexOf("await ElMessageBox.confirm("))
                .isLessThan(view.indexOf("await apiDeleteCharacter(projectCode, characterCode)"));
        System.out.println("已有角色 Drawer 提供 Element Plus danger 按钮，先确认后请求删除");
    }

    @Test
    void shouldClearPreviousProjectCharactersBeforeLoadingAnotherProject() throws IOException {
        String view = Files.readString(ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String watcher = view.substring(view.indexOf("watch("), view.indexOf("{ immediate: true }"));
        assertThat(watcher).contains("characterCreatorOpen.value = false", "editingCharacterCode.value = null");
        assertThat(watcher.indexOf("characters.value = []")).isGreaterThanOrEqualTo(0)
                .isLessThan(watcher.indexOf("if (projectCode)"));
        System.out.println("项目切换立即清空旧角色和编辑目标，列表加载失败也不会沿用其他项目人物");
    }

    @Test
    void shouldRefreshAfterClosingAndClearingSelectionBeforeSuccessMessage() throws IOException {
        String view = Files.readString(ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String removal = view.substring(view.indexOf("async function removeCharacter()"), view.indexOf("const roleNames"));
        assertThat(removal).doesNotContain("characters.value.filter", "characters.value.splice");
        int deletion = removal.indexOf("await apiDeleteCharacter(projectCode, characterCode)");
        int close = removal.indexOf("characterCreatorOpen.value = false");
        int clear = removal.indexOf("editingCharacterCode.value = null");
        int refresh = removal.indexOf("await loadCharacters(projectCode)");
        int success = removal.indexOf("ElMessage.success");
        assertThat(deletion).isGreaterThan(0).isLessThan(close);
        assertThat(close).isLessThan(clear);
        assertThat(clear).isLessThan(refresh);
        assertThat(refresh).isLessThan(success);
        System.out.println("删除成功后依次关闭详情、清空选择、刷新列表、提示成功；失败不本地移除角色");
    }
}
