package cn.ninth.novel.domain.chapter.model.valobj;

/**
 * 单个章节节点执行产生的指标增量。
 *
 * <p>该对象只作为节点到指标旁路采集器的内部消息，不参与工作流路由。</p>
 */
public record GenerationMetricsDelta(
        int draftCalls,
        int reviewCalls,
        int reviseCalls,
        int compressionCalls,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
) {

    public static GenerationMetricsDelta empty() {
        return new GenerationMetricsDelta(0, 0, 0, 0, null, null, null);
    }

    public static GenerationMetricsDelta forCall(
            String stage,
            ChapterTokenUsage usage
    ) {
        return new GenerationMetricsDelta(
                "DRAFT".equals(stage) ? 1 : 0,
                "REVIEW".equals(stage) ? 1 : 0,
                "REVISE".equals(stage) || "REVISION".equals(stage) ? 1 : 0,
                "COMPRESSION".equals(stage) ? 1 : 0,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.totalTokens()
        );
    }

    public static GenerationMetricsDelta tokens(ChapterTokenUsage usage) {
        return new GenerationMetricsDelta(
                0,
                0,
                0,
                0,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.totalTokens()
        );
    }

    public GenerationMetricsDelta plus(GenerationMetricsDelta other) {
        if (other == null) {
            return this;
        }
        return new GenerationMetricsDelta(
                draftCalls + other.draftCalls,
                reviewCalls + other.reviewCalls,
                reviseCalls + other.reviseCalls,
                compressionCalls + other.compressionCalls,
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
