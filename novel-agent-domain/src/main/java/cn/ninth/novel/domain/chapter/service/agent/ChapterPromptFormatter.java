package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.ninth.novel.domain.chapter.service.agent.PromptAppender.appendLineIfPresent;

/**
 * 将章节上下文中的存储字段渲染为模型可理解的业务文本。
 */
final class ChapterPromptFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> BUSINESS_LABELS = Map.ofEntries(
            Map.entry("name", "名称"),
            Map.entry("description", "说明"),
            Map.entry("说明", "说明"),
            Map.entry("机制", "机制"),
            Map.entry("mechanism", "机制"),
            Map.entry("supplement", "补充设定"),
            Map.entry("补充设定", "补充设定"),
            Map.entry("cost", "代价"),
            Map.entry("代价", "代价"),
            Map.entry("ranks", "境界"),
            Map.entry("levels", "等级/境界"),
            Map.entry("level", "等级/境界"),
            Map.entry("等级/境界", "等级/境界"),
            Map.entry("等级", "等级/境界"),
            Map.entry("层级", "层级"),
            Map.entry("境界", "境界"),
            Map.entry("currentGoal", "当前目标"),
            Map.entry("goal", "当前目标"),
            Map.entry("objective", "当前目标"),
            Map.entry("externalGoal", "当前目标"),
            Map.entry("当前目标", "当前目标"),
            Map.entry("目标", "当前目标"),
            Map.entry("currentLocation", "位置"),
            Map.entry("location", "位置"),
            Map.entry("place", "位置"),
            Map.entry("当前位置", "位置"),
            Map.entry("位置", "位置"),
            Map.entry("地点", "位置"),
            Map.entry("currentState", "状态"),
            Map.entry("state", "状态"),
            Map.entry("condition", "状态"),
            Map.entry("当前状态", "状态"),
            Map.entry("状态", "状态"),
            Map.entry("ability", "能力"),
            Map.entry("abilities", "能力"),
            Map.entry("能力", "能力"),
            Map.entry("resource", "资源"),
            Map.entry("resources", "资源"),
            Map.entry("资源", "资源"),
            Map.entry("inventory", "持有物"),
            Map.entry("items", "持有物"),
            Map.entry("持有物", "持有物"),
            Map.entry("物品", "持有物"),
            Map.entry("relationship", "关系"),
            Map.entry("relationships", "关系"),
            Map.entry("relations", "关系"),
            Map.entry("关系", "关系"),
            Map.entry("rules", "规则"),
            Map.entry("hardRules", "规则"),
            Map.entry("硬规则", "规则"),
            Map.entry("不可违反的规则", "规则")
    );

    private ChapterPromptFormatter() {
    }

    static void appendHardRules(StringBuilder prompt, String label, String storedValue) {
        JsonNode root = parse(storedValue);
        if (root == null) {
            appendLegacyLines(prompt, label, storedValue);
            return;
        }

        prompt.append(label).append("：\n");
        List<String> rules = new ArrayList<>();
        if (root.isObject()) {
            JsonNode explicitRules = firstField(root, "rules", "hardRules", "硬规则", "不可违反的规则");
            if (explicitRules != null) {
                appendNaturalValues(rules, explicitRules);
            } else {
                root.properties().forEach(field -> addValue(rules,
                        businessLabel(field.getKey()) + "：" + naturalText(field.getValue())));
            }
        } else {
            appendNaturalValues(rules, root);
        }
        appendBullets(prompt, rules);
    }

    static void appendPowerSystem(StringBuilder prompt, String label, String storedValue) {
        JsonNode root = parse(storedValue);
        if (root == null) {
            appendLegacyLines(prompt, label, storedValue);
            return;
        }

        prompt.append(label).append("：\n");
        if (root.isObject()) {
            int before = prompt.length();
            root.properties().forEach(field -> appendField(prompt, field.getKey(), field.getValue(), "- "));
            if (prompt.length() == before) {
                prompt.append("- 无\n");
            }
        } else if (root.isArray()) {
            appendNaturalValuesAsBullets(prompt, root, "- ");
        } else {
            String text = naturalText(root);
            prompt.append(text.isBlank() ? "- 无\n" : "- ").append(text).append('\n');
        }
    }

    static void appendCharacterStaticSettings(StringBuilder prompt, StoryCharacterEntity character) {
        if (character == null) {
            return;
        }
        appendLineIfPresent(prompt, "角色定位", character.getRoleType());
        appendLineIfPresent(prompt, "性别", character.getGender());
        appendLineIfPresent(prompt, "年龄", character.getAgeDescription());
        appendLineIfPresent(prompt, "外貌", character.getAppearance());
        appendLineIfPresent(prompt, "性格", character.getPersonality());
        appendLineIfPresent(prompt, "背景故事", character.getBackgroundStory());
        appendLineIfPresent(prompt, "作者备注", character.getNote());
    }

    static void appendStoryStateSnapshot(StringBuilder prompt, StoryStateSnapshot snapshot) {
        if (snapshot == null) {
            prompt.append("无\n\n");
            return;
        }
        appendList(prompt, "重要资源/物品", snapshot.resources());
        appendList(prompt, "能力、伤势与限制", snapshot.abilities());
        appendList(prompt, "已知与未知信息", snapshot.knowledge());
        appendList(prompt, "位置与出场状态", snapshot.presence());
        prompt.append('\n');
    }

    private static void appendList(StringBuilder prompt, String label, List<String> values) {
        prompt.append(label).append("：");
        if (values == null || values.isEmpty()) {
            prompt.append("无\n");
            return;
        }
        prompt.append('\n');
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                prompt.append("- ").append(value.trim()).append('\n');
            }
        }
    }

    static void appendJsonList(StringBuilder prompt, String label, String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return;
        }
        JsonNode root = parse(storedValue);
        List<String> values = new ArrayList<>();
        if (root == null) {
            for (String line : storedValue.trim().split("\\R")) {
                addValue(values, line);
            }
        } else {
            appendNaturalValues(values, root);
        }
        PromptAppender.appendList(prompt, label, values);
    }

    private static void appendField(
            StringBuilder prompt,
            String key,
            JsonNode value,
            String prefix
    ) {
        if (value == null || value.isNull()) {
            return;
        }
        String label = businessLabel(key);
        if (value.isObject()) {
            prompt.append(prefix).append(label).append("：\n");
            value.properties().forEach(field -> appendField(prompt, field.getKey(), field.getValue(), childPrefix(prefix)));
            return;
        }
        if (value.isArray()) {
            prompt.append(prefix).append(label).append("：\n");
            appendNaturalValuesAsBullets(prompt, value, childPrefix(prefix));
            return;
        }
        String text = naturalText(value).trim();
        if (!text.isBlank()) {
            prompt.append(prefix).append(label).append('：').append(text).append('\n');
        }
    }

    private static String childPrefix(String prefix) {
        return prefix.trim().equals("-") ? "  " : prefix + "  ";
    }

    private static void appendNaturalValuesAsBullets(StringBuilder prompt, JsonNode value, String prefix) {
        List<String> values = new ArrayList<>();
        appendNaturalValues(values, value);
        if (values.isEmpty()) {
            prompt.append(prefix).append("- 无\n");
            return;
        }
        for (String item : values) {
            prompt.append(prefix).append("- ").append(item).append('\n');
        }
    }

    private static void appendNaturalValues(List<String> values, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(item -> appendNaturalValues(values, item));
            return;
        }
        addValue(values, naturalText(value));
    }

    private static void appendBullets(StringBuilder prompt, List<String> values) {
        if (values.isEmpty()) {
            prompt.append("- 无\n");
            return;
        }
        for (String value : values) {
            prompt.append("- ").append(value).append('\n');
        }
    }

    private static void appendLegacyLines(StringBuilder prompt, String label, String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return;
        }
        prompt.append(label).append("：\n");
        List<String> lines = new ArrayList<>();
        for (String line : storedValue.trim().split("\\R")) {
            addValue(lines, line);
        }
        appendBullets(prompt, lines);
    }

    private static void addValue(List<String> values, String value) {
        if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
            values.add(value.trim());
        }
    }

    private static JsonNode parse(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(storedValue);
            return root == null || root.isNull() ? null : root;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonNode firstField(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static String naturalText(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        if (value.isArray()) {
            List<String> items = new ArrayList<>();
            value.forEach(item -> addValue(items, naturalText(item)));
            return String.join("、", items);
        }
        List<String> fields = new ArrayList<>();
        value.properties().forEach(field -> {
            String text = naturalText(field.getValue()).trim();
            if (!text.isBlank()) {
                fields.add(businessLabel(field.getKey()) + "：" + text);
            }
        });
        return String.join("；", fields);
    }

    private static String businessLabel(String key) {
        if (key == null || key.isBlank()) {
            return "其他信息";
        }
        String trimmed = key.trim();
        String known = BUSINESS_LABELS.get(trimmed);
        if (known != null) {
            return known;
        }
        if (trimmed.chars().anyMatch(character -> character >= '\u4e00' && character <= '\u9fff')) {
            return trimmed;
        }
        return "其他信息";
    }
}
