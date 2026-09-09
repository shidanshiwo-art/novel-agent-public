package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRouterTest {

    private final ReviewRouter router = new ReviewRouter();

    @Test
    void shouldReturnToHumanWithoutReadingMissingReportAfterReviewFailure() throws Exception {
        Command command = router.apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.WORKFLOW_STATUS, "REVIEW_FAILED"
        )), RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.HUMAN);
        System.out.println("REVIEW_FAILED 路由检查：缺少有效审稿报告时仍返回 HUMAN 检查点");
    }

    @Test
    void shouldReviseMajorIssueBeforeAutomaticLimit() throws Exception {
        Command command = router.apply(state(SeverityEnum.MAJOR, 0), RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.REVISE);
        assertThat(command.update()).containsEntry(ChapterGraphKeys.REVISE_ROUND, 1);
    }

    @Test
    void shouldGoToCompressionWhenReviewHasNoIssues() throws Exception {
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.REVIEW_REPORT,
                ReviewReportVO.builder().reviewIssueVOList(List.of()).build(),
                ChapterGraphKeys.HUMAN_REVISE_ROUND, 1
        ));

        Command command = router.apply(state, RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.COMPRESSION);
        System.out.println("无问题 Review 路由检查：issues 为空时直接进入 COMPRESSION，不再进入 HUMAN");
    }

    @Test
    void shouldPreferCompressionWhenFailedStateCarriesAnEmptyReport() throws Exception {
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.WORKFLOW_STATUS, "REVIEW_FAILED",
                ChapterGraphKeys.REVIEW_REPORT,
                ReviewReportVO.builder().reviewIssueVOList(List.of()).build()
        ));

        Command command = router.apply(state, RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.COMPRESSION);
        System.out.println("Review 空报告优先级检查：issues 为空时即使带有旧 REVIEW_FAILED 状态也直达 COMPRESSION");
    }

    @Test
    void shouldSendMajorIssueToHumanAtAutomaticLimit() throws Exception {
        Command command = router.apply(
                state(SeverityEnum.MAJOR, ReviewRouter.MAX_REVISE_ROUND),
                RunnableConfig.empty()
        );

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.HUMAN);
    }

    @Test
    void shouldPassMinorIssue() throws Exception {
        Command command = router.apply(state(SeverityEnum.MINOR, 0), RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.PASS);
    }

    @Test
    void shouldReturnToHumanWithoutDecidingDuringHumanRevisePhase() throws Exception {
        // 人工返修阶段(humanReviseRound>0)：无论严重度与自动改稿计数，REVIEW 只出报告并交回人工。
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.REVIEW_REPORT,
                ReviewReportVO.builder()
                        .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                                .severity(SeverityEnum.MAJOR)
                                .build()))
                        .build(),
                ChapterGraphKeys.REVISE_ROUND, 0,
                ChapterGraphKeys.HUMAN_REVISE_ROUND, 1
        ));

        Command command = router.apply(state, RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(ReviewRouter.HUMAN);
        assertThat(command.update()).doesNotContainKey(ChapterGraphKeys.REVISE_ROUND);
    }

    private ChapterGraphState state(SeverityEnum severity, int reviseRound) {
        return new ChapterGraphState(Map.of(
                ChapterGraphKeys.REVIEW_REPORT,
                ReviewReportVO.builder()
                        .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                                .severity(severity)
                                .build()))
                        .build(),
                ChapterGraphKeys.REVISE_ROUND,
                reviseRound
        ));
    }
}
