package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMemoryBridgeTest {

    @Test
    void chapterMemoryAndSnapshotBecomeBridgeOnlyEvidence() {
        ChapterMemoryVO memory = new ChapterMemoryVO(
                9,
                "残晶在地下室被发现",
                List.of("残晶被带出地下室"),
                List.of("残晶来源尚未查明"),
                "远处传来钟声");
        StoryStateSnapshot snapshot = new StoryStateSnapshot(
                List.of("黑色残晶"),
                List.of("主动激活夜行能力"),
                List.of("陆遥知道古籍中的封印记载"),
                List.of("残晶位于马车暗格"));

        LegacyMemoryBridge bridge = new LegacyMemoryBridge();
        List<MemoryContextItem> items = new java.util.ArrayList<>(bridge.fromChapterMemory(memory));
        items.addAll(bridge.fromStoryStateSnapshot(snapshot, 9));

        assertThat(items).isNotEmpty().allMatch(MemoryContextItem::bridgeOnly);
        assertThat(items).extracting(MemoryContextItem::category)
                .contains(MemoryContextCategory.CONSOLIDATED,
                        MemoryContextCategory.OPEN_LOOPS,
                        MemoryContextCategory.CURRENT_STATES);
        assertThat(items).extracting(MemoryContextItem::content)
                .anyMatch(content -> content.contains("残晶来源尚未查明"))
                .anyMatch(content -> content.contains("陆遥知道古籍中的封印记载"));

        ChapterHistoryVO history = ChapterHistoryVO.builder()
                .recentMemories(List.of(memory))
                .storyStateSnapshot(snapshot)
                .build();
        assertThat(bridge.fromHistory(history)).hasSameSizeAs(items)
                .allMatch(MemoryContextItem::bridgeOnly);
        System.out.printf(
                "Legacy bridge evidence chapter=9 items=%d categories=%s canonicalUpgrade=false%n",
                items.size(), items.stream().map(MemoryContextItem::category).distinct().toList());
    }
}
