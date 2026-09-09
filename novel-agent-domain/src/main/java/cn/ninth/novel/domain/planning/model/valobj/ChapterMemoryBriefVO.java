package cn.ninth.novel.domain.planning.model.valobj;

import java.util.List;

/**
 * 单章规划可使用的章节记忆摘要。
 *
 * <p>只保留后续规划所需的故事语义，不携带持久化对象、数据库 ID 或 JSON 字段。</p>
 */
public record ChapterMemoryBriefVO(
        Integer chapterNumber,
        String shortSummary,
        List<String> keyEvents,
        List<String> unresolved,
        String endingHook
) {

    public ChapterMemoryBriefVO {
        keyEvents = immutable(keyEvents);
        unresolved = immutable(unresolved);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
