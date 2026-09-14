package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalChapterCandidateExtractorTest {

    @Test
    void shouldExtractEvidenceBoundCandidatesOnlyFromFinalContent() {
        String finalContent = "沈夜进入旧钟楼。残晶位于旧钟楼。买家身份仍未查明。";
        MemorySourceVersion version = MemorySourceVersion.create("final:11", finalContent);

        var candidates = new FinalChapterCandidateExtractor().extract(
                "extractor-test", 11, finalContent, version);

        System.out.printf(
                "Final candidate extraction：count=%d, types=%s, evidence=%s%n",
                candidates.size(),
                candidates.stream().map(MemoryCandidate::candidateType).toList(),
                candidates.stream().map(candidate -> candidate.evidenceRange().excerpt()).toList());

        assertThat(candidates).hasSize(3);
        assertThat(candidates).extracting(MemoryCandidate::candidateType)
                .containsExactlyInAnyOrder(
                        MemoryCandidateType.EVENT,
                        MemoryCandidateType.FACT,
                        MemoryCandidateType.OPEN_LOOP_CHANGE);
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.candidateStatus()).isEqualTo(MemoryCandidateStatus.READY_FOR_GATE);
            assertThat(candidate.sourceVersion()).isEqualTo(version);
            assertThat(candidate.evidenceRange().resolve(finalContent))
                    .isEqualTo(candidate.evidenceRange().excerpt());
        });
        assertThat(candidates.stream()
                .map(candidate -> candidate.evidenceRange().excerpt())
                .collect(java.util.stream.Collectors.toSet()))
                .doesNotContain("摘要中伪造的事实");
    }

    @Test
    void shouldRejectCandidateExtractionWhenVersionDoesNotMatchFinalContent() {
        String finalContent = "最终正文只接受当前版本证据。";
        MemorySourceVersion staleVersion = MemorySourceVersion.create(
                "stale-version", "旧正文");

        System.out.println("Final candidate extraction：stale source version 被拒绝");
        assertThatThrownBy(() -> new FinalChapterCandidateExtractor().extract(
                "extractor-test", 11, finalContent, staleVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentHash");
    }
}
