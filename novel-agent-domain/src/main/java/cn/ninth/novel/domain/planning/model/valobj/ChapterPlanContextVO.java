package cn.ninth.novel.domain.planning.model.valobj;

import java.util.List;

/**
 * 单章 ChapterPlan 生成使用的精简上下文。
 *
 * <p>该对象只允许携带已经投影后的 Prompt 字段，并在边界处固定近期记忆、人物和硬规则的数量预算。</p>
 */
public record ChapterPlanContextVO(
        Integer chapterNumber,
        OutlineBriefVO bookOutline,
        OutlineBriefVO parentOutline,
        OutlineBriefVO targetOutline,
        StoryBibleBriefVO storyBible,
        ChapterPositionVO position,
        String previousChapterSummary,
        List<ChapterMemoryBriefVO> recentMemories,
        List<CharacterBriefVO> relevantCharacters,
        List<String> hardRules,
        String requirement
) {

    public static final int MAX_RECENT_MEMORIES = 5;
    public static final int MAX_RELEVANT_CHARACTERS = 4;
    public static final int MAX_HARD_RULES = 5;

    public ChapterPlanContextVO(
            Integer chapterNumber,
            OutlineBriefVO bookOutline,
            OutlineBriefVO parentOutline,
            OutlineBriefVO targetOutline,
            ChapterPositionVO position,
            String previousChapterSummary,
            List<ChapterMemoryBriefVO> recentMemories,
            List<CharacterBriefVO> relevantCharacters,
            List<String> hardRules,
            String requirement
    ) {
        this(
                chapterNumber,
                bookOutline,
                parentOutline,
                targetOutline,
                null,
                position,
                previousChapterSummary,
                recentMemories,
                relevantCharacters,
                hardRules,
                requirement
        );
    }

    public ChapterPlanContextVO {
        recentMemories = immutableWithinLimit(
                recentMemories, MAX_RECENT_MEMORIES, "recentMemories");
        relevantCharacters = immutableWithinLimit(
                relevantCharacters, MAX_RELEVANT_CHARACTERS, "relevantCharacters");
        hardRules = immutableWithinLimit(
                hardRules, MAX_HARD_RULES, "hardRules");
    }

    private static <T> List<T> immutableWithinLimit(
            List<T> values,
            int limit,
            String fieldName
    ) {
        List<T> normalized = values == null ? List.of() : List.copyOf(values);
        if (normalized.size() > limit) {
            throw new IllegalArgumentException(
                    fieldName + " 数量不能超过 " + limit);
        }
        return normalized;
    }
}
