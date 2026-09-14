package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiRootOutlineUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldOfferRootOutlineDraftPreviewWhenBookIsMissing() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts"));
        String controller = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/NovelPlanningController.java"));

        assertThat(view)
                .contains("const hasBook = computed")
                .contains("v-if=\"!hasBook\"")
                .contains("<el-empty")
                .contains("description=\"暂无故事总纲\"")
                .contains("先创建全书总纲，再按创作进度逐步生成下一卷和下一章。")
                .contains("AI 生成总纲")
                .contains("function openRootGeneration()")
                .contains("class=\"ai-generation-drawer\"")
                .contains(":size=\"generationDrawerSize\"")
                .contains(":close-on-click-modal=\"false\"")
                .contains(":close-on-press-escape=\"false\"")
                .contains("title=\"生成故事总纲\"")
                .contains("label=\"补充创作要求\"")
                .contains("<el-input")
                .contains("生成")
                .contains("rootGenerationStep === 'preview'")
                .contains("label=\"项目标题\"")
                .contains(":model-value=\"projectStore.active?.title ?? ''\"")
                .contains("readonly")
                .contains("v-model=\"rootDraft.summary\"")
                .contains("重新生成")
                .contains("确认")
                .contains("rootGenerationStep.value = 'preview'")
                .contains("rootGenerationStep.value = 'config'")
                .contains("async function generateRootDraft()")
                .contains("await generateRootOutline(projectCode, { requirement })")
                .contains("const payload = draft.payload")
                .contains("rootDraft.draftId = draft.draftId")
                .contains("async function confirmRootDraft()")
                .contains("await confirmRootOutline(projectCode")
                .contains("await loadOutline(projectCode)")
                .doesNotContain("v-model=\"rootDraft.title\"")
                .doesNotContain("payload.title")
                .doesNotContain("title: rootDraft.title")
                .doesNotContain("rootDraft.title = '全书总纲'")
                .doesNotContain("rootDraft.summary = rootGenerationForm")
                .doesNotContain("if (!projectCode || !requirement)")
                .doesNotContain("手动创建")
                .doesNotContain("尚未生成总纲")
                .doesNotContain("generateBookOutline")
                .doesNotContain("AI 生成世界观");
        assertThat(api)
                .contains("export const generateRootOutline")
                .contains("export const confirmRootOutline")
                .doesNotContain("export const generateBookOutline");
        assertThat(controller)
                .contains("@PostMapping(\"/outlines/root/generate\")")
                .contains("@PostMapping(\"/outlines/root/confirm\")")
                .doesNotContain("/outlines/book")
                .doesNotContain("generateBookOutline")
                .doesNotContain("updateBookOutline");

        System.out.println("AiRootOutlineUiContractTest verified no-BOOK empty state and Draft preview before root confirmation");
    }
}
