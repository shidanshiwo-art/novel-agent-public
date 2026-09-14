package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryQueryProfile;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalCategory;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalMetricsReport;
import cn.ninth.novel.domain.memory.model.MemoryRetrievalSample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Side-channel aggregation for retrieval observations.
 *
 * <p>This collector never admits, trims, reorders, archives or otherwise changes memory items.
 * Callers provide the observation after their retrieval decision has already been made.</p>
 */
public final class MemoryRetrievalMetricsCollector {

    private final List<MemoryRetrievalSample> samples = new ArrayList<>();

    public synchronized void record(MemoryRetrievalSample sample) {
        samples.add(Objects.requireNonNull(sample, "sample 不能为空"));
    }

    public synchronized List<MemoryRetrievalSample> samples() {
        return List.copyOf(samples);
    }

    public synchronized MemoryRetrievalMetricsReport report(
            MemoryQueryProfile profile,
            int chapterCount) {
        Objects.requireNonNull(profile, "profile 不能为空");
        List<MemoryRetrievalSample> matching = samples.stream()
                .filter(sample -> sample.profile() == profile)
                .filter(sample -> sample.chapterCount() == chapterCount)
                .toList();
        if (matching.isEmpty()) {
            return MemoryRetrievalMetricsReport.empty(profile, chapterCount);
        }
        return aggregate(profile, chapterCount, matching);
    }

    public synchronized List<MemoryRetrievalMetricsReport> reportAll() {
        Set<ReportKey> keys = samples.stream()
                .map(sample -> new ReportKey(sample.profile(), sample.chapterCount()))
                .sorted(Comparator.comparingInt(ReportKey::chapterCount)
                        .thenComparing(key -> key.profile().ordinal()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return keys.stream()
                .map(key -> report(key.profile(), key.chapterCount()))
                .toList();
    }

    private MemoryRetrievalMetricsReport aggregate(
            MemoryQueryProfile profile,
            int chapterCount,
            List<MemoryRetrievalSample> matching) {
        List<Long> latencies = matching.stream()
                .map(MemoryRetrievalSample::retrievalLatencyMillis)
                .sorted()
                .toList();
        long comparableRequests = matching.stream()
                .filter(MemoryRetrievalSample::legacyComparable)
                .count();
        long legacyMisses = matching.stream()
                .filter(MemoryRetrievalSample::legacyMiss)
                .count();
        EnumMap<MemoryRetrievalCategory, Long> categoryUsage =
                new EnumMap<>(MemoryRetrievalCategory.class);
        for (MemoryRetrievalCategory category : MemoryRetrievalCategory.values()) {
            categoryUsage.put(category, matching.stream()
                    .mapToLong(sample -> sample.categoryUsage().getOrDefault(category, 0))
                    .sum());
        }
        return new MemoryRetrievalMetricsReport(
                profile,
                chapterCount,
                matching.size(),
                percentile(latencies, 0.50d),
                percentile(latencies, 0.95d),
                matching.stream().mapToLong(MemoryRetrievalSample::contextTokenCount).sum(),
                matching.stream().mapToLong(MemoryRetrievalSample::contextItemCount).sum(),
                matching.stream().mapToLong(sample -> sample.selectedItems().size()).sum(),
                matching.stream().mapToLong(sample -> sample.trimmedItems().size()).sum(),
                matching.stream().mapToLong(MemoryRetrievalSample::candidateScanCount).sum(),
                matching.stream().filter(MemoryRetrievalSample::legacyFallbackHit).count(),
                legacyMisses,
                comparableRequests,
                comparableRequests == 0 ? 0.0d : (double) legacyMisses / comparableRequests,
                categoryUsage);
    }

    /** Nearest-rank percentile keeps metric output deterministic for small replay samples. */
    private static long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(0, rank - 1));
    }

    private record ReportKey(MemoryQueryProfile profile, int chapterCount) {
    }
}
