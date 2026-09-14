package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationDrawerContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseWideClosableProtectedDrawersForOutlineAiGeneration() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));

        System.out.printf("Outline AI Drawer 契约检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "class=\"ai-generation-drawer\"",
                        ":size=\"generationDrawerSize\"",
                        ":close-on-click-modal=\"false\"",
                        ":close-on-press-escape=\"false\"",
                        ":before-close=\"guardRootGenerationClose\"",
                        ":before-close=\"guardNextGenerationClose\"",
                        ":before-close=\"guardArcRegenerationClose\"",
                        "{{ generationDrawerExpanded ? '恢复默认宽度' : '一键展开' }}",
                        "const generationDrawerSize = computed(() =>",
                        "if (generationDrawerExpanded.value) return 'max(80vw, 720px)'",
                        "return isMobile.value ? '100vw' : '720px'",
                        "min-width: min(680px, 100vw)",
                        "function guardRootGenerationClose(done: () => void)",
                        "function guardNextGenerationClose(done: () => void)",
                        "function guardArcRegenerationClose(done: () => void)",
                        "AI 正在处理中，请等待完成"
                )
                .doesNotContain(
                        "class=\"root-generation-dialog\"",
                        "class=\"ai-next-dialog\"",
                        "class=\"ai-regeneration-dialog\""
                );

        long drawerCount = view.lines().filter(line -> line.contains("class=\"ai-generation-drawer\"")).count();
        System.out.printf("Outline AI Drawer 契约通过：统一抽屉=%d 个，默认 720px，展开至少 720px 且不小于 80vw%n", drawerCount);
        assertThat(drawerCount).isEqualTo(3);
    }

    @Test
    void shouldUseWideProtectedDrawersForSetupAiGenerationAndCharacterDrafts() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));

        System.out.printf("Setup AI Drawer 契约检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "class=\"ai-generation-drawer\"",
                        ":size=\"aiGeneratorDrawerSize\"",
                        ":close-on-click-modal=\"false\"",
                        ":close-on-press-escape=\"false\"",
                        ":before-close=\"guardBibleGeneratorClose\"",
                        ":before-close=\"guardCharacterGeneratorClose\"",
                        "{{ aiGeneratorDrawerExpanded ? '恢复默认宽度' : '一键展开' }}",
                        "const aiGeneratorDrawerSize = computed(() => aiGeneratorDrawerExpanded.value ? 'max(80vw, 720px)' : '720px')",
                        "min-width: min(680px, 100vw)",
                        "function guardBibleGeneratorClose(done: () => void)",
                        "function guardCharacterGeneratorClose(done: () => void)",
                        "AI 正在生成，请等待完成",
                        "人物草稿正在处理中，请等待完成"
                )
                .doesNotContain(
                        "class=\"story-bible-generator-dialog\"",
                        "class=\"character-generator-dialog\""
                );

        long drawerCount = view.lines().filter(line -> line.contains("class=\"ai-generation-drawer\"")).count();
        System.out.printf("Setup AI Drawer 契约通过：统一抽屉=%d 个，默认 720px、展开至少 720px 且不小于 80vw，故事设定与人物草稿均覆盖关闭保护%n", drawerCount);
        assertThat(drawerCount).isEqualTo(2);
    }

    @Test
    void shouldReuseDrawerWidthAndCloseProtectionForChapterPlanEditor() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        System.out.printf("Generate ChapterPlan Drawer 契约检查：view=%d%n", view.length());

        assertThat(view)
                .contains(
                        "class=\"ai-generation-drawer\"",
                        ":size=\"chapterPlanDrawerSize\"",
                        ":close-on-click-modal=\"false\"",
                        ":close-on-press-escape=\"false\"",
                        ":before-close=\"guardChapterPlanDrawerClose\"",
                        "{{ chapterPlanDrawerExpanded ? '恢复默认宽度' : '一键展开' }}",
                        "const chapterPlanDrawerSize = computed(()",
                        "chapterPlanDrawerExpanded.value ? 'max(80vw, 720px)' : '720px'",
                        "min-width: min(680px, 100vw)",
                        "function guardChapterPlanDrawerClose(done: () => void)",
                        "function requestCloseChapterPlanDrawer"
                );

        System.out.println("Generate ChapterPlan Drawer 契约通过：编辑章节计划复用 720px/max(80vw, 720px) 宽度规范并拦截处理中关闭");
        assertThat(view).doesNotContain("size=\"min(560px, 100%)\"");
    }
}
