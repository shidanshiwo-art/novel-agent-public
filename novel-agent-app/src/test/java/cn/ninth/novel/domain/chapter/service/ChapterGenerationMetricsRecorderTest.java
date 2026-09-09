package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.adapter.repository.IGenerationMetricsRepository;
import cn.ninth.novel.domain.chapter.model.entity.GenerationMetricsDO;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsDelta;
import cn.ninth.novel.domain.chapter.model.valobj.GenerationMetricsSummaryVO;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 验证指标采集器按 generation session 聚合，且采集异常不影响主流程。 */
class ChapterGenerationMetricsRecorderTest {

    @Test
    void shouldAggregateStageDeltasIntoOneGenerationSession() {
        InMemoryMetricsRepository repository = new InMemoryMetricsRepository();
        INovelProjectRepository projectRepository = projectRepository();
        ChapterGenerationMetricsRecorder recorder = new ChapterGenerationMetricsRecorder(
                repository,
                projectRepository
        );

        recorder.startSession("session-1", "novel-001", 3);
        ChapterGraphState initialState = state("session-1", 3, 0);
        recorder.recordStageResult(
                initialState,
                "DRAFT",
                update(1, new GenerationMetricsDelta(2, 0, 0, 0, 100L, 50L, 150L))
        );
        recorder.recordStageResult(
                state("session-1", 3, 1),
                "REVIEW",
                update(1, new GenerationMetricsDelta(0, 1, 0, 0, 200L, 100L, 300L))
        );
        recorder.recordStageResult(
                state("session-1", 3, 1),
                "REVISE",
                update(2, new GenerationMetricsDelta(0, 0, 1, 0, 50L, 25L, 75L))
        );
        recorder.recordStageResult(
                state("session-1", 3, 2),
                "DRAFT",
                update(2, new GenerationMetricsDelta(1, 0, 0, 0, null, null, null))
        );
        recorder.markHumanIntervened(state("session-1", 3, 2));
        recorder.finishSession("session-1", "novel-001", 3, "你好 world");

        GenerationMetricsDO metrics = repository.find(7L, 3, "session-1");
        assertThat(repository.rows()).isEqualTo(1);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getDraftCalls()).isEqualTo(3);
        assertThat(metrics.getReviewCalls()).isEqualTo(1);
        assertThat(metrics.getReviseCalls()).isEqualTo(1);
        assertThat(metrics.getReviseRounds()).isEqualTo(1);
        assertThat(metrics.getRetryCount()).isEqualTo(2);
        assertThat(metrics.getHumanIntervened()).isTrue();
        assertThat(metrics.getInputTokens()).isEqualTo(350L);
        assertThat(metrics.getOutputTokens()).isEqualTo(175L);
        assertThat(metrics.getTotalTokens()).isEqualTo(525L);
        assertThat(metrics.getFinalWordCount()).isEqualTo(7);
        assertThat(metrics.getGenerationDurationMs()).isNotNull();
        System.out.println("[GenerationMetrics] session 聚合通过：draft=3, review=1, reviseRounds=1, retry=2, tokens=525");
    }

    @Test
    void shouldIgnoreMetricsStorageFailure() {
        IGenerationMetricsRepository repository = new IGenerationMetricsRepository() {
            @Override
            public void save(GenerationMetricsDO metrics) {
                throw new IllegalStateException("模拟指标存储故障");
            }

            @Override
            public GenerationMetricsDO find(
                    Long projectId,
                    Integer chapterNumber,
                    String generationSessionId
            ) {
                throw new IllegalStateException("模拟指标读取故障");
            }

            @Override
            public List<GenerationMetricsDO> findByChapter(
                    Long projectId,
                    Integer chapterNumber
            ) {
                return List.of();
            }

            @Override
            public GenerationMetricsSummaryVO summarizeByProject(Long projectId) {
                return GenerationMetricsSummaryVO.empty();
            }
        };
        ChapterGenerationMetricsRecorder recorder = new ChapterGenerationMetricsRecorder(
                repository,
                projectRepository()
        );

        assertThatCode(() -> recorder.startSession("session-failure", "novel-001", 3))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.recordStageResult(
                state("session-failure", 3, 0),
                "DRAFT",
                update(0, GenerationMetricsDelta.empty())
        )).doesNotThrowAnyException();
        System.out.println("[GenerationMetrics] 存储异常隔离通过：指标故障未向章节流程抛出异常");
    }

    @Test
    void shouldReleaseJvmSessionStateIdempotently() throws Exception {
        ChapterGenerationMetricsRecorder recorder = new ChapterGenerationMetricsRecorder(
                new InMemoryMetricsRepository(),
                projectRepository()
        );

        recorder.startSession("session-release", "novel-001", 3);
        recorder.releaseSession("session-release");
        recorder.releaseSession("session-release");

        assertThat(mapSize(recorder, "sessionLocks")).isZero();
        assertThat(mapSize(recorder, "sessionIdentities")).isZero();
        System.out.println("[GenerationMetrics] JVM 会话状态释放通过：sessionLocks/sessionIdentities 均已清理且重复释放安全");
    }

    @SuppressWarnings("unchecked")
    private static int mapSize(
            ChapterGenerationMetricsRecorder recorder,
            String fieldName
    ) throws Exception {
        Field field = ChapterGenerationMetricsRecorder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((ConcurrentMap<String, ?>) field.get(recorder)).size();
    }

    private static Map<String, Object> update(
            int retryCount,
            GenerationMetricsDelta delta
    ) {
        Map<String, Object> update = new HashMap<>();
        update.put(ChapterGraphKeys.RETRY_COUNT, retryCount);
        update.put(ChapterGraphKeys.GENERATION_METRICS_DELTA, delta);
        return update;
    }

    private static ChapterGraphState state(
            String workflowId,
            int chapterNumber,
            int retryCount
    ) {
        return new ChapterGraphState(Map.of(
                ChapterGraphKeys.PROJECT_CODE, "novel-001",
                ChapterGraphKeys.CHAPTER_NUMBER, chapterNumber,
                ChapterGraphKeys.WORKFLOW_ID, workflowId,
                ChapterGraphKeys.RETRY_COUNT, retryCount
        ));
    }

    private static INovelProjectRepository projectRepository() {
        return new INovelProjectRepository() {
            @Override
            public Long findProjectId(String projectCode) {
                return "novel-001".equals(projectCode) ? 7L : null;
            }

            @Override
            public NovelProjectVO createProject(NovelProjectVO project) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NovelProjectVO updateTargetChapterCount(
                    String projectCode,
                    Integer targetChapterCount
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoryBibleVO findBible(String projectCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<StoryCharacterVO> findCharacters(String projectCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasOutlines(String projectCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoryCharacterVO addCharacter(
                    String projectCode,
                    StoryCharacterVO character
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoryCharacterVO updateCharacter(
                    String projectCode,
                    String characterCode,
                    StoryCharacterVO character
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void deleteCharacter(String projectCode, String characterCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GeneratedChapterVO overwriteChapterContent(
                    String projectCode,
                    int chapterNumber,
                    String title,
                    String content,
                    int wordCount
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void deleteChapter(String projectCode, int chapterNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NovelProjectVO findProject(String projectCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<NovelProjectVO> findProjects() {
                throw new UnsupportedOperationException();
            }

            @Override
            public GeneratedChapterVO findChapter(String projectCode, int chapterNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GeneratedChapterVO> findChapters(String projectCode) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GeneratedChapterVO> searchChapters(
                    String projectCode,
                    String keyword,
                    int limit
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<VolumeChapterGroupVO> findChaptersByVolume(String projectCode) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class InMemoryMetricsRepository
            implements IGenerationMetricsRepository {

        private final Map<String, GenerationMetricsDO> metrics = new HashMap<>();

        @Override
        public void save(GenerationMetricsDO value) {
            metrics.put(key(value.getProjectId(), value.getChapterNumber(),
                    value.getGenerationSessionId()), copy(value));
        }

        @Override
        public GenerationMetricsDO find(
                Long projectId,
                Integer chapterNumber,
                String generationSessionId
        ) {
            GenerationMetricsDO value = metrics.get(key(projectId, chapterNumber, generationSessionId));
            return value == null ? null : copy(value);
        }

        @Override
        public List<GenerationMetricsDO> findByChapter(
                Long projectId,
                Integer chapterNumber
        ) {
            return metrics.values().stream()
                    .filter(value -> projectId.equals(value.getProjectId()))
                    .filter(value -> chapterNumber.equals(value.getChapterNumber()))
                    .map(ChapterGenerationMetricsRecorderTest::copy)
                    .toList();
        }

        @Override
        public GenerationMetricsSummaryVO summarizeByProject(Long projectId) {
            return GenerationMetricsSummaryVO.empty();
        }

        private int rows() {
            return metrics.size();
        }

        private String key(Long projectId, Integer chapterNumber, String sessionId) {
            return projectId + ":" + chapterNumber + ":" + sessionId;
        }
    }

    private static GenerationMetricsDO copy(GenerationMetricsDO source) {
        return GenerationMetricsDO.builder()
                .projectId(source.getProjectId())
                .chapterNumber(source.getChapterNumber())
                .generationSessionId(source.getGenerationSessionId())
                .draftCalls(source.getDraftCalls())
                .reviewCalls(source.getReviewCalls())
                .reviseCalls(source.getReviseCalls())
                .compressionCalls(source.getCompressionCalls())
                .reviseRounds(source.getReviseRounds())
                .retryCount(source.getRetryCount())
                .humanIntervened(source.getHumanIntervened())
                .generationDurationMs(source.getGenerationDurationMs())
                .startedAt(source.getStartedAt())
                .endedAt(source.getEndedAt())
                .inputTokens(source.getInputTokens())
                .outputTokens(source.getOutputTokens())
                .totalTokens(source.getTotalTokens())
                .finalWordCount(source.getFinalWordCount())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
