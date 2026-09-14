package cn.ninth.novel.domain.memory.model;

/**
 * 正文证据的可定位引用。
 *
 * <p>范围采用正文 UTF-16 字符串上的半开区间 {@code [startOffset, endOffset)}。
 * 这样既能直接调用 {@link String#substring(int, int)}，也能明确表示空范围非法。</p>
 */
public record MemoryEvidenceRef(
        int startOffset,
        int endOffset,
        String excerpt
) {

    public MemoryEvidenceRef {
        if (startOffset < 0) {
            throw new IllegalArgumentException("evidenceRange.startOffset 不能小于 0");
        }
        if (endOffset <= startOffset) {
            throw new IllegalArgumentException(
                    "evidenceRange 必须是非空半开区间 [startOffset, endOffset)");
        }
        if (excerpt != null && excerpt.isEmpty()) {
            throw new IllegalArgumentException("evidenceRange.excerpt 不能为空字符串");
        }
    }

    public MemoryEvidenceRef(int startOffset, int endOffset) {
        this(startOffset, endOffset, null);
    }

    public static MemoryEvidenceRef of(int startOffset, int endOffset) {
        return new MemoryEvidenceRef(startOffset, endOffset);
    }

    /** 从正文生成带原文片段的证据引用，并立即检查范围边界。 */
    public static MemoryEvidenceRef of(String content, int startOffset, int endOffset) {
        requireWithin(content, startOffset, endOffset);
        return new MemoryEvidenceRef(
                startOffset,
                endOffset,
                content.substring(startOffset, endOffset)
        );
    }

    public boolean isWithin(String content) {
        return content != null
                && startOffset >= 0
                && endOffset <= content.length()
                && endOffset > startOffset
                && (excerpt == null || excerpt.equals(content.substring(startOffset, endOffset)));
    }

    public String resolve(String content) {
        requireWithin(content, startOffset, endOffset);
        if (excerpt != null && !excerpt.equals(content.substring(startOffset, endOffset))) {
            throw new IllegalArgumentException("evidenceRange.excerpt 与正文不匹配");
        }
        return content.substring(startOffset, endOffset);
    }

    public void requireWithin(String content) {
        requireWithin(content, startOffset, endOffset);
        if (excerpt != null && !excerpt.equals(content.substring(startOffset, endOffset))) {
            throw new IllegalArgumentException("evidenceRange.excerpt 与正文不匹配");
        }
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public int start() {
        return startOffset;
    }

    public int end() {
        return endOffset;
    }

    private static void requireWithin(String content, int startOffset, int endOffset) {
        if (content == null) {
            throw new IllegalArgumentException("正文不能为空");
        }
        if (startOffset < 0 || endOffset <= startOffset || endOffset > content.length()) {
            throw new IllegalArgumentException(
                    "evidenceRange 超出正文范围: ["
                            + startOffset + ", " + endOffset + ")，正文长度=" + content.length());
        }
    }
}
