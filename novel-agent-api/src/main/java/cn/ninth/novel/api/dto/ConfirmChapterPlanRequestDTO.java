package cn.ninth.novel.api.dto;

public record ConfirmChapterPlanRequestDTO(
        String draftId,
        String title,
        String summary
) {
}
