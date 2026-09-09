package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadViewEditingContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepChapterEditorVisibleWithOneSaveAction() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));

        assertThat(view)
                .contains("<ChapterToolbar")
                .contains("<ChapterCanvas v-if=\"selectedChapter\"")
                .contains("v-model=\"editTitle\"")
                .contains("{{ saving ? '保存中…' : '保存' }}")
                .contains("watch(selectedNumber")
                .doesNotContain("class=\"paper-kicker\"")
                .doesNotContain("class=\"reading-footer\"")
                .doesNotContain("readingProgress")
                .doesNotContain("v-if=\"editing\"");
    }

    @Test
    void shouldBindAndSaveEditableChapterTitle() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));

        assertThat(view)
                .contains("v-model=\"editTitle\"")
                .contains("title: editTitle.value")
                .contains("editTitle.value = selectedChapter.value?.title ?? ''")
                .contains("if (!editTitle.value.trim())");
    }

    @Test
    void shouldSwitchChapterContentFromLoadedChaptersWithoutFetching() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));

        System.out.println("ReadView 正文切换检查：selectedChapter 使用本地 chapters，selectedNumber 监听只同步编辑器字段");

        assertThat(view)
                .contains("const selectedChapter = computed(() => chapters.value.find((chapter) => chapter.chapterNumber === selectedNumber.value) ?? null)")
                .contains("editTitle.value = selectedChapter.value?.title ?? ''")
                .contains("editContent.value = selectedChapter.value?.content ?? ''")
                .contains("const hasLocalEdits = computed(() =>")
                .contains("editTitle.value !== chapter.title")
                .contains("editContent.value !== chapter.content")
                .contains("'当前章节还有未保存修改'")
                .contains("confirmButtonText: '放弃修改并切换'")
                .contains("cancelButtonText: '继续编辑'")
                .contains("function selectChapter(chapterNumber: number) {")
                .contains("selectedNumber.value = chapterNumber")
                .contains("function goAdjacent(offset: -1 | 1) {\n  const target = chapters.value[selectedIndex.value + offset]\n  if (target) void selectChapter(target.chapterNumber)\n}")
                .doesNotContain("getChapter(", "watch(selectedNumber, async");
    }

    @Test
    void shouldUseRouteChapterOnlyForInitialDeepLink() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        int requestedStart = view.indexOf("const requested = Number(route.query.chapter)");
        int requestedEnd = view.lastIndexOf("const requested = Number(route.query.chapter)");
        int selectStart = view.indexOf("async function selectChapter(chapterNumber: number)");
        int selectEnd = view.indexOf("\n}\nfunction goAdjacent", selectStart);
        int adjacentStart = view.indexOf("function goAdjacent(offset: -1 | 1)");
        int adjacentEnd = view.indexOf("\n}\nfunction replaceChapter", adjacentStart);

        System.out.printf(
                "ReadView 路由切章隔离检查：requested=%d/%d, select=%d, adjacent=%d%n",
                requestedStart,
                requestedEnd,
                selectStart,
                adjacentStart
        );

        assertThat(view)
                .contains("const requested = Number(route.query.chapter)")
                .doesNotContain(
                        "router.replace({ query:",
                        "router.push({ query:",
                        "useRouter"
                );
        assertThat(requestedStart).isGreaterThanOrEqualTo(0);
        assertThat(requestedStart).isEqualTo(requestedEnd);
        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        assertThat(selectEnd).isGreaterThan(selectStart);
        assertThat(adjacentStart).isGreaterThan(selectEnd);
        assertThat(adjacentEnd).isGreaterThan(adjacentStart);
        assertThat(view.substring(selectStart, selectEnd))
                .contains("selectedNumber.value = chapterNumber")
                .doesNotContain("route.query.chapter", "router.");
        assertThat(view.substring(adjacentStart, adjacentEnd))
                .contains("void selectChapter(target.chapterNumber)")
                .doesNotContain("route.query.chapter", "router.");

        System.out.println("ReadView 路由切章隔离契约通过：深链接只用于首次定位，目录和相邻章节均只更新 selectedNumber");
    }

    @Test
    void shouldKeepSelectedNumberWatcherLimitedToEditorValues() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        int watcherStart = view.indexOf("watch(selectedNumber, () => {");
        int watcherEnd = view.indexOf("\n})", watcherStart);
        String watcher = view.substring(watcherStart, watcherEnd);

        System.out.printf("ReadView watcher 检查：start=%d, end=%d，selectedNumber 仅同步编辑值%n", watcherStart, watcherEnd);

        assertThat(watcher)
                .contains(
                        "editTitle.value = selectedChapter.value?.title ?? ''",
                        "editContent.value = selectedChapter.value?.content ?? ''"
                )
                .doesNotContain("refresh(", "listChapters", "listChaptersByVolume", "route.query.chapter");
        assertThat(view)
                .doesNotContain("watch(route.query.chapter", "watch(() => route.query.chapter", "watch(() => route.query");
        assertThat(watcherStart).isGreaterThanOrEqualTo(0);
        assertThat(watcherEnd).isGreaterThan(watcherStart);
    }

    @Test
    void shouldProtectChapterSwitchWhenLocalEditsExist() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        int selectStart = view.indexOf("async function selectChapter(chapterNumber: number)");
        int selectEnd = view.indexOf("async function removeChapter", selectStart);
        if (selectEnd < 0) {
            selectEnd = view.indexOf("function goAdjacent", selectStart);
        }
        String selectFunction = view.substring(selectStart, selectEnd);

        System.out.println("ReadView 未保存切章检查：本地标题或正文有修改时必须先确认");

        assertThat(view)
                .contains(
                        "const hasLocalEdits = computed(() =>",
                        "editTitle.value !== chapter.title",
                        "editContent.value !== chapter.content",
                        "@select-chapter=\"selectChapter\"",
                        "if (target) void selectChapter(target.chapterNumber)");
        assertThat(selectFunction)
                .contains(
                        "if (hasLocalEdits.value)",
                        "await ElMessageBox.confirm(",
                        "'当前章节还有未保存修改'",
                        "confirmButtonText: '放弃修改并切换'",
                        "cancelButtonText: '继续编辑'",
                        "} catch {\n      return",
                        "selectedNumber.value = chapterNumber")
                .doesNotContain("refresh(", "listChapters", "listChaptersByVolume");

        System.out.println("ReadView 未保存切章契约通过：继续编辑停留当前章节，确认后才切换本地 selectedNumber");
    }

    @Test
    void shouldCloseChapterMenuWhenClickingOutsideIt() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));

        assertThat(view)
                .contains("document.addEventListener('click', closeChapterMenu)")
                .contains("document.removeEventListener('click', closeChapterMenu)")
                .contains("class=\"chapter-menu\"")
                .contains("@click.stop");
    }
}
