package cn.ninth.novel.api.dto;

public record ConfirmCharacterDraftDTO(
        String name,
        String role,
        String gender,
        String ageDescription,
        String appearance,
        String personality,
        String backgroundStory,
        String note
) {
}
