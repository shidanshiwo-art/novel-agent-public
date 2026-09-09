package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiSplitOutlineUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeRollingNextOutlineGenerationWithoutCountControls() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts"));

        assertThat(view)
                .contains("command=\"generate-next\"")
                .contains("command === 'generate-next'")
                .contains("function openNextGeneration(node")
                .contains("class=\"ai-next-dialog\"")
                .contains("createNextVolume(projectCode, node.nodeCode)")
                .contains("generateVolumeOutline(projectCode, parent.nodeCode")
                .contains("confirmVolumeOutline(projectCode, parent.nodeCode")
                .contains("generateNextOutline(projectCode, parent.nodeCode")
                .contains("confirmNextOutline(projectCode, parent.nodeCode")
                .contains("生成下一卷")
                .contains("生成下一章")
                .contains("生成卷纲")
                .contains("VOLUME_PLAN")
                .contains("系统将保留当前卷的编号和章节范围")
                .contains("const selectedNodeNextGenerationButtonLabel")
                .contains("canCreateChild(data)")
                .contains("canManuallyCreateChild(data)")
                .contains("function isActiveVolume(node")
                .contains("const nextGenerationActionLabel")
                .contains("const nextDraftStructureLabel")
                .contains("return `卷${toChineseNumber(nextDraft.sequenceNo)}`")
                .contains("return `第${nextDraft.startChapter}章`")
                .contains("const payload = draft.payload")
                .contains("nextDraftId.value = draft.draftId")
                .contains("v-model=\"nextDraft.title\"")
                .contains("v-model=\"nextDraft.summary\"")
                .contains("重新生成")
                .contains("确认写入")
                .doesNotContain(
                        "preferredCount",
                        "生成数量",
                        "AI {{ nextGenerationLabel(data) }}",
                        "AI {{ selectedNodeNextGenerationLabel }}",
                        "split-by-ai",
                        "ai-split",
                        "splitDraft",
                        "generateChildOutlines",
                        "confirmChildOutlines",
                        "v-model=\"nextDraft.nodeCode\"",
                        "v-model=\"nextDraft.sequenceNo\"",
                        "v-model=\"nextDraft.startChapter\"",
                        "v-model=\"nextDraft.endChapter\""
                );
        assertThat(api)
                .contains("export const generateNextOutline")
                .contains("export const confirmNextOutline")
                .contains("export const createNextVolume")
                .contains("export const generateVolumeOutline")
                .contains("export const confirmVolumeOutline")
                .contains("/outlines/volumes/${volumeNodeCode}/generate")
                .contains("/outlines/volumes/${volumeNodeCode}/confirm")
                .contains("/outlines/${bookNodeCode}/volumes/create")
                .contains("/children/generate")
                .contains("/children/confirm")
                .doesNotContain("generateChildOutlines", "confirmChildOutlines", "preferredCount");

        System.out.println("Outline 滚动式生成 UI 契约已验证：BOOK 显示卷序号，VOLUME 只生成下一章，无数量控件");
    }
}
