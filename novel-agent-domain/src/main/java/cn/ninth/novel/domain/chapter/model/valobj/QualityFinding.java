package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.QualitySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条章节质量发现。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QualityFinding {

    /** 质量问题类型。 */
    private String type;

    /** 支撑问题的正文证据。 */
    private String evidence;

    /** 问题描述。 */
    private String problem;

    /** 返修意图。 */
    private String repairIntent;

    /** 受影响正文范围；本阶段使用业务位置描述。 */
    private String affectedRange;

    /** MUST_FIX 进入必需返修，OPTIONAL 不阻断通过。 */
    private QualitySeverity severity;
}
