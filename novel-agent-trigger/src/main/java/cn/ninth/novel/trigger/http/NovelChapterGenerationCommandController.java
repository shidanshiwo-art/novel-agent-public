package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerationSessionCommandRequestDTO;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章节生成 Session 控制命令接口。
 */
@RestController
@RequestMapping("/api/v1/novels/generation-sessions")
public class NovelChapterGenerationCommandController {

    private final IChapterService chapterService;

    public NovelChapterGenerationCommandController(IChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @PostMapping("/{workflowId}/commands")
    public Response<Void> command(
            @PathVariable String workflowId,
            @RequestBody GenerationSessionCommandRequestDTO request
    ) {
        if (request == null || request.command() == null) {
            throw AppException.user(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "操作不能为空"
            );
        }
        switch (request.command()) {
            case "STOP" -> chapterService.stopGenerationSession(workflowId);
            case "ACCEPT" -> chapterService.acceptGenerationSession(workflowId);
            default -> throw AppException.user(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "不支持该操作，请选择停止或采用"
            );
        }
        return Response.success(null);
    }
}
