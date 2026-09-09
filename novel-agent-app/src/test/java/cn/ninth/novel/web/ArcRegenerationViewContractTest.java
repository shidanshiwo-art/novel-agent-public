package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArcRegenerationViewContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeArcRegenerationAsFixedChapterDraftAndUpdateFlow() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts"));

        assertThat(view)
                .contains("AI 重新生成")
                .contains("openSelectedArcRegeneration")
                .contains("ai-regeneration-dialog")
                .contains("arcRegenerationDraft.chapterNumber")
                .contains("v-model=\"arcRegenerationDraft.title\"")
                .contains("v-model=\"arcRegenerationDraft.summary\"")
                .contains("await generateArcRegeneration(projectCode, node.nodeCode")
                .contains("await confirmArcRegeneration(projectCode, node.nodeCode")
                .contains("await loadOutline(projectCode, node.nodeCode)")
                .doesNotContain("v-model=\"arcRegenerationDraft.startChapter\"")
                .doesNotContain("v-model=\"arcRegenerationDraft.endChapter\"");
        assertThat(api)
                .contains("export const generateArcRegeneration")
                .contains("export const confirmArcRegeneration")
                .contains("/outlines/nodes/${nodeCode}/regenerate")
                .contains("/outlines/nodes/${nodeCode}/regenerate/confirm");

        System.out.println("ArcRegenerationViewContractTest verified fixed chapter Draft and existing-node confirmation");
    }
}
