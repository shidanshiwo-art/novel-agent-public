package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.memory.model.MemoryBudgetSpec;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemoryQuerySpec;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsQuery;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalObservation;
import cn.ninth.novel.domain.memory.service.LegacyFallbackMetricsCollector;
import cn.ninth.novel.domain.memory.service.LegacyFallbackRouter;
import cn.ninth.novel.domain.memory.service.MemoryContextProvider;
import cn.ninth.novel.domain.memory.service.MemoryContextProviderMetricsRecorder;
import cn.ninth.novel.domain.memory.service.MemoryRetrievalMetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 MySQL 验证生产 Memory Retrieval 观测的落库、查询和元数据边界。 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class MemoryRetrievalMetricsIntegrationTest {

    private final String projectCode = "memory-metrics-" + UUID.randomUUID();

    @Autowired
    private MemoryRetrievalMetricsRepository metricsRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        createMetricsTableIfMissing();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM memory_retrieval_metrics WHERE project_code = ?", projectCode);
    }

    @Test
    void recordsPlanDraftReviewAndSeparatesTrimReasonsWithoutContent() {
        MemoryContextProviderMetricsRecorder recorder = recorder();
        MemoryBudgetSpec budget = new MemoryBudgetSpec(16, 4, 4, 4, 4, 4);

        recorder.provide(
                new MemoryQuerySpec(MemoryProfile.PLAN,
                        Set.of(), Set.of(), Set.of("plan-loop"), Set.of(), Set.of()),
                budget,
                List.of(MemoryContextItem.of(
                        "plan-loop", MemoryContextCategory.OPEN_LOOPS,
                        "PLAN 伏笔正文", 2)),
                2, projectCode, "plan-run", MemoryMode.V1);

        recorder.provide(
                new MemoryQuerySpec(MemoryProfile.DRAFT,
                        Set.of("draft-state"), Set.of("draft-state"), Set.of("draft-loop"),
                        Set.of(), Set.of("missing-explicit-item")),
                new MemoryBudgetSpec(3, 4, 4, 4, 4, 4),
                List.of(
                        MemoryContextItem.of("draft-state", MemoryContextCategory.CURRENT_STATES,
                                "DRAFT 状态正文", 3),
                        MemoryContextItem.of("draft-loop", MemoryContextCategory.OPEN_LOOPS,
                                "DRAFT 伏笔正文", 2),
                        MemoryContextItem.of("draft-summary", MemoryContextCategory.CONSOLIDATED,
                                "DRAFT 规则过滤正文", 1)),
                3, projectCode, "draft-run", MemoryMode.V1);

        recorder.provide(
                MemoryQuerySpec.review(),
                budget,
                List.of(MemoryContextItem.of(
                        "review-evidence", MemoryContextCategory.EPISODES,
                        "REVIEW 证据正文", 4)),
                4, projectCode, "review-run", MemoryMode.V1);

        List<MemoryRetrievalObservation> observations = metricsRepository.find(
                new MemoryRetrievalMetricsQuery(projectCode, null, null, null));
        MemoryRetrievalObservation draft = observations.stream()
                .filter(item -> item.profile() == MemoryProfile.DRAFT)
                .findFirst()
                .orElseThrow();

        String storedMetadata = jdbcTemplate.queryForObject(
                "SELECT context_items_json FROM memory_retrieval_metrics "
                        + "WHERE project_code = ? AND profile = 'DRAFT'",
                String.class,
                projectCode);
        System.out.printf(
                "Memory metrics profiles=%s draft(candidate=%d,filtered=%d,selected=%d,trimmed=%d,notFound=%d,intentional=%d,budget=%d) metadataBytes=%d%n",
                observations.stream().map(item -> item.profile().name()).toList(),
                draft.candidateCount(), draft.filteredCount(), draft.selectedCount(),
                draft.trimmedCount(), draft.notFoundCount(), draft.intentionalTrimCount(),
                draft.budgetTrimCount(), storedMetadata.length());

        assertThat(observations).extracting(item -> item.profile())
                .containsExactly(MemoryProfile.PLAN, MemoryProfile.DRAFT, MemoryProfile.REVIEW);
        assertThat(draft.route().memoryMode()).isEqualTo("V1");
        assertThat(draft.route().canonicalRequested()).isTrue();
        assertThat(draft.route().canonicalHit()).isTrue();
        assertThat(draft.notFoundCount()).isEqualTo(1);
        assertThat(draft.filteredCount()).isEqualTo(2);
        assertThat(draft.selectedCount()).isEqualTo(1);
        assertThat(draft.trimmedCount()).isEqualTo(1);
        assertThat(draft.intentionalTrimCount()).isEqualTo(1);
        assertThat(draft.budgetTrimCount()).isEqualTo(1);
        assertThat(storedMetadata).doesNotContain("DRAFT 状态正文", "DRAFT 伏笔正文");
        assertThat(storedMetadata).contains("draft-state", "BUDGET_TRIM", "INTENTIONAL_TRIM");
    }

    @Test
    void recordsLegacyFallbackSeparatelyFromRetrievalMiss() {
        MemoryContextProviderMetricsRecorder recorder = recorder();
        LegacyFallbackRouter router = new LegacyFallbackRouter(
                recorder, new LegacyFallbackMetricsCollector());
        MemoryContextItem legacyLoop = MemoryContextItem.bridge(
                "legacy-loop", MemoryContextCategory.OPEN_LOOPS,
                "Legacy fallback 正文", 5);

        router.provide(
                MemoryMode.V1,
                new MemoryQuerySpec(MemoryProfile.DRAFT),
                MemoryBudgetSpec.defaultP0(),
                List.of(),
                () -> List.of(legacyLoop),
                6,
                true,
                false,
                true,
                false,
                projectCode,
                "fallback-run");

        MemoryRetrievalObservation observation = metricsRepository.find(
                        new MemoryRetrievalMetricsQuery(
                                projectCode, 6, MemoryProfile.DRAFT, "fallback-run"))
                .stream()
                .findFirst()
                .orElseThrow();
        System.out.printf(
                "Legacy fallback route requested=%s hit=%s retrievalMiss=%d source=%s%n",
                observation.route().legacyFallbackRequested(),
                observation.route().legacyFallbackHit(),
                observation.retrievalMissCount(),
                observation.items().stream()
                        .map(item -> item.canonicalOrLegacy() + ":" + item.decision())
                        .toList());

        assertThat(observation.route().canonicalRequested()).isTrue();
        assertThat(observation.route().canonicalHit()).isFalse();
        assertThat(observation.route().legacyFallbackRequested()).isTrue();
        assertThat(observation.route().legacyFallbackHit()).isTrue();
        assertThat(observation.retrievalMissCount()).isZero();
        assertThat(observation.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.canonicalOrLegacy()).isEqualTo("LEGACY");
                    assertThat(item.selected()).isTrue();
                    assertThat(item.trimmed()).isFalse();
                });
    }

    private MemoryContextProviderMetricsRecorder recorder() {
        return new MemoryContextProviderMetricsRecorder(
                new MemoryContextProvider(),
                new MemoryRetrievalMetricsCollector(),
                metricsRepository);
    }

    private void createMetricsTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_retrieval_metrics (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    project_code VARCHAR(64) NOT NULL,
                    chapter_number INT UNSIGNED NOT NULL,
                    profile VARCHAR(16) NOT NULL,
                    generation_id VARCHAR(64) DEFAULT NULL,
                    memory_mode VARCHAR(32) NOT NULL,
                    canonical_requested BOOLEAN NOT NULL,
                    canonical_hit BOOLEAN NOT NULL,
                    legacy_fallback_requested BOOLEAN NOT NULL,
                    legacy_fallback_hit BOOLEAN NOT NULL,
                    candidate_count INT UNSIGNED NOT NULL,
                    filtered_count INT UNSIGNED NOT NULL,
                    selected_count INT UNSIGNED NOT NULL,
                    trimmed_count INT UNSIGNED NOT NULL,
                    retrieval_latency_ms BIGINT UNSIGNED NOT NULL,
                    estimated_tokens BIGINT UNSIGNED NOT NULL,
                    not_found_count INT UNSIGNED NOT NULL DEFAULT 0,
                    retrieval_miss_count INT UNSIGNED NOT NULL DEFAULT 0,
                    intentional_trim_count INT UNSIGNED NOT NULL DEFAULT 0,
                    budget_trim_count INT UNSIGNED NOT NULL DEFAULT 0,
                    context_items_json JSON NOT NULL,
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (id),
                    KEY idx_memory_retrieval_project_chapter
                        (project_code, chapter_number, created_at),
                    KEY idx_memory_retrieval_profile
                        (project_code, profile, chapter_number, created_at),
                    KEY idx_memory_retrieval_generation
                        (project_code, generation_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
