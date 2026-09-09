package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadViewTypographyContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseOneStableChineseUiFontStackForReadingContentAndChapterTitle() throws IOException {
        String read = read("novel-agent-web/src/views/ReadView.vue");
        String directory = read("novel-agent-web/src/components/workbench/ChapterDirectory.vue");
        String title = read("novel-agent-web/src/components/workbench/ChapterTitle.vue");
        String canvas = read("novel-agent-web/src/components/workbench/ChapterCanvas.vue");
        String fontStack = "font-family: \"Microsoft YaHei\", \"PingFang SC\", \"Noto Sans CJK SC\", \"Noto Sans SC\", sans-serif;";

        System.out.printf("ReadView 字体栈检查：read=%d, directory=%d, title=%d, canvas=%d%n",
                read.length(), directory.length(), title.length(), canvas.length());

        assertThat(read).contains(fontStack);
        assertThat(directory)
                .contains(".directory-node {")
                .contains(".node-title {")
                .contains(".chapter-node-copy strong {")
                .contains(".node-prefix {");
        assertThat(title)
                .contains(".chapter-title {")
                .contains("font-size: 20px;");
        assertThat(canvas)
                .contains(fontStack)
                .contains("font-size: 16px; line-height: 1.9;")
                .contains("letter-spacing: normal;");

        assertThat(read + directory + title + canvas)
                .doesNotContain("font-size: 38px", "line-height: 2.15", "font-size: 30px; line-height: 1.3; letter-spacing: 0;", "letter-spacing: .035em;")
                .doesNotContain("Songti SC", "STSong", "Source Han Serif SC");

        System.out.println("ReadView 字体栈检查通过：目录、章节标题和正文画布统一使用中文 UI 字体栈");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
