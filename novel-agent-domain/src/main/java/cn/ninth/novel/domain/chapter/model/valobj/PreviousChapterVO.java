package cn.ninth.novel.domain.chapter.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上一章快照值对象。
 * 保存当前待生成章节的直接前章正文及基础信息，用于衔接场景、动作和叙事语气。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PreviousChapterVO {

    /** 上一章数据库主键，用于追溯正文来源。 */
    private Long chapterId;
    /** 上一章章节号，用于校验与当前章节是否连续。 */
    private Integer chapterNumber;
    /** 上一章标题，用于提供章节主题信息。 */
    private String title;
    /** 上一章完整正文，用于衔接场景、动作、对白和叙事语气。 */
    private String content;
    /** 上一章正文字数，用于描述历史章节的篇幅信息。 */
    private Integer wordCount;
    /** 上一章状态，用于判断正文是否可作为有效续写依据。 */
    private String status;
}
