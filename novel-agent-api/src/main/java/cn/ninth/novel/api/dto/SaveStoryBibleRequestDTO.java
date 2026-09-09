package cn.ninth.novel.api.dto;

public record SaveStoryBibleRequestDTO(
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
