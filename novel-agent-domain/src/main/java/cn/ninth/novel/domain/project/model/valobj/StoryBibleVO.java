package cn.ninth.novel.domain.project.model.valobj;

public record StoryBibleVO(
        String oneSentencePremise,
        String coreTheme,
        String mainConflict,
        String endingDirection,
        String worldBackground,
        String powerSystemJson,
        String hardRulesJson,
        String styleGuide,
        String status
) {
}
