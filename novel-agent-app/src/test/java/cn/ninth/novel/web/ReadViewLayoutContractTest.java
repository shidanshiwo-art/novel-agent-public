package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadViewLayoutContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldPlaceDirectoryOnTheLeftAndReadingStageOnTheRight() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        String directory = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/components/workbench/ChapterDirectory.vue"));

        System.out.printf(
                "ReadView 目录宽度检查：view=%d, desktop=clamp(340px, 25vw, 380px)%n",
                view.length()
        );

        assertThat(view)
                .contains("grid-template-columns: clamp(340px, 25vw, 380px) minmax(0, 1fr)")
                .contains(".reading-stage { grid-column: 2; grid-row: 1;")
                .contains("@media (max-width: 767px)")
                .contains("display: flex; flex-direction: column; overflow: hidden")
                .doesNotContain("grid-template-columns: 320px minmax(0, 1fr)")
                .doesNotContain("grid-template-columns: 280px minmax(0, 1fr)")
                .doesNotContain("grid-template-columns: minmax(0, 1fr) 286px")
                .doesNotContain(".chapter-directory { grid-column: 2; grid-row: 1;");

        assertThat(directory)
                .contains(".chapter-directory { min-width: 0; min-height: 0; display: flex;")
                .contains("border-right: 1px solid var(--border)")
                .contains(".chapter-directory { flex: 0 0 310px;");

        System.out.println("ReadView 目录宽度检查通过：页面保留左侧目录与右侧阅读区，目录基础样式集中在公共组件");
    }
}
