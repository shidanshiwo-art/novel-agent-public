package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * REVIEW 节点的条件路由。
 * 根据审稿报告和已执行的自动改稿轮次决定进入章节整理、继续改稿或转人工处理。
 */
@Component
public class ReviewRouter implements CommandAction<ChapterGraphState> {

    public static final int MAX_REVISE_ROUND = 3;
    public static final String COMPRESSION = "COMPRESSION";
    public static final String PASS = "PASS";
    public static final String REVISE = "REVISE";
    public static final String HUMAN = "HUMAN";

    @Override
    public Command apply(ChapterGraphState state, RunnableConfig config) {
        if (state.workflowStatus()
                .filter(ChapterWorkflowStatusEnum.REVIEW_FAILED.name()::equals)
                .isPresent()
                && state.reviewReport().isEmpty()) {
            return new Command(HUMAN);
        }
        ReviewReportVO report = state.reviewReport()
                .orElseThrow(() -> illegalParameter("REVIEW 条件边缺少 reviewReport"));

        List<ReviewIssueVO> issues = report.getReviewIssueVOList();
        if (issues != null && issues.isEmpty()) {
            return new Command(COMPRESSION);
        }

        if (state.workflowStatus()
                .filter(ChapterWorkflowStatusEnum.REVIEW_FAILED.name()::equals)
                .isPresent()) {
            return new Command(HUMAN);
        }

        // 人工返修阶段有问题时：REVIEW 只产出报告不做决策，交回人工，
        // 由人工判定落库(PASS)或结束(ABORT)，避免 REVISE→REVIEW→HUMAN 往返打转。
        if (state.humanReviseRound() > 0) {
            return new Command(HUMAN);
        }

        if (!report.requiresRevision()) {
            return new Command(PASS);
        }

        int reviseRound = state.reviseRound();
        if (reviseRound < MAX_REVISE_ROUND) {
            return new Command(
                    REVISE,
                    Map.of(ChapterGraphKeys.REVISE_ROUND, reviseRound + 1)
            );
        }

        return new Command(HUMAN);
    }

    private AppException illegalParameter(String detail) {
        return AppException.internal(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                detail
        );
    }
}
