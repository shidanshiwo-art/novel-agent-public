package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.service.LegacyMemoryBridge;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 将章节记忆持久化对象转换为历史章节记忆业务对象。 */
final class ChapterMemoryMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LegacyMemoryBridge LEGACY_MEMORY_BRIDGE = new LegacyMemoryBridge();

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

    /**
     * 将兼容的 story_summary 转换为统一 ContextProvider 的长期候选。
     *
     * <p>摘要和关键事件进入 consolidated，未解决问题进入 openLoops，
     * 最新状态快照进入 currentStates。这里仍是 legacy backfill 适配，
     * 不改变 ChapterMemoryVO 或 StoryStateSnapshot 的兼容结构。</p>
     */
    static List<MemoryContextItem> toMemoryContextItems(
            List<StorySummaryPO> summaries,
            Integer beforeChapter
    ) {
        List<StorySummaryPO> valid = summaries == null
                ? List.of()
                : summaries.stream()
                .filter(ChapterMemoryMapper::isUsableSummary)
                .filter(summary -> beforeChapter == null
                        || summary.getChapterNumber() < beforeChapter)
                .sorted(Comparator.comparing(StorySummaryPO::getChapterNumber))
                .toList();
        if (valid.isEmpty()) {
            return List.of();
        }

        List<MemoryContextItem> items = new ArrayList<>();
        for (StorySummaryPO summary : valid) {
            items.addAll(LEGACY_MEMORY_BRIDGE.fromChapterMemory(toMemory(summary)));
        }

        StorySummaryPO latest = valid.get(valid.size() - 1);
        items.addAll(LEGACY_MEMORY_BRIDGE.fromStoryStateSnapshot(
                toStoryStateSnapshot(latest), latest.getChapterNumber()));
        return List.copyOf(items);
    }

    private static boolean isUsableSummary(StorySummaryPO summary) {
        if (summary == null || summary.getChapterNumber() == null) {
            return false;
        }
        String status = summary.getStatus();
        return status == null || status.isBlank()
                || "GENERATED".equalsIgnoreCase(status)
                || "VERIFIED".equalsIgnoreCase(status);
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
