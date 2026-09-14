package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 章节审稿报告值对象。
 * REVIEW 节点对当前草稿完成一次审查后生成本报告，
 * 编排层通过报告中的问题严重度决定是否进入 REVISE 节点。
 *
 * @author ninth
 * @date 2026/08/19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReviewReportVO {

    /** 本次审稿发现的问题列表；没有问题时应为空列表。 */
    List<ReviewIssueVO> reviewIssueVOList;

    /**
     * 判断报告中是否存在必须返修的硬伤。
     *
     * @return 存在严重度为 BLOCKER 的问题时返回 {@code true}，否则返回 {@code false}
     */
    public boolean hasBlock() {
        return hasSeverityAtLeast(SeverityEnum.BLOCKER);
    }

    /** 是否存在需要自动返修或人工确认的问题。 */
    public boolean requiresRevision() {
        return hasSeverityAtLeast(SeverityEnum.MAJOR);
    }

    /**
     * 将节点内的确定性检查追加到模型审核结果中。
     * 同一问题按分类、描述和当前正文证据去重，避免模型与 deterministic check 重复报告。
     */
    public ReviewReportVO withAdditionalIssues(Collection<ReviewIssueVO> additionalIssues) {
        if (additionalIssues == null || additionalIssues.isEmpty()) {
            return this;
        }
        List<ReviewIssueVO> merged = new ArrayList<>();
        if (reviewIssueVOList != null) {
            merged.addAll(reviewIssueVOList);
        }
        for (ReviewIssueVO issue : additionalIssues) {
            if (issue != null && merged.stream().noneMatch(existing -> sameIssue(existing, issue))) {
                merged.add(issue);
            }
        }
        return new ReviewReportVO(merged);
    }

    private boolean sameIssue(ReviewIssueVO left, ReviewIssueVO right) {
        return left != null && right != null
                && java.util.Objects.equals(left.getCategory(), right.getCategory())
                && java.util.Objects.equals(left.getDescription(), right.getDescription())
                && java.util.Objects.equals(left.getEvidence(), right.getEvidence());
    }

    private boolean hasSeverityAtLeast(SeverityEnum threshold) {
        if (reviewIssueVOList == null || reviewIssueVOList.isEmpty()) {
            return false;
        }

        for (ReviewIssueVO reviewIssueVO : reviewIssueVOList) {
            if (reviewIssueVO != null
                    && reviewIssueVO.getSeverity() != null
                    && reviewIssueVO.getSeverity().getRank() >= threshold.getRank()) {
                return true;
            }
        }

        return false;
    }

}
