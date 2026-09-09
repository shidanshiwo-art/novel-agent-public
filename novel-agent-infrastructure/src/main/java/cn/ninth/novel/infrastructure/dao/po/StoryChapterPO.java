package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryChapterPO {
    /** 章节主键，用于关联摘要及其他章节衍生数据。 */
    private Long id;
    /** 所属小说项目主键，用于按作品查询章节。 */
    private Long projectId;
    /** 对应章节计划主键，用于校验正文是否落实章节规划。 */
    private Long chapterPlanId;
    /** 项目内章节序号，用于排序和续写定位。 */
    private Integer chapterNumber;
    /** 章节标题，用于展示和概括章节主题。 */
    private String title;
    /** 章节正文，用于阅读、续写和上下文拼接。 */
    private String content;
    /** 正文字数，用于校验单章篇幅是否达标。 */
    private Integer wordCount;
    /** 章节状态，用于区分草稿、已定稿或人工修改后待同步内容。 */
    private String status;
    /** 章节创建时间，用于审计和排序。 */
    private LocalDateTime createdAt;
    /** 章节最后更新时间，用于追踪修订。 */
    private LocalDateTime updatedAt;
}
