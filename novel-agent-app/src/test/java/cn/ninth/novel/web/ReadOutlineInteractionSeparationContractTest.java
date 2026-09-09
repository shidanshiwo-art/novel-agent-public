package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOutlineInteractionSeparationContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepReadAndOutlineInteractionResponsibilitiesSeparate() throws IOException {
        String readView = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        String outlineView = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        System.out.printf(
                "Read/Outline 交互职责隔离检查：read=%d, outline=%d%n",
                readView.length(),
                outlineView.length()
        );

        assertThat(readView)
                .contains(
                        "<ChapterDirectory",
                        "function toggleBook()",
                        "function toggleVolume(volumeCode: string)",
                        "async function selectChapter(chapterNumber: number)",
                        "function goAdjacent(offset: -1 | 1)"
                )
                .doesNotContain(
                        "<el-tree",
                        "createOutlineNode",
                        "updateOutlineNode",
                        "deleteOutlineNode",
                        "reorderOutlineNode",
                        "<OutlineTree"
                );

        assertThat(outlineView)
                .contains(
                        "<aside class=\"outline-directory\">",
                        "<el-tree",
                        "function selectNode(node: WorkbenchTreeNode)",
                        "function handleNodeCommand(command: string | number, node: WorkbenchTreeNode)",
                        "createOutlineNode",
                        "updateOutlineNode",
                        "deleteOutlineNode",
                        "reorderOutlineNode"
                )
                .doesNotContain(
                        "<ChapterDirectory",
                        "function goAdjacent(offset: -1 | 1)",
                        "async function selectChapter(chapterNumber: number)",
                        "<ReadOutlineTree"
                );

        System.out.println("Read/Outline 交互职责隔离契约通过：Read 负责阅读目录导航，Outline 负责树形结构编辑，共享展示组件但不共享业务职责");
    }

    @Test
    void shouldKeepLegacyDirectoryStylesOutOfTheViews() throws IOException {
        String readView = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        String outlineView = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        System.out.println("Read/Outline 旧样式清理检查：chapter-index、row-arrow、tree-range、旧标题 wrapper");

        assertThat(readView)
                .doesNotContain(
                        "chapter-index",
                        "row-arrow",
                        "router.push('/planning'",
                        "router.replace({ query:"
                );
        assertThat(outlineView)
                .doesNotContain(
                        "tree-range",
                        "range-editor",
                        "chapterRangeLocked",
                        "chapterRangeLockReason",
                        "outline-title-input",
                        ".editor-form :deep(.el-form-item:first-child .el-input__wrapper)"
                );

        assertThat(outlineView)
                .contains(".outline-title-editor :deep(.el-input__wrapper)")
                .contains(".outline-title-editor :deep(.el-input__wrapper.is-focus)");

        System.out.println("Read/Outline 旧样式清理契约通过：已移除旧目录节点、旧标题 wrapper、章节范围 UI 和隐藏导航行为");
    }
}
