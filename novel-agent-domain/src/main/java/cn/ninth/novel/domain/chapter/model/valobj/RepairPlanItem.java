package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairPriority;
import cn.ninth.novel.domain.chapter.model.valobj.enums.RepairSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条可执行的局部返修计划项。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepairPlanItem {

    /** 本次 Review Session 内稳定可引用的返修项标识。 */
    private String id;

    /** 返修项来自连续性还是质量审核。 */
    private RepairSource source;

    /** 问题描述。 */
    private String problem;

    /** 问题证据。 */
    private String evidence;

    /** 受影响正文范围。 */
    private String affectedRange;

    /** 返修意图。 */
    private String repairIntent;

    /** REQUIRED 阻断通过，OPTIONAL 不阻断通过。 */
    private RepairPriority priority;
}
