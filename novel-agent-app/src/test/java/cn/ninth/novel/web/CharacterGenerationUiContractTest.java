package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterGenerationUiContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeCharacterGenerationDialogAndEditableDraftFields() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/project.ts"));
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String dialog = section(view, "<el-dialog v-model=\"characterGeneratorOpen\"", "</el-dialog>");
        String draftType = section(types, "export interface CharacterDraft", "export interface CharacterDraftList");
        String confirmType = section(types, "export interface ConfirmCharactersRequest", "export interface ConfirmStoryBibleRequest");

        System.out.printf("character generation UI checked: dialogLength=%d%n", dialog.length());
        assertThat(view)
                .contains("AI 补充人物", "＋ 新建角色")
                .contains("<el-dialog")
                .contains("@click=\"openCharacterGenerator\"");
        assertThat(dialog)
                .contains("新增数量", "补充要求", "generateCharacterDrafts", "删除人物草稿")
                .contains("告诉 AI 你希望补充哪些人物功能、性格或主线关联：")
                .contains("补充与核心谜案有关的调查者或盟友，避免重复已有人物")
                .contains("放弃", "应用到角色库", "@click=\"discardCharacterDraft\"", "@click=\"applyCharacterDrafts\"")
                .contains("v-model=\"draft.name\"", "v-model=\"draft.role\"", "v-model=\"draft.gender\"")
                .contains("v-model=\"draft.ageDescription\"", "v-model=\"draft.appearance\"")
                .contains("v-model=\"draft.personality\"", "v-model=\"draft.backgroundStory\"")
                .contains("v-model=\"draft.note\"")
                .doesNotContain("AI 生成核心角色", "建议人物数量", "生成核心角色")
                .doesNotContain("characterCode", "lifeStatus", "status", "roleType", "currentStateJson");
        assertThat(view)
                .contains("await apiDiscardCharacterDraft", "await apiConfirmCharacters", "await loadCharacters(projectCode)")
                .doesNotContain("createCharacterCode", "characterCode: createCharacterCode");
        assertThat(api)
                .contains("generateCharacters", "/characters/generate")
                .contains("PlanningDraftResponse<CharacterDraftList>");
        assertThat(types)
                .contains("interface CharacterDraft", "interface CharacterDraftList")
                .contains("role: string", "gender: string", "backgroundStory?: string");
        assertThat(draftType)
                .contains("name: string", "role: string", "gender: string", "appearance?: string")
                .doesNotContain("characterCode", "currentStateJson", "lifeStatus", "status");
        assertThat(confirmType)
                .contains("draftId: string", "characters: CharacterDraft[]")
                .doesNotContain("characterCode", "currentStateJson", "lifeStatus", "status");
    }

    private String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex);
    }
}
