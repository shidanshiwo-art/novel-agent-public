package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.model.valobj.CharacterBriefVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 使用简单文本规则筛选单章规划需要的人物，并投影为四字段摘要。
 *
 * <p>筛选器只接收人物名称与人物上下文 JSON 作为输入信号，输出不会携带完整人物档案或人物状态。</p>
 */
public final class RelevantCharacterSelector {

    public static final int MAX_CHARACTERS = 4;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 按上一章人物、当前大纲人物和 ACTIVE 人物的优先级返回摘要。
     *
     * @param previousChapterSummary 上一章摘要
     * @param targetOutlineTitle 当前最具体大纲节点标题，可为空
     * @param targetOutlineSummary 当前最具体大纲节点摘要，可为空
     * @param characters 项目人物候选
     * @return 最多四个不含完整档案和人物状态的人物摘要
     */
    public List<CharacterBriefVO> select(
            String previousChapterSummary,
            String targetOutlineTitle,
            String targetOutlineSummary,
            List<StoryCharacterVO> characters
    ) {
        if (characters == null || characters.isEmpty()) {
            return List.of();
        }

        String previousText = normalize(previousChapterSummary);
        String targetText = normalize(targetOutlineTitle, targetOutlineSummary);
        List<RankedCharacter> ranked = new ArrayList<>();
        for (int index = 0; index < characters.size(); index++) {
            StoryCharacterVO character = characters.get(index);
            if (character == null || isBlank(character.name())) {
                continue;
            }
            int priority = priority(character, previousText, targetText);
            if (priority >= 0) {
                ranked.add(new RankedCharacter(character, priority, index));
            }
        }

        ranked.sort(Comparator
                .comparingInt(RankedCharacter::priority).reversed()
                .thenComparingInt(RankedCharacter::sourceIndex));

        List<CharacterBriefVO> selected = new ArrayList<>(MAX_CHARACTERS);
        Set<String> selectedNames = new HashSet<>();
        for (RankedCharacter candidate : ranked) {
            if (selectedNames.add(normalize(candidate.character().name()))) {
                selected.add(toBrief(candidate.character()));
            }
            if (selected.size() == MAX_CHARACTERS) {
                return List.copyOf(selected);
            }
        }

        if (!selected.isEmpty()) {
            return List.copyOf(selected);
        }

        return protagonistFallback(characters);
    }

    /**
     * 不需要单独标题时的便捷入口。
     */
    public List<CharacterBriefVO> select(
            String previousChapterSummary,
            String targetOutlineSummary,
            List<StoryCharacterVO> characters
    ) {
        return select(previousChapterSummary, null, targetOutlineSummary, characters);
    }

    private int priority(
            StoryCharacterVO character,
            String previousText,
            String targetText
    ) {
        if (!isActive(character)) {
            return -1;
        }
        String name = normalize(character.name());
        if (!previousText.isBlank() && previousText.contains(name)) {
            return 2;
        }
        if (!targetText.isBlank() && targetText.contains(name)) {
            return 1;
        }
        return -1;
    }

    private boolean isActive(StoryCharacterVO character) {
        return "active".equals(normalize(character.status()))
                && !"dead".equals(normalize(character.lifeStatus()));
    }

    private List<CharacterBriefVO> protagonistFallback(List<StoryCharacterVO> characters) {
        for (StoryCharacterVO character : characters) {
            if (character != null
                    && !isBlank(character.name())
                    && isActive(character)
                    && isProtagonist(character.roleType())) {
                return List.of(toBrief(character));
            }
        }
        return List.of();
    }

    private boolean isProtagonist(String roleType) {
        String role = normalize(roleType);
        return role.contains("protagonist")
                || role.contains("main")
                || role.contains("lead")
                || role.contains("主角")
                || role.contains("男主")
                || role.contains("女主");
    }

    private CharacterBriefVO toBrief(StoryCharacterVO character) {
        CharacterContextProjection context = projectCharacterContext(character.currentStateJson());
        return new CharacterBriefVO(
                clean(character.name()),
                roleLabel(character.roleType()),
                context.currentGoal(),
                context.currentLocation()
        );
    }

    private String roleLabel(String roleType) {
        if (isBlank(roleType)) {
            return "其他角色";
        }
        String trimmed = roleType.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MALE_LEAD" -> "男主角";
            case "FEMALE_LEAD" -> "女主角";
            case "PROTAGONIST" -> "主角";
            case "ALLY" -> "盟友";
            case "RIVAL" -> "对手";
            case "ANTAGONIST", "VILLAIN" -> "反派";
            case "SUPPORTING" -> "配角";
            default -> normalized.matches("[A-Z][A-Z0-9_]*") ? "其他角色" : trimmed;
        };
    }

    private CharacterContextProjection projectCharacterContext(String currentStateJson) {
        if (isBlank(currentStateJson)) {
            return new CharacterContextProjection(null, null);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(currentStateJson);
            if (root != null && root.isObject()) {
                String goal = firstValue(root,
                        "currentGoal", "goal", "objective", "externalGoal", "当前目标", "目标");
                String location = firstValue(root,
                        "currentLocation", "location", "place", "当前位置", "位置", "地点");
                return new CharacterContextProjection(goal, location);
            }
        } catch (RuntimeException ignored) {
            // 兼容历史非 JSON 状态；无法投影为目标或位置时不进入 ChapterPlan 摘要。
        }
        return new CharacterContextProjection(null, null);
    }

    private String firstValue(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = root.get(key);
            String text = valueText(value);
            if (!isBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private String valueText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return clean(value.isTextual() ? value.asText() : value.toString());
    }

    private String normalize(String... values) {
        StringBuilder text = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (!isBlank(value)) {
                    text.append(normalize(value));
                }
            }
        }
        return text.toString();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RankedCharacter(
            StoryCharacterVO character,
            int priority,
            int sourceIndex
    ) {
    }

    private record CharacterContextProjection(
            String currentGoal,
            String currentLocation
    ) {
    }
}
