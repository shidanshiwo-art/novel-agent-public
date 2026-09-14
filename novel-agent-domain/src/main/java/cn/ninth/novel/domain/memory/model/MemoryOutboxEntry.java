package cn.ninth.novel.domain.memory.model;

/** PGVector 或其他异步派生任务使用的 MySQL outbox 事件。 */
public record MemoryOutboxEntry(
        String outboxId,
        String commitKey,
        String aggregateType,
        String aggregateId,
        String payload
) {

    public MemoryOutboxEntry {
        outboxId = required(outboxId, "outboxId");
        commitKey = required(commitKey, "commitKey");
        aggregateType = required(aggregateType, "aggregateType");
        aggregateId = required(aggregateId, "aggregateId");
        payload = required(payload, "payload");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
