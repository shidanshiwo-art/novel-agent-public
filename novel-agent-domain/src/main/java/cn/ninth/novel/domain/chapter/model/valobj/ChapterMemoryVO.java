package cn.ninth.novel.domain.chapter.model.valobj;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面向后续章节使用的章节压缩记忆。
 *
 * <p>查询历史记忆时只暴露业务字段，不暴露数据库 JSON 字段名或持久化对象。</p>
 */
@Data
@Builder
@NoArgsConstructor
public class ChapterMemoryVO {

    /** 压缩记忆对应的章节号。 */
    private Integer chapterNumber;

    /** 本章主要推进和核心冲突结果。 */
    private String shortSummary;

    /** 本章需要后续章节记住的关键事件。 */
    private List<String> keyEvents;

    /** 本章结束时仍未解决的问题。 */
    private List<String> unresolved;

    /** 本章结尾已经出现的明确钩子。 */
    private String endingHook;

    public ChapterMemoryVO(
            Integer chapterNumber,
            String shortSummary,
            List<String> keyEvents,
            List<String> unresolved,
            String endingHook
    ) {
        this.chapterNumber = chapterNumber;
        this.shortSummary = shortSummary;
        this.keyEvents = keyEvents;
        this.unresolved = unresolved;
        this.endingHook = endingHook;
    }
}
