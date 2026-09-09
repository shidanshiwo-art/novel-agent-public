package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineViewCrudContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUsePlanningCrudApisWithEmptyInitialState() throws IOException {
        String view = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue")
        );

        assertThat(view)
                .contains("const tree = reactive<WorkbenchTreeNode[]>([])")
                .contains("@click=\"saveNode\"")
                .contains("await updateOutlineNode(projectCode, node.nodeCode")
                .contains("await createOutlineNode(projectCode")
                .contains("await deleteOutlineNode(projectCode, node.nodeCode)")
                .contains("await reorderOutlineNode(projectCode, node.nodeCode")
                .contains("async function refreshWorkspace()")
                .contains("listOutlineNodes(projectCode)")
                .doesNotContain("ChapterPlan")
                .doesNotContain("chapterPlan")
                .doesNotContain("@click=\"noop\"")
                .doesNotContain("function noop()")
                .doesNotContain("当前视图为布局预览")
                .doesNotContain("nodeCode: 'BOOK_001'")
                .doesNotContain("outlineNodeCode: 'ARC_001'");

        System.out.println(
                "OutlineViewCrudContractTest verified real outline CRUD calls, refresh queries and empty initial state"
        );
    }
}
