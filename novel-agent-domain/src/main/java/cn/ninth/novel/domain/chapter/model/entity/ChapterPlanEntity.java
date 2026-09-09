package cn.ninth.novel.domain.chapter.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 持久化章节计划实体。
 * 表示 chapter_plan 中具有稳定身份的章节计划及其所属项目、大纲节点关系。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChapterPlanEntity {

    /** 章节计划主键。 */
    private Long id;
    /** 所属小说项目主键。 */
    private Long projectId;
    /** 所属大纲节点主键。 */
    private Long outlineNodeId;
    /** 所属 ARC 的业务编码，用于正文生成前的一致性校验。 */
    private String outlineNodeCode;
    /** 项目内章节号。 */
    private Integer chapterNumber;
    /** 章节标题。 */
    private String title;
    /** 章节计划摘要。 */
    private String summary;
    /** 章节计划状态。 */
    private String status;
}
