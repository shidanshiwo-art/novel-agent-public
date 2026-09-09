package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.repository.IPromptTraceRepository;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 将一次模型调用的完整 Prompt 写入单条可检索日志。 */
@Component
@Slf4j
public class PromptTraceRecorder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IPromptTraceRepository promptTraceRepository;

    public PromptTraceRecorder() {
        this(trace -> { });
    }

    @Autowired
    public PromptTraceRecorder(IPromptTraceRepository promptTraceRepository) {
        this.promptTraceRepository = promptTraceRepository;
    }

    public void record(PromptTraceRecord trace) {
        if (trace == null) {
            return;
        }
        try {
            promptTraceRepository.save(trace);
        } catch (RuntimeException exception) {
            log.warn(
                    "Prompt Trace 独立表写入失败，workflowId={}，node={}，attempt={}",
                    trace.workflowId(),
                    trace.node(),
                    trace.attempt(),
                    exception
            );
        }
        log.info(
                "PROMPT_TRACE workflowId={} node={} attempt={} promptLength={} responseLength={} durationMs={}",
                trace.workflowId(),
                trace.node(),
                trace.attempt(),
                length(trace.systemPrompt()) + length(trace.userPrompt()),
                length(trace.responseText()),
                trace.durationMs()
        );
    }

    public void recordSuccess(
            PromptTraceRecord trace,
            String responseText,
            long durationMs
    ) {
        if (trace != null) {
            record(trace.completed(responseText, true, null, null, durationMs));
        }
    }

    public void recordFailure(
            PromptTraceRecord trace,
            String responseText,
            Throwable exception,
            long durationMs
    ) {
        if (trace == null) {
            return;
        }
        AppException appException = findAppException(exception);
        String traceResponseText = responseText == null
                ? rawResponse(exception)
                : responseText;
        String errorCode = appException == null ? null : appException.getCode();
        String errorMessage = appException == null
                ? ResponseCode.UN_ERROR.getMessage()
                : ResponseCode.messageFor(
                        appException.getCode(),
                        appException.getUserMessage()
                );
        log.error(
                "模型调用失败，workflowId={}，projectCode={}，chapterNumber={}，node={}，attempt={}，errorCode={}，cause={}",
                trace.workflowId(),
                trace.projectCode(),
                trace.chapterNumber(),
                trace.node(),
                trace.attempt(),
                errorCode,
                exception == null ? null : exception.getCause(),
                exception
        );
        record(trace.completed(
                traceResponseText,
                false,
                errorCode,
                errorMessage,
                durationMs
        ));
    }

    private String rawResponse(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ChapterModelResponseException responseException) {
                return responseException.rawText();
            }
            current = current.getCause();
        }
        return null;
    }

    /** 生产端优先使用原始返回；测试或其他兼容端没有原始文本时序列化解析结果。 */
    public String responseText(String rawText, Object parsedResponse) {
        if (rawText != null) {
            return rawText;
        }
        if (parsedResponse == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(parsedResponse);
        } catch (Exception ignored) {
            return String.valueOf(parsedResponse);
        }
    }

    private AppException findAppException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AppException appException) {
                return appException;
            }
            current = current.getCause();
        }
        return null;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
