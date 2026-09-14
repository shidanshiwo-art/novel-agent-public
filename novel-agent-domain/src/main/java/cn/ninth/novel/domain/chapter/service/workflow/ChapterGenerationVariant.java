package cn.ninth.novel.domain.chapter.service.workflow;

import java.util.Locale;

/**
 * 同一 V1 Memory 路由下的章节工作流实验变体。
 *
 * <p>该变体只用于隔离 Review Checker、Known-vs-New Draft Control 和
 * Continuous Canonical Commit 三项实验变量，不改变 Memory 类型或模型参数。</p>
 */
public enum ChapterGenerationVariant {
    /** V1 当前基线：保留 V1 Recall，但不启用本次三项改进。 */
    V1_CURRENT(false, false, false, null),
    /** V1 改进组：启用三项改进。 */
    V1_IMPROVED(true, true, true, null),
    /** REVIEW reasoning A 组：沿用当前 REVIEW stage 配置。 */
    REVIEW_REASONING_ON(true, true, true, null),
    /** REVIEW reasoning B 组：只关闭 REVIEW 调用的思考开关。 */
    REVIEW_REASONING_OFF(true, true, true, Boolean.FALSE);

    private final boolean reviewCheckerEnabled;
    private final boolean knownVsNewDraftControlEnabled;
    private final boolean continuousCanonicalCommitEnabled;
    private final Boolean reviewReasoningEnabledOverride;

    ChapterGenerationVariant(
            boolean reviewCheckerEnabled,
            boolean knownVsNewDraftControlEnabled,
            boolean continuousCanonicalCommitEnabled,
            Boolean reviewReasoningEnabledOverride
    ) {
        this.reviewCheckerEnabled = reviewCheckerEnabled;
        this.knownVsNewDraftControlEnabled = knownVsNewDraftControlEnabled;
        this.continuousCanonicalCommitEnabled = continuousCanonicalCommitEnabled;
        this.reviewReasoningEnabledOverride = reviewReasoningEnabledOverride;
    }

    public boolean reviewCheckerEnabled() {
        return reviewCheckerEnabled;
    }

    public boolean knownVsNewDraftControlEnabled() {
        return knownVsNewDraftControlEnabled;
    }

    public boolean continuousCanonicalCommitEnabled() {
        return continuousCanonicalCommitEnabled;
    }

    /**
     * 返回 REVIEW 思考开关的单次调用覆盖值；null 表示沿用当前配置。
     */
    public Boolean reviewReasoningEnabledOverride() {
        return reviewReasoningEnabledOverride;
    }

    public static ChapterGenerationVariant defaultVariant() {
        return V1_IMPROVED;
    }

    public static ChapterGenerationVariant parse(String value) {
        if (value == null || value.isBlank()) {
            return defaultVariant();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "chapterGenerationVariant 必须是 V1_CURRENT、V1_IMPROVED、"
                            + "REVIEW_REASONING_ON 或 REVIEW_REASONING_OFF",
                    exception);
        }
    }
}
