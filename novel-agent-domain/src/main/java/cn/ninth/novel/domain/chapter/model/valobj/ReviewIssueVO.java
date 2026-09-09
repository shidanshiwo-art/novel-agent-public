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

    /** 问题严重度，用于判断是否必须进入返修流程。 */
    private SeverityEnum severity;

    /** 问题分类，如时间线、人物动机、世界规则、剧情、重复、文风或结尾钩子。 */
    private String category;

    /** 对问题本身及其影响的具体说明。 */
    private String description;

    /** 支撑该问题结论的正文片段、上下文事实或位置线索。 */
    private String evidence;
}
