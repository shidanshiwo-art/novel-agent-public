package cn.ninth.novel.api.dto;

public record UpdateStoryCharacterRequestDTO(
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
