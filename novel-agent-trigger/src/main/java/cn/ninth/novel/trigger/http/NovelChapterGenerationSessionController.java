package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerationSessionResponseDTO;
import cn.ninth.novel.api.dto.GenerationSessionSnapshotResponseDTO;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionSnapshot;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 章节生成会话接口。
 */
@RestController
@RequestMapping("/api/v1/novels/projects")
public class NovelChapterGenerationSessionController {

    private final IChapterService chapterService;

    public NovelChapterGenerationSessionController(IChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @PostMapping("/{projectCode}/chapters/{chapterNumber}/generation-sessions")
    public Response<GenerationSessionResponseDTO> createGenerationSession(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber
    ) {
        return Response.success(new GenerationSessionResponseDTO(
                chapterService.createGenerationSession(projectCode, chapterNumber)
        ));
    }

    /**
     * 查询活动会话或短期保留的最近终态快照，供 Generate 页面刷新后恢复结果。
     */
    @GetMapping("/{projectCode}/chapters/{chapterNumber}/generation-sessions")
    public Response<GenerationSessionSnapshotResponseDTO> findActiveGenerationSession(
            @PathVariable String projectCode,
            @PathVariable int chapterNumber
    ) {
        Optional<ChapterGenerationSessionSnapshot> snapshot = chapterService.findActiveGenerationSession(
                projectCode,
                chapterNumber
        );
        return Response.success(snapshot.map(this::toSnapshotResponse).orElse(null));
    }

    private GenerationSessionSnapshotResponseDTO toSnapshotResponse(
            ChapterGenerationSessionSnapshot snapshot
    ) {
        return new GenerationSessionSnapshotResponseDTO(
                snapshot.workflowId(),
                snapshot.chapterNumber(),
                snapshot.status(),
                snapshot.currentNode(),
                snapshot.accumulatedContent(),
                snapshot.completedStages(),
                snapshot.reviewIssues(),
                snapshot.failureMessage()
        );
    }
}
