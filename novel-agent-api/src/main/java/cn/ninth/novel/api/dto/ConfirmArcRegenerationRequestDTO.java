package cn.ninth.novel.api.dto;

public record ConfirmArcRegenerationRequestDTO(
        String draftId,
        String title,
        String summary
) {
}
