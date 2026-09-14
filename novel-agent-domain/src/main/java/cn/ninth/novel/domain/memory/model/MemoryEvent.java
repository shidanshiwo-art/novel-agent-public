package cn.ninth.novel.domain.memory.model;

/**
 * 已接受证据支持的历史发生记录。
 *
 * <p>Event 只记录发生过什么以及可回溯的来源，不携带 Fact 的当前有效状态，
 * 也没有任何会原地改写历史的操作。</p>
 */
public record MemoryEvent(
        String eventId,
        String description,
        String chapterVersion,
        String evidenceRange,
        String storyTime
) {

    public MemoryEvent {
        eventId = required(eventId, "eventId");
        description = required(description, "description");
        chapterVersion = optional(chapterVersion);
        evidenceRange = optional(evidenceRange);
        storyTime = optional(storyTime);
    }

    /** 适用于尚未需要展开章节来源字段的领域测试或内存装配。 */
    public MemoryEvent(String eventId, String description) {
        this(eventId, description, null, null, null);
    }

    public String getEventId() {
        return eventId;
    }

    public String getDescription() {
        return description;
    }

    public String getChapterVersion() {
        return chapterVersion;
    }

    public String getEvidenceRange() {
        return evidenceRange;
    }

    public String getStoryTime() {
        return storyTime;
    }

    private static String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
