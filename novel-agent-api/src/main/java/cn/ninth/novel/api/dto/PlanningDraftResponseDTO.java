package cn.ninth.novel.api.dto;

import java.time.Instant;

public record PlanningDraftResponseDTO(
        String draftId,
        String draftType,
        Object payload,
        Instant expiresAt
) {
}
