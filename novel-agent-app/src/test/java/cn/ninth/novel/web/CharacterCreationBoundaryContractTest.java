package cn.ninth.novel.web;

import cn.ninth.novel.api.dto.AddStoryCharacterRequestDTO;
import cn.ninth.novel.api.dto.ConfirmCharacterDraftDTO;
import cn.ninth.novel.api.dto.ConfirmCharactersRequestDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterCreationBoundaryContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeOnlyProfileFieldsOnCharacterCreationDtos() {
        String[] profileFields = {
                "name", "roleType", "gender", "ageDescription",
                "appearance", "personality", "backgroundStory", "note"
        };

        String[] addFields = recordFields(AddStoryCharacterRequestDTO.class);
        String[] confirmDraftFields = recordFields(ConfirmCharacterDraftDTO.class);
        String[] confirmRequestFields = recordFields(ConfirmCharactersRequestDTO.class);

        System.out.printf(
                "人物创建 DTO 字段：add=%s, confirmDraft=%s, confirmRequest=%s%n",
                Arrays.toString(addFields), Arrays.toString(confirmDraftFields),
                Arrays.toString(confirmRequestFields)
        );
        assertThat(addFields).containsExactly(profileFields);
        assertThat(confirmDraftFields).containsExactly(
                "name", "role", "gender", "ageDescription",
                "appearance", "personality", "backgroundStory", "note"
        );
        assertThat(confirmRequestFields).containsExactly("draftId", "characters");
    }

    @Test
    void shouldLetBackendOwnCharacterCodeForManualCreation() throws IOException {
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String dto = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-api/src/main/java/cn/ninth/novel/api/dto/AddStoryCharacterRequestDTO.java"));
        String controller = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/NovelProjectController.java"));
        String addConversion = section(controller, "private StoryCharacterVO toCharacter(\n            AddStoryCharacterRequestDTO character", "private StoryCharacterVO toCharacter(\n            String characterCode");

        String addType = section(types, "export interface AddStoryCharacterRequest", "export interface UpdateStoryCharacterRequest");
        String roleOptions = section(view, "const roleOptions = [", "const savingChar");
        System.out.printf("character creation boundary checked: addTypeLength=%d%n", addType.length());
        assertThat(addType)
                .doesNotContain("characterCode", "currentStateJson", "lifeStatus", "status");
        assertThat(view)
                .contains("await addCharacter(projectCode, characterData)")
                .doesNotContain("createCharacterCode", "characterCode: createCharacterCode", "characterCode: ''");
        assertThat(roleOptions)
                .contains("value: '男主角'", "value: '女主角'", "value: '反派'")
                .doesNotContain("MALE_LEAD", "FEMALE_LEAD", "ANTAGONIST");
        assertThat(dto)
                .doesNotContain(
                        "String characterCode", "currentStateJson", "lifeStatus", "status"
                );
        assertThat(addConversion)
                .contains("new StoryCharacterVO")
                .doesNotContain(
                        "character.characterCode()",
                        "character.currentStateJson()",
                        "character.lifeStatus()",
                        "character.status()"
                );
    }

    private String[] recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
    }

    private String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex);
    }
}
