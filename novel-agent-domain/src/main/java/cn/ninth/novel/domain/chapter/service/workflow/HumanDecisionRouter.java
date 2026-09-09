package cn.ninth.novel.domain.chapter.service.workflow;

import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.springframework.stereotype.Component;

import java.util.Map;

import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.HUMAN_DECISION;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.HUMAN_REVISE_ROUND;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.FAILURE_MESSAGE;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.RETRY_COUNT;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.REVISE_ROUND;
import static cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys.WORKFLOW_STATUS;
import static org.bsc.langgraph4j.StateGraph.END;

/** 消费一次人工决策并选择章节图的恢复方向。 */
@Component
public class HumanDecisionRouter implements CommandAction<ChapterGraphState> {

    public static final String COMPRESSION = "COMPRESSION";
    public static final String REVISE = "REVISE";
    public static final String REVIEW = "REVIEW";
    public static final String ABORTED = "ABORTED";

    /**
     * 人工可要求返修的最大次数。
     * 达到上限后人工只能落库(PASS)或结束(ABORT)，不再接受 REVISE，
     * 由此给人工返修设熔断上限、避免 REVISE→REVIEW→HUMAN 无限往返。
     */
    public static final int MAX_HUMAN_REVISE_ROUND = 1;

    @Override
    public Command apply(ChapterGraphState state, RunnableConfig config) {
        HumanDecisionEnum decision = state.humanDecision()
                .map(this::parseDecision)
                .orElseThrow(() -> illegalParameter("HUMAN 条件边缺少 humanDecision"));

        return switch (decision) {
            case PASS -> new Command(COMPRESSION, consumedDecision());
            case REVISE -> reviseCommand(state);
            case REVIEW -> reviewCommand(state);
            case ABORT -> new Command(END, Map.of(
                    HUMAN_DECISION, "",
                    WORKFLOW_STATUS, ABORTED
            ));
        };
    }

    /**
     * 人工返修分支：
     * 1. 校验人工返修次数是否已达上限，达上限则拒绝，交前端在 PASS/ABORT 间二选一；
     * 2. 自增人工返修计数，并重置自动改稿计数，让本轮人工指令重新获得干净的改稿-评审机会。
     */
    private Command reviseCommand(ChapterGraphState state) {
        int humanReviseRound = state.humanReviseRound();
        if (humanReviseRound >= MAX_HUMAN_REVISE_ROUND) {
            // 达上限后不再接受 REVISE，返回专用错误码交前端在 PASS/ABORT 间二选一。
            throw AppException.user(
                    ResponseCode.HUMAN_REVISE_LIMIT_EXCEEDED.getCode(),
                    ResponseCode.HUMAN_REVISE_LIMIT_EXCEEDED.getMessage()
            );
        }
        return new Command(REVISE, Map.of(
                HUMAN_DECISION, "",
                HUMAN_REVISE_ROUND, humanReviseRound + 1,
                REVISE_ROUND, 0
        ));
    }

    private Command reviewCommand(ChapterGraphState state) {
        if (state.workflowStatus()
                .filter(ChapterWorkflowStatusEnum.REVIEW_FAILED.name()::equals)
                .isEmpty()) {
            throw illegalParameter("REVIEW 仅允许在 REVIEW_FAILED 状态执行");
        }
        return new Command(REVIEW, Map.of(
                HUMAN_DECISION, "",
                RETRY_COUNT, 0,
                WORKFLOW_STATUS, ChapterWorkflowStatusEnum.WAITING_HUMAN.name(),
                FAILURE_MESSAGE, ""
        ));
    }

    private HumanDecisionEnum parseDecision(String value) {
        try {
            return HumanDecisionEnum.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw illegalParameter("不支持的 humanDecision: " + value);
        }
    }

    private Map<String, Object> consumedDecision() {
        return Map.of(HUMAN_DECISION, "");
    }

    private AppException illegalParameter(String detail) {
        return AppException.internal(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                detail
        );
    }
}
