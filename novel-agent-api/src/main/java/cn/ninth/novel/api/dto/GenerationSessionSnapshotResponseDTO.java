package cn.ninth.novel.api.dto;

import java.util.List;

/**
 * 章节生成会话恢复快照。
 */
public record GenerationSessionSnapshotResponseDTO(
        String workflowId,
        int chapterNumber,
        String status,
        String currentNode,
        String accumulatedContent,
        List<String> completedStages,
        List<String> reviewIssues,
        String failureMessage
) {

    public GenerationSessionSnapshotResponseDTO(
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
}
