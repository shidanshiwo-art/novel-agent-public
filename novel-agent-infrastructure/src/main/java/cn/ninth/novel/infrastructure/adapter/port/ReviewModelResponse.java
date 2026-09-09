package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 模型审核响应，仅使用业务等级名称；领域报告保留后端枚举。 */
public record ReviewModelResponse(List<Issue> issues) {

    public record Issue(
            @JsonPropertyDescription("问题等级，只能为严重、一般、轻微中的一个")
            String severity,
            String category,
            String description,
            String evidence
    ) {
        ReviewIssueVO toDomain() {
            return new ReviewIssueVO(SeverityEnum.fromLabel(severity), category, description, evidence);
        }
    }

    ReviewReportVO toDomain() {
        return new ReviewReportVO(issues == null ? null : issues.stream()
                .map(issue -> issue == null ? null : issue.toDomain())
                .toList());
    }
}
