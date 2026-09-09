package cn.ninth.novel.web;

import cn.ninth.novel.api.dto.UpdateStoryCharacterRequestDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterLibraryContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposeOnlyProfileFieldsOnUpdateDto() {
        String[] fields = Arrays.stream(UpdateStoryCharacterRequestDTO.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);

        System.out.printf("角色编辑 DTO 字段：%s%n", Arrays.toString(fields));
        assertThat(fields).containsExactly(
                "name", "roleType", "gender", "ageDescription",
                "appearance", "personality", "backgroundStory", "note"
        );
    }

    @Test
    void shouldSearchEditAndCreateCharactersThroughLibraryActions() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String api = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/project.ts"));
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String controller = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-trigger/src/main/java/cn/ninth/novel/trigger/http/NovelProjectController.java"));
        String updateType = section(types, "export interface UpdateStoryCharacterRequest", "export interface StoryCharacterResponse");
        String updateDto = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-api/src/main/java/cn/ninth/novel/api/dto/UpdateStoryCharacterRequestDTO.java"));
        String characterMapperSignature = "private StoryCharacterVO toCharacter(";
        int firstMapperStart = controller.indexOf(characterMapperSignature);
        int updateStart = controller.indexOf(characterMapperSignature, firstMapperStart + characterMapperSignature.length());
        int updateEnd = controller.indexOf("private List<CharacterDraftVO> toCharacterDrafts", updateStart);
        assertThat(updateStart).isGreaterThanOrEqualTo(0);
        assertThat(updateEnd).isGreaterThan(updateStart);
        String updateConversion = controller.substring(updateStart, updateEnd);

        System.out.printf("character library state boundary checked: updateTypeLength=%d%n", updateType.length());

        assertThat(api)
                .contains("export const listCharacters")
                .contains("http.get<any, StoryCharacterResponse[]>");
        assertThat(view)
                .contains("<h1>角色库</h1>")
                .contains("v-for=\"character in filteredCharacters\"")
                .contains(":prefix-icon=\"Search\"")
                .contains("clearable size=\"large\" class=\"character-search\"")
                .contains("@click=\"openCharacterCreator\"")
                .contains("@click=\"openCharacterEditor(character)\"")
                .contains("v-model=\"characterCreatorOpen\"")
                .contains("label-position=\"top\" class=\"character-form-stack\"")
                .contains("await updateCharacter(projectCode, editingCharacterCode.value, {")
                .contains("await loadCharacters(projectCode)")
                .contains("characterCreatorOpen.value = false")
                .doesNotContain("<el-segmented")
                .doesNotContain("<h1>创建角色</h1>");
        assertThat(view)
                .doesNotContain("currentStateJson: cf.currentStateJson", "lifeStatus: cf.lifeStatus", "status: cf.status");
        assertThat(updateType)
                .doesNotContain("currentStateJson?: string", "lifeStatus?: string", "status?: string");
        assertThat(updateDto)
                .doesNotContain("currentStateJson", "lifeStatus", "status");
        assertThat(updateConversion)
                .contains(
                        "character.name()", "character.roleType()", "character.gender()",
                        "character.ageDescription()", "character.appearance()",
                        "character.personality()", "character.backgroundStory()", "character.note()"
                )
                .doesNotContain(
                        "character.currentStateJson()", "character.lifeStatus()",
                        "character.status()", "existing"
                );
    }

    @Test
    void shouldShowSimpleStoryPreparationStatusOnCharacterPage() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String planningApi = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/planning.ts"));

        System.out.println("角色页故事准备状态检查：世界设定、核心人物、大纲使用已确认数据计算");
        assertThat(view)
                .contains("class=\"story-preparation\"")
                .contains("aria-label=\"创作准备\"")
                .contains("<h2>创作准备</h2>")
                .contains("preparationProgress")
                .contains("preparationSummary")
                .contains("v-for=\"item in preparationItems\"")
                .contains("item.completed ? '✓' : '○'")
                .contains("const preparationItems = computed(() => [")
                .contains("{ label: '世界设定', completed: hasConfirmedBible.value }")
                .contains("{ label: '核心人物', completed: characters.value.length > 0 }")
                .contains("{ label: '故事大纲', completed: outlineNodes.value.length > 0 }")
                .contains("import { listOutlineNodes } from '../api/planning'")
                .contains("async function loadOutlineNodes(projectCode: string)")
                .contains("void loadOutlineNodes(projectCode)");
        assertThat(view)
                .contains("class=\"character-card-summary\"")
                .contains("class=\"character-edit-action\"")
                .contains(":aria-label=\"`编辑角色 ${character.name}`\"")
                .doesNotContain("点击编辑 →", "class=\"edit-hint\"")
                .contains("@click.stop=\"openCharacterEditor(character)\"");
        assertThat(planningApi)
                .contains("export const listOutlineNodes")
                .contains("`${base(projectCode)}/outlines/tree`");
    }

    private String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex);
    }
}
