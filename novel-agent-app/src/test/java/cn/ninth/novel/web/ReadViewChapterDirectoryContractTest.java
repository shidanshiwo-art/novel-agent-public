package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadViewChapterDirectoryContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldMapReadDataToTheSharedChapterDirectory() throws IOException {
        String view = read("novel-agent-web/src/views/ReadView.vue");
        String directory = read("novel-agent-web/src/components/workbench/ChapterDirectory.vue");

        System.out.printf("ReadView 公共目录映射检查：view=%d, directory=%d%n", view.length(), directory.length());

        assertThat(view)
                .contains(
                        "<ChapterDirectory",
                        "v-model:keyword=\"keyword\"",
                        ":volumes=\"chapterDirectoryVolumes\"",
                        "@toggle-book=\"toggleBook\"",
                        "@toggle-volume=\"toggleVolume\"",
                        "@select-chapter=\"selectChapter\"",
                        "@chapter-contextmenu=\"openChapterMenu\"",
                        "const filteredVolumeGroups = computed(() =>",
                        "const showSingleDefaultVolumeFlat = computed(() =>",
                        "const bookExpanded = ref(true)",
                        "const expandedVolumeCodes = ref(new Set<string>())",
                        "metaLabel: statusLabel(chapter.status)"
                )
                .doesNotContain("<el-tree", "class=\"chapter-directory\"", "formatWords(");
        assertThat(directory)
                .contains(
                        "<span class=\"node-prefix\">故事总纲 ·</span>",
                        "formatVolumeLabel(volume.sequenceNo)",
                        "chapterLabel(chapter)",
                        "class=\"directory-node volume-row\"",
                        "class=\"directory-node chapter-row\"",
                        "chapter-row--active",
                        "<small v-if=\"chapter.metaLabel\" class=\"chapter-row-status\">{{ chapter.metaLabel }}</small>",
                        "selectedChapterNumber",
                        "return chapter.title ? `第 ${number} 章 ${chapter.title}` : `第 ${number} 章`",
                        ".directory-tree > .directory-children { padding-left: 12px; }",
                        ".volume-group > .directory-children,",
                        ".chapter-row { padding-left: 8px; }",
                        ".chapter-row--active {"
                )
                .doesNotContain("第 ${number} 章 ·", "chapter.secondary");

        System.out.println("ReadView 公共目录映射检查通过：后端卷/章数据在页面映射后交给共享目录渲染");
    }

    @Test
    void shouldUseRealVolumeDataWhenWeakeningTheDefaultVolumeUi() throws IOException {
        String view = read("novel-agent-web/src/views/ReadView.vue");

        System.out.printf("ReadView 默认卷弱化检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "const group = volumeGroups.value[0]",
                        "node.nodeKind === 'VOLUME' && node.nodeCode === group.volumeCode",
                        "volume?.sequenceNo === 1",
                        "&& !volume.summary?.trim()"
                )
                .doesNotContain("fakeVolume", "defaultVolume = {");

        System.out.println("ReadView 默认卷弱化契约通过：仅对后端真实卷结构隐藏卷标题，不补造目录节点");
    }

    @Test
    void shouldRenderChapterRowsWithInlineStatusMetadata() throws IOException {
        String directory = read("novel-agent-web/src/components/workbench/ChapterDirectory.vue");

        System.out.printf("ReadView 章节行检查：directory=%d%n", directory.length());

        assertThat(directory)
                .contains(
                        "<div class=\"chapter-node-copy\">",
                        "<strong>{{ chapterLabel(chapter) }}</strong>",
                        "<small v-if=\"chapter.metaLabel\" class=\"chapter-row-status\">{{ chapter.metaLabel }}</small>",
                        ".chapter-node-copy { min-width: 0; display: flex; flex: 1; align-items: baseline; gap: 8px; }",
                        ".chapter-row-status { flex: 0 0 auto;",
                        "white-space: nowrap;"
                )
                .doesNotContain("chapter-index", "chapter-arrow", "chapter-badge", "row-arrow", "chapter.secondary", "chapter.status");

        System.out.println("ReadView 章节行检查通过：完整章节标题与字数/状态副信息由共享目录统一呈现");
    }

    @Test
    void shouldRefreshDirectoryAfterDeletingChapter() throws IOException {
        String view = read("novel-agent-web/src/views/ReadView.vue");
        int deleteStart = view.indexOf("async function removeChapter() {");
        int deleteEnd = view.indexOf("async function selectChapter", deleteStart);

        System.out.printf("ReadView 删除同步检查：removeChapter=%d-%d%n", deleteStart, deleteEnd);

        assertThat(view.substring(deleteStart, deleteEnd))
                .contains("await deleteChapter(projectCode, deleted)", "await refresh()")
                .doesNotContain("chapters.value = chapters.value.filter", "volumeGroups.value = volumeGroups.value.map");
        assertThat(view)
                .contains("@click=\"refreshChapterList\"")
                .contains("watch(() => projectStore.active?.projectCode, () => {")
                .contains("void refresh(true).catch((error) => {")
                .doesNotContain("function selectChapter(chapterNumber: number) {\n  refresh", "function goAdjacent(offset: -1 | 1) {\n  refresh");

        System.out.println("ReadView 删除同步契约通过：删除后重新拉取目录，章节切换和相邻章节不触发 refresh");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
