package cn.ninth.novel.domain.chapter.service.session;

import java.util.List;

/**
 * 章节生成会话的可恢复快照。
 */
public record ChapterGenerationSessionSnapshot(
        String workflowId,
        int chapterNumber,
        String status,
        String currentNode,
        String accumulatedContent,
        List<String> completedStages,
        List<String> reviewIssues,
        String failureMessage
) {

    public ChapterGenerationSessionSnapshot(
            String workflowId,
            int chapterNumber,
            String status,
            String accumulatedContent,
            List<String> completedStages,
            List<String> reviewIssues,
            String failureMessage
    ) {
        this(
                workflowId,
                chapterNumber,
                status,
                "",
                accumulatedContent,
                completedStages,
                reviewIssues,
                failureMessage
        );
    }

    public ChapterGenerationSessionSnapshot {
        status = status == null || status.isBlank() ? "DRAFTING" : status;
        currentNode = currentNode == null ? "" : currentNode;
        accumulatedContent = accumulatedContent == null ? "" : accumulatedContent;
        completedStages = completedStages == null ? List.of() : List.copyOf(completedStages);
        reviewIssues = reviewIssues == null ? List.of() : List.copyOf(reviewIssues);
        failureMessage = failureMessage == null ? "" : failureMessage;
    }
}
