package cn.ninth.novel.domain.chapter.model.valobj;

/**
 * 结构化模型调用结果，同时保留模型返回的原始文本。
 *
 * @param value 解析后的领域结果
 * @param rawText 模型返回的原始文本，通常为结构化 JSON
 */
public record ChapterModelResponse<T>(
        T value,
        String rawText,
        ChapterTokenUsage usage
) {

    public ChapterModelResponse(T value, String rawText) {
        this(value, rawText, null);
    }
}
