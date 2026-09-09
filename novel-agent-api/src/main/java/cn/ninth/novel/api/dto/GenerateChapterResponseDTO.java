package cn.ninth.novel.api.dto;

import java.util.List;

public record GenerateChapterResponseDTO(
        String workflowId,
        String status,
        String projectId,
        int chapterNumber,
        String content,
        List<String> reviewIssues,
        List<String> completedStages,
        boolean canHumanRevise) {
}
