package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

/** chapter_model_trace 表持久化对象。 */
@Data
public class PromptTracePO {

    private Long id;
    private String workflowId;
    private String projectCode;
    private Integer chapterNumber;
    private String node;
    /** 0-based 尝试序号：首次调用为0，第一次重试为1。 */
    private Integer attempt;
    private String systemPrompt;
    private String userPrompt;
    private String responseText;
    private Boolean success;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createdAt;
}
