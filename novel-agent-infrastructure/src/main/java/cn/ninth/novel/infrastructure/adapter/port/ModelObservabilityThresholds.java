package cn.ninth.novel.infrastructure.adapter.port;

final class ModelObservabilityThresholds {

    static final long SLOW_MODEL_CALL_MS = 15_000L;
    static final long SLOW_STREAM_TTFT_MS = 8_000L;
    static final long SLOW_STREAM_TOTAL_MS = 30_000L;

    private ModelObservabilityThresholds() {
    }
}
