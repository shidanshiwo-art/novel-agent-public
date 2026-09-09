package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterDerivedDataResyncUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeResyncActionOnlyForDirtyChapter() throws IOException {
        String api = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/api/project.ts")
        );
        String view = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue")
        );

        assertThat(api)
                .contains("resyncChapterDerivedData")
                .contains("/chapters/${chapterNumber}/resync");
        assertThat(view)
                .contains("selectedChapter.status === 'DIRTY'")
                .contains("正文已修改")
                .contains("更新章节记忆")
                .doesNotContain("derivedSyncFailed ? '重新更新' : '更新章节记忆'")
                .contains("正文已修改，更新章节记忆后后续章节才能使用最新内容。")
                .contains("ElMessage.error('章节记忆更新失败')")
                .contains("replaceChapter(updated)")
                .contains("derivedSyncFailed.value = updated.status === 'DIRTY'")
                .contains("resyncChapterDerivedData(projectCode, chapter.chapterNumber)")
                .contains("resyncing.value = false")
                .doesNotContain("重新同步派生数据", "DIRTY: '待同步'");
        System.out.println("ChapterDerivedDataResyncUiContractTest verified DIRTY-only resync UI and API wiring");
    }
}
