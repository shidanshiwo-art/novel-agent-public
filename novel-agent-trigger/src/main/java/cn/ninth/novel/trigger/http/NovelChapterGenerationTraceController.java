package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.PromptTraceResponseDTO;
import cn.ninth.novel.domain.chapter.adapter.repository.IPromptTraceRepository;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.types.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.List;

/** 开发用途的章节生成 Prompt Trace 查询接口。 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/novels/generation-sessions")
public class NovelChapterGenerationTraceController {

    private final IPromptTraceRepository promptTraceRepository;

    public NovelChapterGenerationTraceController(
            IPromptTraceRepository promptTraceRepository
    ) {
        this.promptTraceRepository = promptTraceRepository;
    }

    @GetMapping("/{workflowId}/traces")
    public Response<List<PromptTraceResponseDTO>> traces(
            @PathVariable String workflowId
    ) {
        if (workflowId == null || workflowId.isBlank()) {
            throw AppException.user(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "查询会话标识不能为空"
            );
        }
        return Response.success(promptTraceRepository.findByWorkflowId(workflowId).stream()
                .map(this::toResponse)
                .toList());
    }

    private PromptTraceResponseDTO toResponse(PromptTraceRecord trace) {
        return new PromptTraceResponseDTO(
                trace.node(),
                trace.attempt(),
                trace.success(),
                trace.durationMs(),
                trace.errorMessage(),
                trace.systemPrompt(),
                trace.userPrompt(),
                trace.responseText()
        );
    }
}
