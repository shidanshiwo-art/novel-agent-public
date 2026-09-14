package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 将旧 ChapterMemory/StoryStateSnapshot 降级为不可升级的 evidence/bridge。
 *
 * <p>该适配器只生成带 bridge 标记的 Context item，不生成 Candidate、Fact、Event 或
 * Projection，因此旧数据不会直接进入 Canonical。</p>
 */
public final class LegacyMemoryBridge {

    public List<MemoryContextItem> fromChapterMemory(ChapterMemoryVO memory) {
        if (memory == null || memory.getChapterNumber() == null
                || memory.getChapterNumber() < 0) {
            return List.of();
        }
        int chapter = memory.getChapterNumber();
        List<MemoryContextItem> items = new ArrayList<>();
        String consolidated = consolidatedText(memory);
        if (!consolidated.isBlank()) {
            items.add(MemoryContextItem.bridge(
                    "legacy-consolidated-" + chapter,
                    MemoryContextCategory.CONSOLIDATED,
                    consolidated,
                    chapter));
        }
        appendBridgeItems(
                items,
                "legacy-open-loop-" + chapter + "-",
                MemoryContextCategory.OPEN_LOOPS,
                memory.getUnresolved(),
                chapter,
                "");
        return List.copyOf(items);
    }

    public List<MemoryContextItem> fromStoryStateSnapshot(
            StoryStateSnapshot snapshot,
            int sourceChapter
    ) {
        if (snapshot == null) {
            return List.of();
        }
        if (sourceChapter < 0) {
            throw new IllegalArgumentException("sourceChapter 不能小于 0");
        }
        List<MemoryContextItem> items = new ArrayList<>();
        appendBridgeItems(
                items, "legacy-current-state-resource-", MemoryContextCategory.CURRENT_STATES,
                snapshot.resources(), sourceChapter, "资源/物品：");
        appendBridgeItems(
                items, "legacy-current-state-ability-", MemoryContextCategory.CURRENT_STATES,
                snapshot.abilities(), sourceChapter, "能力、伤势与限制：");
        appendBridgeItems(
                items, "legacy-current-state-knowledge-", MemoryContextCategory.CURRENT_STATES,
                snapshot.knowledge(), sourceChapter, "已知与未知信息：");
        appendBridgeItems(
                items, "legacy-current-state-presence-", MemoryContextCategory.CURRENT_STATES,
                snapshot.presence(), sourceChapter, "位置与出场状态：");
        return List.copyOf(items);
    }

    public List<MemoryContextItem> fromHistory(ChapterHistoryVO history) {
        if (history == null) {
            return List.of();
        }
        List<MemoryContextItem> items = new ArrayList<>();
        if (history.getRecentMemories() != null) {
            for (ChapterMemoryVO memory : history.getRecentMemories()) {
                items.addAll(fromChapterMemory(memory));
            }
        }
        int latestChapter = history.getRecentMemories() == null
                ? 0
                : history.getRecentMemories().stream()
                .filter(memory -> memory != null && memory.getChapterNumber() != null)
                .mapToInt(ChapterMemoryVO::getChapterNumber)
                .max()
                .orElse(0);
        items.addAll(fromStoryStateSnapshot(history.getStoryStateSnapshot(), latestChapter));
        return List.copyOf(items);
    }

    private static String consolidatedText(ChapterMemoryVO memory) {
        List<String> parts = new ArrayList<>();
        if (hasText(memory.getShortSummary())) {
            parts.add("摘要：" + memory.getShortSummary().trim());
        }
        if (memory.getKeyEvents() != null && !memory.getKeyEvents().isEmpty()) {
            String events = memory.getKeyEvents().stream()
                    .filter(LegacyMemoryBridge::hasText)
                    .map(String::trim)
                    .reduce((left, right) -> left + "；" + right)
                    .orElse("");
            if (!events.isBlank()) {
                parts.add("关键事件：" + events);
            }
        }
        if (hasText(memory.getEndingHook())) {
            parts.add("结尾钩子：" + memory.getEndingHook().trim());
        }
        return String.join("\n", parts);
    }

    private static void appendBridgeItems(
            List<MemoryContextItem> items,
            String idPrefix,
            MemoryContextCategory category,
            List<String> values,
            int sourceChapter,
            String label
    ) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (!hasText(value)) {
                continue;
            }
            items.add(MemoryContextItem.bridge(
                    idPrefix + index,
                    category,
                    label + value.trim(),
                    sourceChapter));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
