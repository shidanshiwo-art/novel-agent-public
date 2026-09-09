package cn.ninth.novel.api.dto;

public record AddStoryCharacterRequestDTO(
        String name,
        String roleType,
        String gender,
        String ageDescription,
        String appearance,
        String personality,
        String backgroundStory,
        String note
) {
}
