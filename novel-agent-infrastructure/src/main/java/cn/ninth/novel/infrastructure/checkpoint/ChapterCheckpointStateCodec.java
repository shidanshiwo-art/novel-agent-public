package cn.ninth.novel.infrastructure.checkpoint;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.QualityContext;
import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.RepairPlan;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.checkpoint.CheckpointStateCodec;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
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
            ChapterGraphKeys.REVIEW_CONTEXT, ReviewContext.class,
            ChapterGraphKeys.REPAIR_PLAN, RepairPlan.class,
            ChapterGraphKeys.REMAINING_REPAIR_PLAN, RepairPlan.class,
            ChapterGraphKeys.REGRESSION_RESULT, RegressionCheckResult.class,
            ChapterGraphKeys.MEMORY, ChapterMemoryVO.class,
            ChapterGraphKeys.STORY_STATE_SNAPSHOT, StoryStateSnapshot.class,
            ChapterGraphKeys.SOURCE_VERSION, MemorySourceVersion.class
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
            Object value = decodeValue(entry.getKey(), entry.getValue());
            result.put(entry.getKey(), value);
        });
        return result;
    }

    private Object decodeValue(String key, JsonNode valueNode) {
        if (ChapterGraphKeys.MEMORY_CANDIDATES.equals(key)) {
            MemoryCandidate[] candidates = objectMapper.treeToValue(
                    valueNode, MemoryCandidate[].class);
            return candidates == null ? java.util.List.of() : java.util.List.of(candidates);
        }
        if (ChapterGraphKeys.CONTINUITY_FINDINGS.equals(key)) {
            ContinuityFinding[] findings = objectMapper.treeToValue(
                    valueNode, ContinuityFinding[].class);
            return findings == null ? java.util.List.of() : java.util.List.of(findings);
        }
        if (ChapterGraphKeys.CONFLICT_CANDIDATES.equals(key)) {
            ConflictCandidate[] candidates = objectMapper.treeToValue(
                    valueNode, ConflictCandidate[].class);
            return candidates == null ? java.util.List.of() : java.util.List.of(candidates);
        }
        if (ChapterGraphKeys.REVIEW_CONTEXT.equals(key)) {
            return decodeReviewContext(valueNode);
        }
        if (ChapterGraphKeys.QUALITY_FINDINGS.equals(key)) {
            QualityFinding[] findings = objectMapper.treeToValue(
                    valueNode, QualityFinding[].class);
            return findings == null ? java.util.List.of() : java.util.List.of(findings);
        }
        Class<?> targetType = TYPED_KEYS.get(key);
        return targetType == null
                ? objectMapper.treeToValue(valueNode, Object.class)
                : objectMapper.treeToValue(valueNode, targetType);
    }

    /** ReviewContext 的两个 Object 投影需要在恢复时重新变回领域类型。 */
    private ReviewContext decodeReviewContext(JsonNode valueNode) {
        ReviewContext context = objectMapper.treeToValue(valueNode, ReviewContext.class);
        JsonNode continuityNode = valueNode.get("continuityContext");
        if (continuityNode != null && continuityNode.isObject()
                && continuityNode.has("profile")) {
            context.setContinuityContext(
                    objectMapper.treeToValue(continuityNode, MemoryContextPack.class));
        }
        JsonNode qualityNode = valueNode.get("qualityContext");
        if (qualityNode != null && qualityNode.isObject()
                && (qualityNode.has("currentArc")
                || qualityNode.has("relevantCharacters")
                || qualityNode.has("arcGoal"))) {
            context.setQualityContext(objectMapper.treeToValue(qualityNode, QualityContext.class));
        }
        return context;
    }
}
