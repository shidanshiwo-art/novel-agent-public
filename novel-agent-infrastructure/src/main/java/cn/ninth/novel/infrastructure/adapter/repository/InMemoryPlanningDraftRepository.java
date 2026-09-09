package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryPlanningDraftRepository
        implements IPlanningDraftRepository {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(60);

    private final ConcurrentMap<String, PlanningDraftVO> drafts =
            new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public InMemoryPlanningDraftRepository() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    InMemoryPlanningDraftRepository(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public PlanningDraftVO save(
            String projectCode,
            String draftType,
            Object payload
    ) {
        String draftId = UUID.randomUUID().toString();
        PlanningDraftVO draft = new PlanningDraftVO(
                draftId,
                projectCode,
                draftType,
                payload,
                clock.instant().plus(ttl)
        );
        drafts.put(draftId, draft);
        return draft;
    }

    @Override
    public Optional<PlanningDraftVO> find(
            String projectCode,
            String draftId
    ) {
        PlanningDraftVO draft = drafts.get(draftId);
        if (draft == null || !draft.projectCode().equals(projectCode)) {
            return Optional.empty();
        }
        if (!draft.expiresAt().isAfter(Instant.now(clock))) {
            drafts.remove(draftId, draft);
            return Optional.empty();
        }
        return Optional.of(draft);
    }

    @Override
    public void remove(String projectCode, String draftId) {
        drafts.computeIfPresent(draftId, (key, draft) ->
                draft.projectCode().equals(projectCode) ? null : draft
        );
    }

    @Override
    public void removeByProject(String projectCode) {
        drafts.entrySet().removeIf(entry ->
                entry.getValue().projectCode().equals(projectCode)
        );
    }
}
