package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 章节审稿问题值对象。
 * REVIEW 节点用本对象记录一条具体问题，
 * REVISE 节点根据问题的严重度、分类及原文证据进行定向修改。
 *
 * @author ninth
 * @date 2026/08/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReviewIssueVO {

    /** 业务问题类型，例如 CHARACTER_PRESENCE、ITEM_LOCATION、ABILITY_RULE。 */
    private String issueType;

    /** 问题严重度，用于判断是否必须进入返修流程。 */
    private SeverityEnum severity;

    /** 问题分类，如时间线、人物动机、世界规则、剧情、重复、文风或结尾钩子。 */
    private String category;

    /** 对问题本身及其影响的具体说明。 */
    private String description;

    /** 支撑该问题结论的正文片段、上下文事实或位置线索。 */
    private String evidence;

    /** 当前正文问题对应的来源章节；模型问题可以为空。 */
    private Integer sourceChapter;

    /** 当前正文问题对应的来源版本；版本检查失败时保留候选版本。 */
    private String sourceVersion;

    /** 支撑问题的历史原文片段；与当前正文 evidence 分开保存。 */
    private String sourceEvidence;

    /** 当前正文中的直接证据；与兼容字段 evidence 保持相同语义。 */
    private String currentEvidence;

    /** 历史事实、事件或规则中的直接证据；与兼容字段 sourceEvidence 保持相同语义。 */
    private String historicalEvidence;

    /** 引发连续性检查的角色、物品、能力或其他业务实体。 */
    private String relatedEntity;

    /** 面向业务的判定理由，区别于 category 和 description。 */
    private String reason;

    /** 问题来自代码确定性检查还是模型语义检查。 */
    private String checkerType;

    /** 保留既有四字段构造方式，兼容旧测试和调用方。 */
    public ReviewIssueVO(
            SeverityEnum severity,
            String category,
            String description,
            String evidence
    ) {
        this(
                issueTypeOf(category),
                severity,
                category,
                description,
                evidence,
                null,
                null,
                null,
                evidence,
                null,
                null,
                description,
                "SEMANTIC"
        );
    }

    /** 模型 issue 的兼容分类映射；模型返回仍只使用旧业务输出字段。 */
    private static String issueTypeOf(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return switch (category.trim()) {
            case "时间线" -> "TIMELINE";
            case "独占位置" -> "LOCATION";
            case "生存状态" -> "LIFE_STATUS";
            case "世界规则" -> "WORLD_RULE";
            case "能力规则" -> "ABILITY_RULE";
            case "知识来源" -> "KNOWLEDGE_PROVENANCE";
            case "来源/版本" -> "SOURCE_VERSION";
            default -> category.trim();
        };
    }
}
