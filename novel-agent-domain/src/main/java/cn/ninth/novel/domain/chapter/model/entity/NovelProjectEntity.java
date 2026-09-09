package cn.ninth.novel.domain.chapter.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说项目实体。
 * 表示章节上下文所属的作品及其创作规模、进度和生命周期状态。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NovelProjectEntity {

    /** 项目业务编码，用于从外部请求稳定定位小说项目。 */
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
}
