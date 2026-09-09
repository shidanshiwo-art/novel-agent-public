package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPlanningDraftRepositoryTest {

    @Test
    void shouldExpireAndIsolateDraftsByProject() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        InMemoryPlanningDraftRepository repository =
                new InMemoryPlanningDraftRepository(clock, Duration.ofMinutes(60));

        PlanningDraftVO draft = repository.save(
                "novel-001",
                "ROOT_OUTLINE",
                "payload"
        );

        assertThat(repository.find("novel-001", draft.draftId())).contains(draft);
        assertThat(repository.find("novel-002", draft.draftId())).isEmpty();

        clock.advance(Duration.ofMinutes(61));

        assertThat(repository.find("novel-001", draft.draftId())).isEmpty();
    }

    @Test
    void shouldRemoveOnlyDraftsFromTargetProject() {
        InMemoryPlanningDraftRepository repository =
                new InMemoryPlanningDraftRepository(
                        Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneId.of("UTC")),
                        Duration.ofMinutes(60)
                );
        PlanningDraftVO first = repository.save("novel-001", "ROOT_OUTLINE", "a");
        PlanningDraftVO second = repository.save("novel-002", "CHILD_OUTLINES", "b");

        repository.removeByProject("novel-001");

        assertThat(repository.find("novel-001", first.draftId())).isEmpty();
        assertThat(repository.find("novel-002", second.draftId())).contains(second);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
