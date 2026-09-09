package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineResponsiveUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseDesktopCompactAndTreeOnlyMobileBreakpoints() throws IOException {
        String view = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue")
        );

        assertThat(view)
                .contains("grid-template-columns: 320px minmax(0, 1fr)")
                .contains("@media (max-width: 1199px)")
                .contains("grid-template-columns: 260px minmax(0, 1fr)")
                .contains("@media (max-width: 767px)")
                .contains("  .outline-detail {")
                .contains("    display: none;")
                .contains("window.matchMedia('(max-width: 767px)')")
                .contains("if (isMobile.value) drawerVisible.value = true")
                .contains("<el-drawer v-model=\"drawerVisible\"")
                .doesNotContain("@media (max-width: 960px)")
                .doesNotContain("@media (max-width: 680px)")
                .doesNotContain(".outline-detail.is-empty");

        System.out.println("OutlineResponsiveUiContractTest verified >=1200 320px tree, 768-1199 260px tree, and <768 tree-only layout with editor Drawer");
    }
}
