package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 将章节记忆持久化对象转换为历史章节记忆业务对象。 */
final class ChapterMemoryMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ChapterMemoryMapper() {
    }

    static ChapterMemoryVO toMemory(StorySummaryPO summary) {
        return new ChapterMemoryVO(
                summary.getChapterNumber(),
                summary.getShortSummary(),
                parseBusinessList(summary.getKeyEventsJson()),
                parseBusinessList(summary.getUnresolvedQuestionsJson()),
                summary.getEndingHook()
        );
    }

    static StoryStateSnapshot toStoryStateSnapshot(StorySummaryPO summary) {
        if (summary == null || summary.getStoryStateSnapshotJson() == null
                || summary.getStoryStateSnapshotJson().isBlank()) {
            return StoryStateSnapshot.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(summary.getStoryStateSnapshotJson());
            if (root == null || !root.isObject()) {
                return StoryStateSnapshot.empty();
            }
            return new StoryStateSnapshot(
                    parseBusinessList(root.get("resources")),
                    parseBusinessList(root.get("abilities")),
                    parseBusinessList(root.get("knowledge")),
                    parseBusinessList(root.get("presence"))
            );
        } catch (RuntimeException ignored) {
            return StoryStateSnapshot.empty();
        }
    }

    private static List<String> parseBusinessList(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(storedValue);
            if (root == null || root.isNull()) {
                return List.of();
            }
            return parseBusinessList(root);
        } catch (RuntimeException ignored) {
            List<String> values = new ArrayList<>();
            for (String line : storedValue.trim().split("\\R")) {
                if (!line.isBlank()) {
                    values.add(line.trim());
                }
            }
            return List.copyOf(values);
        }
    }

    private static List<String> parseBusinessList(JsonNode root) {
        if (root == null || root.isNull() || (!root.isArray() && !root.isTextual())) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        appendJsonValues(values, root);
        return List.copyOf(values);
    }

    private static void appendJsonValues(List<String> values, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> appendJsonValues(values, item));
            return;
        }
        String value = node.asText();
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }
}
