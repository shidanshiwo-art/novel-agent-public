package cn.ninth.novel.infrastructure.checkpoint;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.CheckpointStateCodec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ChapterCheckpointStateCodec
 *
 * @author ninth
 * @date 2026/8/25
 * @description
 */
@Service
public class ChapterCheckpointStateCodec implements CheckpointStateCodec {
    private static final int SCHEMA_VERSION = 2;
    private static final Map<String, Class<?>> TYPED_KEYS = Map.of(
            ChapterGraphKeys.CONTEXT, ChapterContextAggregate.class,
            ChapterGraphKeys.REVIEW_REPORT, ReviewReportVO.class,
            ChapterGraphKeys.MEMORY, ChapterMemoryVO.class,
            ChapterGraphKeys.STORY_STATE_SNAPSHOT, StoryStateSnapshot.class
    );

    private final ObjectMapper objectMapper;

    public ChapterCheckpointStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encode(Map<String, Object> state) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.set("state", objectMapper.valueToTree(state));
        return objectMapper.writeValueAsString(root);
    }

    @Override
    public Map<String, Object> decode(String json) {
        JsonNode root = objectMapper.readTree(json);
        if (!root.isObject()) {
            throw new IllegalArgumentException("checkpoint 必须是 JSON object");
        }
        int version = root.path("schemaVersion").asInt();
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的 checkpoint schemaVersion: " + version);
        }

        JsonNode stateNode = root.get("state");
        if (stateNode == null || !stateNode.isObject()) {
            throw new IllegalArgumentException("checkpoint 缺少 state object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        stateNode.properties().forEach(entry -> {
            Class<?> targetType = TYPED_KEYS.get(entry.getKey());
            Object value = targetType == null
                    ? objectMapper.treeToValue(entry.getValue(), Object.class)
                    : objectMapper.treeToValue(entry.getValue(), targetType);
            result.put(entry.getKey(), value);
        });
        return result;
    }
}
