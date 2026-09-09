package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NovelProjectPO {
    /** 项目主键，用于关联项目下的全部小说数据。 */
    private Long id;
    /** 项目业务编码，用于在项目范围外稳定标识作品。 */
    private String projectCode;
    /** 小说标题，用于展示和生成上下文。 */
    private String title;
    /** 小说题材，用于约束创作方向和风格。 */
    private String genre;
    /** 当前创作阶段的预计章节数，达到后需由用户调整才能继续规划。 */
    private Integer targetChapterCount;
    /** 单章目标字数，用于约束章节篇幅。 */
    private Integer wordsPerChapter;
    /** 当前已推进到的章节号，用于续写和进度展示。 */
    private Integer currentChapterNumber;
    /** 项目生命周期状态，用于控制项目是否可继续生成。 */
    private String status;
    /** 项目创建时间，用于审计和排序。 */
    private LocalDateTime createdAt;
    /** 项目最后更新时间，用于同步和最近修改排序。 */
    private LocalDateTime updatedAt;
}
