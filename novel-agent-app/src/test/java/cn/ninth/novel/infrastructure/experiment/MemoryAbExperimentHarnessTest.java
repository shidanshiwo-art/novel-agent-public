package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.infrastructure.config.ChapterModelProperties;
import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** A/B 清单、配置指纹和机器可读输出的无联网契约测试。 */
class MemoryAbExperimentHarnessTest {

    @Test
    void shouldFreezeDistinctImmutableExperimentManifest() {
        MemoryAbExperimentManifest manifest = new MemoryAbExperimentManifest(
                "memory-ab-test",
                "novel-001",
                "novel-001-legacy-exp",
                "novel-001-v1-exp",
                1,
                10,
                11,
                16,
                "baseline-fingerprint",
                "outline-fingerprint",
                "configuration-fingerprint",
                MemoryAbExperimentHarness.WAITING_HUMAN_POLICY,
                3,
                1,
                Instant.parse("2026-09-11T00:00:00Z")
        );

        assertThat(manifest.baselineProjectCode()).isEqualTo("novel-001");
        assertThat(manifest.legacyProjectCode())
                .isNotEqualTo(manifest.v1ProjectCode())
                .isNotEqualTo(manifest.baselineProjectCode());
        System.out.printf(
                "A/B manifest contract: experiment=%s, baseline=%s, A=%s, B=%s, policy=%s%n",
                manifest.experimentId(), manifest.baselineProjectCode(),
                manifest.legacyProjectCode(), manifest.v1ProjectCode(),
                manifest.waitingHumanPolicy());
    }

    @Test
    void shouldKeepConfigurationFingerprintStableAndSerializeMetrics() {
        MemoryAbExperimentHarness harness = newHarness();
        String first = harness.configurationFingerprint();
        String second = harness.configurationFingerprint();
        MemoryAbExperimentManifest manifest = new MemoryAbExperimentManifest(
                "memory-ab-json-test",
                "baseline",
                "baseline-a",
                "baseline-b",
                1,
                10,
                11,
                16,
                "baseline",
                "outline",
                first,
                MemoryAbExperimentHarness.WAITING_HUMAN_POLICY,
                3,
                1,
                Instant.parse("2026-09-11T00:00:00Z")
        );
        MemoryAbExperimentReport report = new MemoryAbExperimentReport(
                manifest,
                List.of(new MemoryAbExperimentReport.ChapterResult(
                        11, "A", cn.ninth.novel.domain.memory.model.MemoryMode.LEGACY,
                        "run-a-11", 1, 1, 0, 100L, 200L, 300L, 1200L,
                        MemoryAbExperimentReport.MemoryContextMetrics.empty(),
                        false, "COMPLETED")));

        String json = harness.toJson(report);
        assertThat(harness.readManifest(harness.toJson(manifest))).isEqualTo(manifest);
        assertThat(first).isEqualTo(second).isNotBlank();
        assertThat(json).contains("run-a-11", "ACCEPT_CURRENT_AFTER_AUTOMATIC_REVISION_LIMIT");
        System.out.printf(
                "A/B harness contract: configFingerprint=%s, resultCount=%d, jsonLength=%d%n",
                first, report.chapters().size(), json.length());
    }

    @Test
    void shouldDescribeV1CurrentAndImprovedAsSameMemoryModeWithDifferentFlags() {
        V1ComparisonManifest manifest = new V1ComparisonManifest(
                "v1-comparison-test", "novel-001", "novel-001-current",
                "novel-001-improved", 1, 10, 11, 16,
                "baseline", "outline", "configuration",
                MemoryAbExperimentHarness.WAITING_HUMAN_POLICY, 3, 1,
                Instant.parse("2026-09-11T00:00:00Z"));

        assertThat(MemoryMode.V1).isEqualTo(MemoryMode.V1);
        assertThat(cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant.V1_CURRENT
                .reviewCheckerEnabled()).isFalse();
        assertThat(cn.ninth.novel.domain.chapter.service.workflow.ChapterGenerationVariant.V1_IMPROVED
                .reviewCheckerEnabled()).isTrue();
        assertThat(manifest.currentProjectCode()).isNotEqualTo(manifest.improvedProjectCode());
        System.out.printf(
                "V1 comparison contract：current=%s, improved=%s, memoryMode=%s, range=Ch%d-Ch%d%n",
                manifest.currentProjectCode(), manifest.improvedProjectCode(), MemoryMode.V1,
                manifest.experimentFromChapter(), manifest.experimentToChapter());
    }

    private MemoryAbExperimentHarness newHarness() {
        return new MemoryAbExperimentHarness(
                mock(DataSource.class),
                mock(PlatformTransactionManager.class),
                mock(IChapterService.class),
                new ChapterModelProperties(),
                new PlanningModelProperties(),
                new ModelTimeoutProperties(),
                new ObjectMapper()
        );
    }
}
