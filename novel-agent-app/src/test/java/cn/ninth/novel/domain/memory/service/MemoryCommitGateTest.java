package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.adapter.repository.MemoryCommitRepository;
import cn.ninth.novel.domain.memory.model.CanonicalEvent;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryCommitPlan;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemoryCommitResult;
import cn.ninth.novel.domain.memory.model.MemoryConflict;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryCommitGateTest {

    private static final String CONTENT = "林舟打开门，发现钥匙在桌上。";

    @Test
    void shouldRejectCommitWithoutEvidence() {
        RecordingRepository repository = new RecordingRepository();
        MemorySourceVersion version = version("chapter-v1", CONTENT);

        assertThatThrownBy(() -> gate(repository).commit(new MemoryCommitRequest(
                "gate-story", 1, CONTENT, version,
                List.of(), List.of(), List.of(), List.of(), false, "commit-no-evidence")))
                .isInstanceOf(MemoryCommitRejectedException.class)
                .satisfies(error -> assertThat(((MemoryCommitRejectedException) error).conflicts())
                        .anyMatch(conflict -> conflict.code().equals(MemoryCommitGate.MISSING_EVIDENCE)));

        System.out.printf("Gate 无 evidence：拒绝，repositoryCalls=%d%n", repository.calls);
        assertThat(repository.calls).isZero();
    }

    @Test
    void shouldRejectStaleCandidateAgainstFinalVersion() {
        RecordingRepository repository = new RecordingRepository();
        String revisedContent = "林舟打开门，发现钥匙已经不在桌上。";
        MemorySourceVersion oldVersion = version("chapter-v1", CONTENT);
        MemoryCandidate candidate = readyCandidate(oldVersion, CONTENT, MemoryCandidateType.FACT);
        MemorySourceVersion revisedVersion = version("chapter-v2", revisedContent);

        MemoryCommitRejectedException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> gate(repository).commit(MemoryCommitRequest.forCandidates(
                        "gate-story", 1, revisedContent, revisedVersion, List.of(candidate))),
                MemoryCommitRejectedException.class);

        System.out.printf("Gate stale candidate：拒绝，conflicts=%s%n", error.conflicts());
        assertThat(error.conflicts()).anyMatch(
                conflict -> conflict.code().equals(MemoryCommitGate.VERSION_MISMATCH));
        assertThat(repository.calls).isZero();
    }

    @Test
    void shouldRejectEvidenceOutsideFinalText() {
        RecordingRepository repository = new RecordingRepository();
        MemorySourceVersion version = version("chapter-v1", CONTENT);
        MemoryCandidate candidate = new MemoryCandidate(
                "candidate-out-of-range", version,
                new cn.ninth.novel.domain.memory.model.MemoryEvidenceRef(0, CONTENT.length() + 1),
                MemoryCandidateType.EVENT,
                cn.ninth.novel.domain.memory.model.MemoryCandidateStatus.READY_FOR_GATE);

        MemoryCommitRejectedException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> gate(repository).commit(MemoryCommitRequest.forCandidates(
                        "gate-story", 1, CONTENT, version, List.of(candidate))),
                MemoryCommitRejectedException.class);

        System.out.printf("Gate evidence 越界：拒绝，conflicts=%s%n", error.conflicts());
        assertThat(error.conflicts()).anyMatch(
                conflict -> conflict.code().equals(MemoryCommitGate.EVIDENCE_OUT_OF_RANGE));
        assertThat(repository.calls).isZero();
    }

    @Test
    void shouldAllowNonBlockingConflictButNeverBypassGate() {
        RecordingRepository repository = new RecordingRepository();
        MemorySourceVersion version = version("chapter-v1", CONTENT);
        MemoryCandidate candidate = readyCandidate(version, CONTENT, MemoryCandidateType.EVENT);
        MemoryCommitRequest request = new MemoryCommitRequest(
                "gate-story", 1, CONTENT, version, List.of(candidate), List.of(), List.of(),
                List.of(MemoryConflict.nonBlocking("TIME_AMBIGUOUS", "时间表达略有模糊")),
                true, "commit-non-blocking");

        MemoryCommitResult result = gate(repository).commit(request);

        System.out.printf("Gate NON_BLOCKING：允许，events=%d, repositoryCalls=%d%n",
                result.eventCount(), repository.calls);
        assertThat(repository.calls).isEqualTo(1);
        assertThat(repository.lastPlan.nonBlockingConflicts())
                .extracting(MemoryConflict::code)
                .containsExactly("TIME_AMBIGUOUS");
        assertThat(repository.lastPlan.events()).hasSize(1);
    }

    @Test
    void shouldRejectBlockingConflictBeforeRepositoryAndAcceptCurrentCannotBypass() {
        RecordingRepository repository = new RecordingRepository();
        MemorySourceVersion version = version("chapter-v1", CONTENT);
        MemoryCandidate candidate = readyCandidate(version, CONTENT, MemoryCandidateType.FACT);
        MemoryCommitRequest request = new MemoryCommitRequest(
                "gate-story", 1, CONTENT, version, List.of(candidate), List.of(), List.of(),
                List.of(MemoryConflict.blocking("WORLD_RULE_VIOLATION", "正文违反世界规则")),
                true, "commit-blocking");

        assertThatThrownBy(() -> gate(repository).commit(request))
                .isInstanceOf(MemoryCommitRejectedException.class);

        System.out.printf("Gate BLOCKING + accept-current：拒绝，repositoryCalls=%d%n", repository.calls);
        assertThat(repository.calls).isZero();
    }

    @Test
    void shouldRunSemanticValidationBeforeRepository() {
        RecordingRepository repository = new RecordingRepository();
        MemorySourceVersion version = version("chapter-v1", CONTENT);
        MemoryCandidate candidate = readyCandidate(version, CONTENT, MemoryCandidateType.EVENT);
        MemorySemanticValidator validator = request -> List.of(
                MemoryConflict.blocking("MISSING_KNOWLEDGE_SOURCE", "关键知识来源未找到"));

        assertThatThrownBy(() -> new MemoryCommitGate(repository, validator).commit(
                MemoryCommitRequest.forCandidates(
                        "gate-story", 1, CONTENT, version, List.of(candidate))))
                .isInstanceOf(MemoryCommitRejectedException.class);

        System.out.printf("Gate semantic BLOCKING：拒绝，repositoryCalls=%d%n", repository.calls);
        assertThat(repository.calls).isZero();
    }

    @Test
    void shouldNotReuseOldCandidateWhenRetryUsesAnotherFinalVersion() {
        RecordingRepository repository = new RecordingRepository();
        MemorySourceVersion firstVersion = version("chapter-v1", CONTENT);
        MemoryCandidate oldCandidate = readyCandidate(firstVersion, CONTENT, MemoryCandidateType.EVENT);
        MemoryCommitRequest retry = MemoryCommitRequest.forCandidates(
                "gate-story", 1, CONTENT + " 重试正文", version("chapter-v2", CONTENT + " 重试正文"),
                List.of(oldCandidate));

        assertThatThrownBy(() -> gate(repository).commit(retry))
                .isInstanceOf(MemoryCommitRejectedException.class);

        System.out.printf("retry/checkpoint 旧 Candidate：拒绝复用，repositoryCalls=%d%n", repository.calls);
        assertThat(repository.calls).isZero();
    }

    @Test
    void canonicalEventMustNotCarryFactStatus() {
        boolean hasFactStatus = List.of(CanonicalEvent.class.getDeclaredFields()).stream()
                .map(Field::getName)
                .anyMatch(name -> name.equalsIgnoreCase("status")
                        || name.equalsIgnoreCase("factStatus"));

        System.out.printf("Canonical Event 字段：%s%n",
                List.of(CanonicalEvent.class.getDeclaredFields()).stream().map(Field::getName).toList());
        assertThat(hasFactStatus).isFalse();
    }

    private MemoryCommitGate gate(RecordingRepository repository) {
        return new MemoryCommitGate(repository);
    }

    private MemoryCandidate readyCandidate(
            MemorySourceVersion version,
            String content,
            MemoryCandidateType type
    ) {
        return MemoryCandidate.provisional(
                version, content, 0, Math.min(4, content.length()), type)
                .withStatus(cn.ninth.novel.domain.memory.model.MemoryCandidateStatus.READY_FOR_GATE);
    }

    private MemorySourceVersion version(String chapterVersion, String content) {
        return MemorySourceVersion.create(chapterVersion, content);
    }

    private static final class RecordingRepository implements MemoryCommitRepository {
        private int calls;
        private MemoryCommitPlan lastPlan;

        @Override
        public MemoryCommitResult commit(MemoryCommitPlan plan) {
            calls++;
            lastPlan = plan;
            return new MemoryCommitResult(
                    plan.commitKey(), false, plan.events().size(), plan.facts().size(),
                    plan.projections().size(), plan.outboxEntries().size());
        }
    }
}
