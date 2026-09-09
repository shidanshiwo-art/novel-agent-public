package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleEditingContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldLoadPersistedStoryBibleIntoEditableForm() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String mapper = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/utils/storyBible.ts"));

        assertThat(view)
                .contains("getBible")
                .contains("generateStoryBible")
                .contains("apiConfirmStoryBible")
                .contains("bibleDraftId")
                .contains("设定")
                .contains("保持故事的世界边界和关键规则一致。")
                .contains("hasConfirmedBible ? 'AI 调整' : 'AI 生成初始设定'")
                .contains("保存")
                .contains("还没有故事设定")
                .contains("AI 生成初始设定")
                .contains("AI 调整草稿")
                .contains("放弃修改")
                .contains("应用修改")
                .contains("confirmedBibleSnapshot")
                .contains("discardBibleDraft")
                .contains("调整故事设定")
                .contains("只描述你希望改变的部分")
                .contains("直接开始编辑")
                .contains("世界设定")
                .contains("世界背景")
                .contains("不可违反的规则")
                .contains("特殊体系")
                .contains("主题与冲突")
                .contains("结局方向")
                .contains("写作风格")
                .contains("生成故事设定")
                .contains("watch(")
                .contains("{ immediate: true }")
                .contains("const bible = await getBible(projectCode)")
                .contains("storyBibleToForm(bible)")
                .contains("showBibleEmpty")
                .contains("v-model=\"bf.powerName\"")
                .contains("v-model=\"bf.powerDescription\"")
                .contains("v-model=\"bf.powerLevels\"")
                .contains("v-model=\"bf.powerSupplement\"")
                .contains("体系说明")
                .contains("等级 / 境界")
                .contains("存在明确等级体系时填写，没有可以留空。")
                .contains("补充设定（可选）")
                .contains("v-model=\"bf.hardRules[index]\"")
                .contains("story-bible-canvas")
                .contains("class=\"bible-writing-field world-background-field\"")
                .contains("class=\"rules-list\"")
                .contains("bible-secondary-section")
                .contains(".story-bible-canvas { width: 100%; min-width: 0;")
                .contains(".bible-editor { width: 100%; min-width: 0;")
                .contains("grid-template-columns: minmax(0, 1fr)")
                .contains("@media (max-width: 600px)")
                .contains("const activeBibleSection = ref<BibleSectionKey>('world')")
                .doesNotContain("AI 重新生成")
                .doesNotContain("AI 生成的设定")
                .doesNotContain("应用这份设定")
                .doesNotContain("这个世界如何运转")
                .doesNotContain("能力如何生效", "代价与限制", "成长层级")
                .doesNotContain("powerMechanism", "powerCost", "powerRanks")
                .doesNotContain("rank-list", "list-heading")
                .doesNotContain("expandedBibleSections", "scrollIntoView", "IntersectionObserver")
                .doesNotContain("v-model=\"bf.powerSystemJson\"")
                .doesNotContain("v-model=\"bf.hardRulesJson\"");
        int generateStart = view.indexOf("async function generateBible()");
        int discardStart = view.indexOf("function discardBibleDraft()");
        int confirmStart = view.indexOf("async function confirmBible()");
        assertThat(generateStart).isGreaterThanOrEqualTo(0);
        assertThat(discardStart).isGreaterThan(generateStart);
        assertThat(confirmStart).isGreaterThan(generateStart);
        assertThat(view.substring(generateStart, discardStart))
                .contains("const revisingBible = hasConfirmedBible.value")
                .contains("bibleDraftId.value = draft.draftId")
                .doesNotContain("hasConfirmedBible.value = false")
                .doesNotContain("hasConfirmedBible.value = revisingBible");
        assertThat(discardStart).isGreaterThanOrEqualTo(0);
        assertThat(view.substring(discardStart, confirmStart))
                .contains("confirmedBibleSnapshot.value")
                .contains("applyBible")
                .contains("bibleDraftId.value = null")
                .doesNotContain("generateStoryBible(")
                .doesNotContain("apiConfirmStoryBible(");
        String[] removedLegacyStoryBibleClasses = {
                "bible-workspace",
                "bible-nav",
                "bible-panel",
                "bible-section-panel",
                "bible-generator-controls",
                "premise-editor",
                "story-engine",
                "world-editor",
                "voice-setting",
                "creative-field",
                "bible-generator",
                "canvas-short-fields",
                "canvas-power-fields",
                "canvas-title-field",
                "canvas-writing-field",
                "canvas-longform-field",
                "canvas-structured-list",
                "list-editor",
                "list-head",
                "list-row",
                "premise-block",
                "premise-input",
                "engine-grid",
                "theme-field",
                "world-input",
                "supporting-settings",
                "section-heading",
                "editor-label",
                "section-index",
                "character-count",
                "rules-grid",
                "rules-setting",
        };
        for (String legacyClass : removedLegacyStoryBibleClasses) {
            assertThat(view)
                    .doesNotContain("." + legacyClass + " ")
                    .doesNotContain("class=\"" + legacyClass + "\"");
        }
        assertThat(mapper)
                .contains("JSON.parse(value)")
                .contains("const power = parsePowerSystem(bible.powerSystemJson)")
                .contains("hardRules: parseHardRules(bible.hardRulesJson)")
                .contains("storyBibleDraftToForm")
                .contains("storyBibleFormToDraft")
                .contains("name: form.powerName.trim()")
                .contains("description: form.powerDescription.trim()")
                .contains("levels: form.powerLevels.trim()")
                .contains("supplement: form.powerSupplement.trim()")
                .doesNotContain("mechanism: form.", "cost: form.", "ranks: cleanList(form.")
                .contains("hardRulesJson: JSON.stringify(cleanList(form.hardRules))");
        System.out.println("StoryBibleEditingContractTest verified editable settings flow and removal of legacy Story Bible UI classes");
    }
}
