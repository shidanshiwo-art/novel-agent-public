package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bsc.langgraph4j.StateGraph.END;

class HumanDecisionRouterTest {

    private final HumanDecisionRouter router = new HumanDecisionRouter();

    @Test
    void shouldConsumePassDecisionAndRouteToCompression() throws Exception {
        Command command = router.apply(state(HumanDecisionEnum.PASS, 0), RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(HumanDecisionRouter.COMPRESSION);
        assertThat(command.update())
                .containsEntry(ChapterGraphKeys.HUMAN_DECISION, "");
    }

    @Test
    void shouldResetRetryBudgetAndRouteToReviewAfterReviewFailure() throws Exception {
        Command command = router.apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.HUMAN_DECISION, HumanDecisionEnum.REVIEW.name(),
                ChapterGraphKeys.RETRY_COUNT, 3,
                ChapterGraphKeys.WORKFLOW_STATUS, "REVIEW_FAILED"
        )), RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(HumanDecisionRouter.REVIEW);
        assertThat(command.update())
                .containsEntry(ChapterGraphKeys.HUMAN_DECISION, "")
                .containsEntry(ChapterGraphKeys.RETRY_COUNT, 0)
                .containsEntry(ChapterGraphKeys.WORKFLOW_STATUS, "WAITING_HUMAN");
        System.out.println("REVIEW_FAILED 可恢复决策检查：REVIEW 会清空旧重试预算并重新进入 REVIEW");
    }

    @Test
    void shouldAuthorizeHumanRevisionAndResetAutomaticRound() throws Exception {
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.HUMAN_DECISION, HumanDecisionEnum.REVISE.name(),
                ChapterGraphKeys.REVISE_ROUND, ReviewRouter.MAX_REVISE_ROUND,
                ChapterGraphKeys.HUMAN_REVISE_ROUND, 0
        ));

        Command command = router.apply(state, RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(HumanDecisionRouter.REVISE);
        assertThat(command.update())
                .containsEntry(ChapterGraphKeys.HUMAN_DECISION, "")
                .containsEntry(ChapterGraphKeys.HUMAN_REVISE_ROUND, 1)
                .containsEntry(ChapterGraphKeys.REVISE_ROUND, 0);
    }

    @Test
    void shouldRejectHumanRevisionOnceCapReached() {
        ChapterGraphState state = new ChapterGraphState(Map.of(
                ChapterGraphKeys.HUMAN_DECISION, HumanDecisionEnum.REVISE.name(),
                ChapterGraphKeys.HUMAN_REVISE_ROUND, HumanDecisionRouter.MAX_HUMAN_REVISE_ROUND
        ));

        assertThatThrownBy(() -> router.apply(state, RunnableConfig.empty()))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getCode())
                .isEqualTo(ResponseCode.HUMAN_REVISE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void shouldConsumeAbortDecisionAndMarkWorkflowAborted() throws Exception {
        Command command = router.apply(state(HumanDecisionEnum.ABORT, 0), RunnableConfig.empty());

        assertThat(command.gotoNode()).isEqualTo(END);
        assertThat(command.update())
                .containsEntry(ChapterGraphKeys.HUMAN_DECISION, "")
                .containsEntry(ChapterGraphKeys.WORKFLOW_STATUS, "ABORTED");
    }

    @Test
    void shouldRejectMissingHumanDecision() {
        assertThatThrownBy(() -> router.apply(
                new ChapterGraphState(Map.of()),
                RunnableConfig.empty()
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getCode())
                .isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
    }

    @Test
    void shouldRejectUnsupportedHumanDecisionAsIllegalParameter() {
        assertThatThrownBy(() -> router.apply(
                new ChapterGraphState(Map.of(
                        ChapterGraphKeys.HUMAN_DECISION,
                        "UNKNOWN"
                )),
                RunnableConfig.empty()
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getCode())
                .isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
    }

    private ChapterGraphState state(HumanDecisionEnum decision, int humanReviseRound) {
        return new ChapterGraphState(Map.of(
                ChapterGraphKeys.HUMAN_DECISION, decision.name(),
                ChapterGraphKeys.HUMAN_REVISE_ROUND, humanReviseRound
        ));
    }
}
