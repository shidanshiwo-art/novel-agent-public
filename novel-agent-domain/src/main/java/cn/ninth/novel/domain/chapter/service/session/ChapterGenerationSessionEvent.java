package cn.ninth.novel.domain.chapter.service.session;

import cn.ninth.novel.types.enums.ResponseCode;

import java.util.List;
import java.util.Set;

/**
 * 章节生成 Session 对外发布的轻量事件。
 *
 * @param type    业务事件类型
 * @param content DRAFT_CHUNK 的正文片段；失败和终止事件携带可读原因，其他事件为空
 * @param reviewIssues REVIEW_COMPLETED 携带的当前审稿问题列表，其他事件为空
 */
public record ChapterGenerationSessionEvent(
        ChapterGenerationEventType type,
        String content,
        List<String> reviewIssues
) {
    public static final String REVIEW_FAILURE_MESSAGE =
            "自动审稿失败，但正文已保留。你可以重新审稿、直接采用当前正文或停止本次生成。";
    public static final String DRAFT_FAILURE_MESSAGE =
            "正文生成失败，请重新生成本章。";
    public static final String REVISION_FAILURE_MESSAGE =
            "正文修改失败，请重新审稿或采用当前正文。";
    public static final String COMPRESSION_FAILURE_MESSAGE =
            "章节记忆更新失败，正文不会丢失，请重新更新。";
    public static final String PERSIST_FAILURE_MESSAGE =
            "章节保存失败，请重试。";

    public ChapterGenerationSessionEvent(
            ChapterGenerationEventType type,
            String content
    ) {
        this(type, content, null);
    }

    private static final Set<String> SAFE_GENERATION_FAILURE_MESSAGES = Set.of(
            ResponseCode.UN_ERROR.getMessage(),
            REVIEW_FAILURE_MESSAGE,
            DRAFT_FAILURE_MESSAGE,
            REVISION_FAILURE_MESSAGE,
            COMPRESSION_FAILURE_MESSAGE,
            PERSIST_FAILURE_MESSAGE
    );

    public ChapterGenerationSessionEvent {
        if (type == null) {
            throw new IllegalArgumentException("章节生成事件类型不能为空");
        }
        if (requiresContent(type) && (content == null || content.isBlank())) {
            throw new IllegalArgumentException(
                    type.name() + " 事件必须携带可读 content"
            );
        }
        if (requiresContent(type)) {
            content = safeFailureMessage(
                    type,
                    content == null ? null : content.trim()
            );
        }
        if (reviewIssues != null) {
            reviewIssues = List.copyOf(reviewIssues);
        }
    }

    private static boolean requiresContent(ChapterGenerationEventType type) {
        return switch (type) {
            case REVIEW_FAILED,
                    GENERATION_FAILED,
                    GENERATION_ABORTED,
                    GENERATION_CANCELLED -> true;
            default -> false;
        };
    }

    /**
     * 返回工作流阶段失败时允许发送给前端的固定用户文案。
     *
     * <p>阶段节点可以记录完整异常，但 SSE 事件只携带这里定义的文案。</p>
     */
    public static String safeFailureMessageForStage(ChapterGenerationEventType startedEvent) {
        if (startedEvent == null) {
            return ResponseCode.UN_ERROR.getMessage();
        }
        return switch (startedEvent) {
            case DRAFT_STARTED -> DRAFT_FAILURE_MESSAGE;
            case REVIEW_STARTED -> REVIEW_FAILURE_MESSAGE;
            case REVISION_STARTED -> REVISION_FAILURE_MESSAGE;
            case COMPRESSION_STARTED -> COMPRESSION_FAILURE_MESSAGE;
            case PERSIST_STARTED -> PERSIST_FAILURE_MESSAGE;
            default -> ResponseCode.UN_ERROR.getMessage();
        };
    }

    private static String safeFailureMessage(
            ChapterGenerationEventType type,
            String content
    ) {
        return switch (type) {
            case REVIEW_FAILED -> REVIEW_FAILURE_MESSAGE;
            case GENERATION_FAILED -> SAFE_GENERATION_FAILURE_MESSAGES.contains(content)
                    ? content
                    : ResponseCode.UN_ERROR.getMessage();
            case GENERATION_ABORTED -> "用户已结束章节生成流程";
            case GENERATION_CANCELLED -> "用户已停止章节生成流程";
            default -> ResponseCode.UN_ERROR.getMessage();
        };
    }
}
