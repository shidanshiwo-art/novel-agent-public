package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.adapter.repository.IPromptTraceRepository;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.infrastructure.dao.IPromptTraceDao;
import cn.ninth.novel.infrastructure.dao.po.PromptTracePO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 将章节模型 Prompt Trace 写入独立调试表。 */
@Repository
public class PromptTraceRepository implements IPromptTraceRepository {

    private final IPromptTraceDao promptTraceDao;

    public PromptTraceRepository(IPromptTraceDao promptTraceDao) {
        this.promptTraceDao = promptTraceDao;
    }

    @Override
    public void save(PromptTraceRecord trace) {
        if (trace == null) {
            return;
        }
        promptTraceDao.insert(toPO(trace));
    }

    @Override
    public List<PromptTraceRecord> findByWorkflowId(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return List.of();
        }
        List<PromptTracePO> traces = promptTraceDao.selectByWorkflowId(workflowId);
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }
        return traces.stream()
                .map(this::toRecord)
                .toList();
    }

    private PromptTracePO toPO(PromptTraceRecord trace) {
        PromptTracePO po = new PromptTracePO();
        po.setWorkflowId(trace.workflowId());
        po.setProjectCode(trace.projectCode());
        po.setChapterNumber(trace.chapterNumber());
        po.setNode(trace.node());
        po.setAttempt(trace.attempt());
        po.setSystemPrompt(trace.systemPrompt());
        po.setUserPrompt(trace.userPrompt());
        po.setResponseText(trace.responseText());
        po.setSuccess(trace.success());
        po.setErrorCode(trace.errorCode());
        po.setErrorMessage(trace.errorMessage());
        po.setDurationMs(trace.durationMs());
        po.setCreatedAt(trace.createdAt() == null
                ? null
                : LocalDateTime.ofInstant(trace.createdAt(), ZoneOffset.UTC));
        return po;
    }

    private PromptTraceRecord toRecord(PromptTracePO trace) {
        return new PromptTraceRecord(
                trace.getWorkflowId(),
                trace.getProjectCode(),
                trace.getChapterNumber(),
                trace.getNode(),
                trace.getAttempt() == null ? 0 : trace.getAttempt(),
                trace.getSystemPrompt(),
                trace.getUserPrompt(),
                trace.getCreatedAt() == null
                        ? null
                        : trace.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                trace.getResponseText(),
                Boolean.TRUE.equals(trace.getSuccess()),
                trace.getErrorCode(),
                trace.getErrorMessage(),
                trace.getDurationMs() == null ? 0L : trace.getDurationMs()
        );
    }
}
