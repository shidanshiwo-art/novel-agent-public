package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerateChapterRequestDTO;
import cn.ninth.novel.api.dto.GenerateChapterResponseDTO;
import cn.ninth.novel.api.dto.ResumeChapterRequestDTO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.types.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * NovelChapterController
 *
 * @author ninth
 * @date 2026/8/24
 * @description
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/novels/chapters")
public class NovelChapterController {
    private final IChapterService chapterService;

    public NovelChapterController(IChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @PostMapping("/generate")
    public Response<GenerateChapterResponseDTO> generateChapter(
            @RequestBody GenerateChapterRequestDTO request
    ) {
        return Response.success(toResponse(chapterService.generateChapter(
                request.projectId(),
                request.chapterNumber()
        )));
    }

    @PostMapping("/{workflowId}/resume")
    public Response<GenerateChapterResponseDTO> resumeChapter(
            @PathVariable String workflowId,
            @RequestBody ResumeChapterRequestDTO request
    ) {
        HumanDecisionEnum decision = HumanDecisionEnum.valueOf(request.humanDecision());
        return Response.success(toResponse(
                chapterService.resumeChapter(
                        workflowId,
                        decision,
                        request.revisionInstruction()
                )
        ));
    }

    private GenerateChapterResponseDTO toResponse(ChapterGenerationResultVO result) {
        return new GenerateChapterResponseDTO(
                result.workflowId(),
                result.status().name(),
                result.projectCode(),
                result.chapterNumber(),
                result.content(),
                result.reviewReport() == null
                        || result.reviewReport().getReviewIssueVOList() == null
                        ? List.of()
                        : result.reviewReport().getReviewIssueVOList().stream()
                                .filter(Objects::nonNull)
                                .map(ReviewIssueVO::getDescription)
                                .toList(),
                result.completedStages(),
                result.canHumanRevise()
        );
    }
}
