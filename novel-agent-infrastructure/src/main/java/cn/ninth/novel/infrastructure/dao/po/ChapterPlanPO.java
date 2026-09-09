package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChapterPlanPO {
    /** 章节计划主键。 */
    private Long id;
    /** 所属小说项目主键。 */
    private Long projectId;
    /** 所属大纲节点主键。 */
    private Long outlineNodeId;
    /** 项目内章节号。 */
    private Integer chapterNumber;
    /** 章节标题。 */
    private String title;
    /** 章节计划摘要。 */
    private String summary;
    /** 章节计划状态。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
