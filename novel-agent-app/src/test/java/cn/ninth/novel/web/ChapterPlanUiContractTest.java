package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterPlanUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldRemoveChapterPlanUiAndDataDependencyFromOutlineView() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        assertThat(view)
                .contains("class=\"detail-primary-actions\"")
                .contains("class=\"detail-more\"")
                .contains("AI 生成{{ selectedNodeNextKindLabel }}")
                .contains("+ 手动添加{{ selectedNodeNextKindLabel }}")
                .contains("v-else-if=\"selectedNode.nodeKind === 'ARC'\"")
                .contains("去生成")
                .contains("router.push({ path: '/generate', query: { chapter: node.startChapter } })")
                .doesNotContain("ChapterPlan")
                .doesNotContain("chapterPlan")
                .doesNotContain("chapterPlans")
                .doesNotContain("class=\"node-next-actions\"")
                .doesNotContain("NEXT STEP")
                .doesNotContain("selectedNodeCanOpenChapterPlans")
                .doesNotContain("selectedNodeChapterSlots")
                .doesNotContain("chapterPlansVisible")
                .doesNotContain("chapterPlanDraftVisible")
                .doesNotContain("openChapterPlans")
                .doesNotContain("generateChapterPlan")
                .doesNotContain("confirmChapterPlan")
                .doesNotContain("updateChapterPlan")
                .doesNotContain("生成章纲")
                .doesNotContain("查看章纲");

        System.out.println("ChapterPlanUiContractTest verified OutlineView contains no ChapterPlan UI or data dependency");
    }
}
