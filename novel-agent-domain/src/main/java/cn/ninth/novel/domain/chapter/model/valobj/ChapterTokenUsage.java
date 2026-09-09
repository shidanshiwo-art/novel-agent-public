package cn.ninth.novel.domain.chapter.model.valobj;

/**
 * 模型一次调用返回的 Token 使用量。
 *
 * <p>各字段都允许为空。模型或供应商未返回对应值时保持为空，不根据文本长度估算。</p>
 */
public record ChapterTokenUsage(
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
) {

    public static ChapterTokenUsage of(
            Long inputTokens,
            Long outputTokens,
            Long totalTokens
    ) {
        return new ChapterTokenUsage(inputTokens, outputTokens, totalTokens);
    }

    /** 合并多次模型响应；缺失字段保持为空，不从其他字段推导。 */
    public ChapterTokenUsage plus(ChapterTokenUsage other) {
        if (other == null) {
            return this;
        }
        return new ChapterTokenUsage(
                addNullable(inputTokens, other.inputTokens),
                addNullable(outputTokens, other.outputTokens),
                addNullable(totalTokens, other.totalTokens)
        );
    }

    private Long addNullable(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }
}
