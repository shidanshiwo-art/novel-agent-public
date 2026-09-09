package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OutlineNodePO {
    /** 大纲节点主键，用于关联章节和定位节点。 */
    private Long id;
    /** 所属小说项目主键，用于隔离不同作品的大纲。 */
    private Long projectId;
    /** 父节点主键，用于构建分层大纲树。 */
    private Long parentId;
    /** 节点业务编码，用于在项目内稳定引用该节点。 */
    private String nodeCode;
    /** 节点语义类型。 */
    private String nodeKind;
    /** 同级节点排序号，用于确定大纲展开顺序。 */
    private Integer sequenceNo;
    /** 节点章节范围起点。 */
    private Integer startChapter;
    /** 节点章节范围终点。 */
    private Integer endChapter;
    /** 节点标题，用于概括该段剧情主题。 */
    private String title;
    /** 节点剧情摘要，用于向生成模型提供情节上下文。 */
    private String summary;
    /** 节点状态，用于标识草稿、已确认或已完成等阶段。 */
    private String status;
    /** 节点创建时间，用于审计。 */
    private LocalDateTime createdAt;
    /** 节点最后更新时间，用于追踪大纲调整。 */
    private LocalDateTime updatedAt;

}
