package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StorySummaryPO {
    /** 摘要主键，用于定位章节生成后的记忆记录。 */
    private Long id;
    /** 所属小说项目主键，用于按作品加载上下文记忆。 */
    private Long projectId;
    /** 对应章节主键，用于保证摘要与正文一一关联。 */
    private Long chapterId;
    /** 对应章节号，用于按时间线顺序检索摘要。 */
    private Integer chapterNumber;
    /** 短摘要，用于以较低 token 成本回顾前文。 */
    private String shortSummary;
    /** 关键事件 JSON，用于记录后续章节需要承接的剧情推进。 */
    private String keyEventsJson;
    /** 未解决问题 JSON，用于管理伏笔并指导后续章节。 */
    private String unresolvedQuestionsJson;
    /** 章节结尾钩子，用于承接下一章的开篇冲突。 */
    private String endingHook;
    /** 当前故事状态快照 JSON，用于承接后续章节容易写错的有效状态。 */
    private String storyStateSnapshotJson;
    /** 摘要状态，用于控制其是否可被续写上下文使用。 */
    private String status;
    /** 摘要创建时间，用于审计。 */
    private LocalDateTime createdAt;
    /** 摘要最后更新时间，用于追踪摘要修订。 */
    private LocalDateTime updatedAt;
}
