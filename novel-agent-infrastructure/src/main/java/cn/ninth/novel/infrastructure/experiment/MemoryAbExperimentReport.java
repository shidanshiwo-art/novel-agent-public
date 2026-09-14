package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.domain.memory.model.MemoryMode;

import java.util.List;

/** 一次 A/B 运行的机器可读结果。 */
public record MemoryAbExperimentReport(
        MemoryAbExperimentManifest manifest,
        List<ChapterResult> chapters
) {

    public MemoryAbExperimentReport {
        if (manifest == null) {
            throw new IllegalArgumentException("实验清单不能为空");
        }
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }

    /** 每个项目、每个章节一条结果；generationRunId 可为空表示尚未启动。 */
    public record ChapterResult(
            int chapter,
            String group,
            MemoryMode memoryMode,
            String generationRunId,
            int draftCalls,
            int reviewCalls,
            int reviseCalls,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Long latencyMs,
            MemoryContextMetrics memoryContext,
            boolean fallback,
            String finalStatus
    ) {

        public ChapterResult {
            if (chapter < 1 || group == null || group.isBlank()
                    || memoryMode == null || finalStatus == null || finalStatus.isBlank()) {
                throw new IllegalArgumentException("章节实验结果字段不完整");
            }
            memoryContext = memoryContext == null
                    ? MemoryContextMetrics.empty() : memoryContext;
            if (draftCalls < 0 || reviewCalls < 0 || reviseCalls < 0) {
                throw new IllegalArgumentException("模型调用次数不能为负数");
            }
        }
    }

    /** 按 PLAN/DRAFT/REVIEW 保留检索指标，避免只给一个不可解释的总数。 */
    public record MemoryContextMetrics(
            List<ProfileMetrics> profiles,
            int requestCount,
            long candidateCount,
            long filteredCount,
            long selectedCount,
            long trimmedCount,
            long estimatedTokens,
            long retrievalLatencyMs,
            boolean canonicalRequested,
            boolean canonicalHit,
            boolean fallbackRequested,
            boolean fallbackHit
    ) {

        public MemoryContextMetrics {
            profiles = profiles == null ? List.of() : List.copyOf(profiles);
            if (requestCount < 0 || candidateCount < 0 || filteredCount < 0
                    || selectedCount < 0 || trimmedCount < 0 || estimatedTokens < 0
                    || retrievalLatencyMs < 0) {
                throw new IllegalArgumentException("Memory context 指标不能为负数");
            }
        }

        public static MemoryContextMetrics empty() {
            return new MemoryContextMetrics(
                    List.of(), 0, 0L, 0L, 0L, 0L, 0L, 0L,
                    false, false, false, false);
        }
    }

    public record ProfileMetrics(
            String profile,
            int requestCount,
            long candidateCount,
            long filteredCount,
            long selectedCount,
            long trimmedCount,
            long estimatedTokens,
            long retrievalLatencyMs,
            boolean canonicalRequested,
            boolean canonicalHit,
            boolean fallbackRequested,
            boolean fallbackHit
    ) {

        public ProfileMetrics {
            if (profile == null || profile.isBlank() || requestCount < 0
                    || candidateCount < 0 || filteredCount < 0 || selectedCount < 0
                    || trimmedCount < 0 || estimatedTokens < 0 || retrievalLatencyMs < 0) {
                throw new IllegalArgumentException("Profile Memory 指标无效");
            }
        }
    }
}
