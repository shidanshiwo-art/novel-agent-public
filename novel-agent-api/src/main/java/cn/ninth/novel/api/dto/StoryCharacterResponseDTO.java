package cn.ninth.novel.api.dto;

public record StoryCharacterResponseDTO(
        String characterCode,
        String name,
        String roleType,
        String gender,
        String ageDescription,
        String personality,
        String appearance,
        String backgroundStory,
        String note,
        String currentStateJson,
        String lifeStatus,
        String status,
        String warning
) {
}
