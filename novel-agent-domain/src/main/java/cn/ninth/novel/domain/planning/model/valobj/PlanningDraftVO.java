package cn.ninth.novel.domain.planning.model.valobj;

import java.time.Instant;

public record PlanningDraftVO(
        String draftId,
        String projectCode,
        String draftType,
        Object payload,
        Instant expiresAt
) {
}
