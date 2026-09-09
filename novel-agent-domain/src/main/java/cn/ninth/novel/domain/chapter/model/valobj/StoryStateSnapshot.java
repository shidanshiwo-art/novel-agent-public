package cn.ninth.novel.domain.chapter.model.valobj;

import java.util.List;

/**
 * 当前故事状态快照。
 *
 * <p>只保留后续章节容易忘记、忘记后容易写错的有效状态，不承载历史事件。</p>
 */
public record StoryStateSnapshot(
        List<String> resources,
        List<String> abilities,
        List<String> knowledge,
        List<String> presence
) {

    public StoryStateSnapshot {
        resources = immutable(resources);
        abilities = immutable(abilities);
        knowledge = immutable(knowledge);
        presence = immutable(presence);
    }

    public static StoryStateSnapshot empty() {
        return new StoryStateSnapshot(List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> immutable(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
