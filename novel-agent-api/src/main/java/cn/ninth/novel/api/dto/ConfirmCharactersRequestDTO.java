package cn.ninth.novel.api.dto;

import java.util.List;

public record ConfirmCharactersRequestDTO(
        String draftId,
        List<ConfirmCharacterDraftDTO> characters
) {
}
