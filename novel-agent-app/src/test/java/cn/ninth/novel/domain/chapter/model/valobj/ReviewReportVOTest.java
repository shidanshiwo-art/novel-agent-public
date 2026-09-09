package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewReportVOTest {

    @Test
    void shouldReturnTrueWhenReportContainsBlockerIssue() {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(issue(SeverityEnum.MINOR), issue(SeverityEnum.BLOCKER)))
                .build();

        assertTrue(report.hasBlock());
    }

    @Test
    void shouldReturnFalseWhenReportDoesNotContainBlockerIssue() {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(issue(SeverityEnum.MAJOR), issue(SeverityEnum.MINOR)))
                .build();

        assertFalse(report.hasBlock());
        assertTrue(report.requiresRevision());
    }

    @Test
    void shouldNotRequireRevisionForMinorIssueOnly() {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(issue(SeverityEnum.MINOR)))
                .build();

        assertFalse(report.requiresRevision());
    }

    @Test
    void shouldReturnFalseWhenIssueListIsEmpty() {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(Collections.emptyList())
                .build();

        assertFalse(report.hasBlock());
    }

    @Test
    void shouldReturnFalseWhenIssueListIsNull() {
        ReviewReportVO report = ReviewReportVO.builder().build();

        assertFalse(report.hasBlock());
    }

    private ReviewIssueVO issue(SeverityEnum severity) {
        return ReviewIssueVO.builder()
                .severity(severity)
                .build();
    }
}
