package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;

import java.util.List;

/**
 * 一次章节生成完成后对外提供的领域结果。
 */
public record ChapterGenerationResultVO(
        String workflowId,
        ChapterWorkflowStatusEnum status,
        String projectCode,
        int chapterNumber,
        String content,
        ReviewReportVO reviewReport,
        ChapterMemoryVO chapterMemory,
        List<String> completedStages,
        boolean canHumanRevise
) {

    public ChapterGenerationResultVO {
        completedStages = completedStages == null ? List.of() : List.copyOf(completedStages);
    }

    public ChapterGenerationResultVO(
            String workflowId,
            ChapterWorkflowStatusEnum status,
            String projectCode,
            int chapterNumber,
            String content,
            ReviewReportVO reviewReport,
            ChapterMemoryVO chapterMemory,
            List<String> completedStages
    ) {
        this(
                workflowId,
                status,
                projectCode,
                chapterNumber,
                content,
                reviewReport,
                chapterMemory,
                completedStages,
                false
        );
    }
}
