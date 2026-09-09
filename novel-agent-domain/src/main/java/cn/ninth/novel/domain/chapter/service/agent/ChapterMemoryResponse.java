package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;

import java.util.List;

/**
 * COMPRESSION 节点与模型交互的边界结果。
 * 只保留后续章节真正需要的章节记忆字段，不包含数据库字段或事实三元组。
 */
public record ChapterMemoryResponse(
        String shortSummary,
        List<String> keyEvents,
        List<String> unresolved,
        String endingHook,
        StoryStateSnapshot state
)
{
}
