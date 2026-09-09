package cn.ninth.novel.domain.chapter.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 章节历史值对象。
 * 汇总当前章节之前的近期压缩记忆和直接上一章正文，表示本次续写所依赖的历史快照。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChapterHistoryVO {

    /** 当前章节之前、按章节号升序排列的近期压缩记忆。 */
    private List<ChapterMemoryVO> recentMemories;
    /** 当前章节之前最近一次 COMPRESSION 产生的有效故事状态。 */
    private StoryStateSnapshot storyStateSnapshot;
    /** 当前章节的直接上一章；生成第一章时允许为空。 */
    private PreviousChapterVO previousChapter;

    /**
     * 读取旧项目历史时，缺失状态快照按四个空列表处理。
     *
     * @return 当前有效状态，旧历史没有快照时返回空快照
     */
    public StoryStateSnapshot getStoryStateSnapshot() {
        return storyStateSnapshot == null ? StoryStateSnapshot.empty() : storyStateSnapshot;
    }
}
