package cn.ninth.novel.api.dto;

/** 用户编辑后的故事圣经确认请求，内容字段与正式 StoryBibleVO 对齐。 */
public record ConfirmStoryBibleRequestDTO(
        String draftId,
        String oneSentencePremise,
        String coreTheme,
        String mainConflict,
        String endingDirection,
        String worldBackground,
        String powerSystemJson,
        String hardRulesJson,
        String styleGuide
) {
}
