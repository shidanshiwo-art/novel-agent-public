package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.types.exception.AppException;

/**
 * 结构化模型响应无法解析或为空时携带原始响应的异常。
 *
 * <p>模型响应已经从外部适配器读出，但还未形成领域对象时，
 * rawText 仍然是排查模型输出问题的唯一依据。</p>
 */
public class ChapterModelResponseException extends AppException {

    private static final long serialVersionUID = 1L;

    private final String rawText;
    private final boolean parseRetryExhausted;
    private final ChapterTokenUsage usage;

    public ChapterModelResponseException(
            String code,
            String detail,
            Throwable cause,
            String rawText
    ) {
        this(code, detail, cause, rawText, false, null);
    }

    public ChapterModelResponseException(
            String code, String detail, Throwable cause, String rawText, boolean parseRetryExhausted
    ) {
        this(code, detail, cause, rawText, parseRetryExhausted, null);
    }

    public ChapterModelResponseException(
            String code,
            String detail,
            Throwable cause,
            String rawText,
            boolean parseRetryExhausted,
            ChapterTokenUsage usage
    ) {
        super(code, detail, cause, false);
        this.rawText = rawText;
        this.parseRetryExhausted = parseRetryExhausted;
        this.usage = usage;
    }

    public boolean parseRetryExhausted() {
        return parseRetryExhausted;
    }

    public String rawText() {
        return rawText;
    }

    public ChapterTokenUsage usage() {
        return usage;
    }
}
