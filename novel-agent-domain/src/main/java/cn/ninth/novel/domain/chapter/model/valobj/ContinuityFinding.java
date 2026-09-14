package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条跨章节连续性发现。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContinuityFinding {

    /** 连续性问题类型。 */
    private String type;

    /** 被检查的角色、物品、能力或其他实体。 */
    private String entity;

    /** HARD 需要返修，SOFT 仅作为可选修复项。 */
    private ContinuitySeverity severity;

    /** 当前正文中的证据。 */
    private String currentEvidence;

    /** 历史上下文中的证据。 */
    private String historicalEvidence;

    /** 历史证据所在章节。 */
    private Integer sourceChapter;

    /** 发现的业务理由。 */
    private String reason;

    /** 发现置信度，取值约定为 0 到 1。 */
    private Double confidence;
}
