package cn.ninth.novel.domain.chapter.model.valobj.enums;


import lombok.Getter;

/**
 * 审稿问题严重度。
 * REVIEW 节点为每条问题标注严重度，编排层据此决定是否回到 REVISE 返修。
 * 常量按严重度从高到低声明，{@code rank} 越大越严重，可用于阈值比较。
 */
@Getter
public enum SeverityEnum {

    /**
     * 硬伤，必须返修。
     * 指破坏设定一致性的问题，如已死亡人物出场、时间线倒流、违反世界规则。
     * 只要审稿报告中存在本档问题，就强制走返修路径。
     */
    BLOCKER(3, "严重"),

    /**
     * 明显缺陷，建议返修。
     * 不破坏一致性但影响章节质量，如人物动机跳跃、章节章纲规划的目标未兑现、结尾钩子过弱。
     */
    MAJOR(2, "一般"),

    /**
     * 润色项，可不返修。
     * 指文风、用词、节奏一类的细节问题。
     */
    MINOR(1, "轻微"),

    /** 模型等级缺失或无法识别时的后端兜底值，不作为模型输出选项。 */
    UNKNOWN(0, "未知");

    /** 严重度权重，越大越严重，用于「严重度不低于某档」这类阈值判断。 */
    private final int rank;

    /** 面向创作与审核任务的业务等级名称。 */
    private final String label;

    SeverityEnum(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public static SeverityEnum fromLabel(String label) {
        for (SeverityEnum severity : values()) {
            if (severity.label.equals(label)) {
                return severity;
            }
        }
        return UNKNOWN;
    }
}
