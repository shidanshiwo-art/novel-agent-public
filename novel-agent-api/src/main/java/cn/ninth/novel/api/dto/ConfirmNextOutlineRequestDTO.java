package cn.ninth.novel.api.dto;

public record ConfirmNextOutlineRequestDTO(
        String draftId,
        String title,
        String summary
) {
}
