package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuitySemanticResult;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySemanticDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 使用章节模型完成单候选窄范围语义复核。
 *
 * <p>Prompt 只提供当前证据、历史证据、相关事件和当前故事时间，
 * 输出协议只有 CONFLICT、NOT_CONFLICT、UNCERTAIN 三种结论。</p>
 */
@Component
public class ModelContinuitySemanticVerifier implements ContinuitySemanticVerifier {

    static final String SYSTEM_PROMPT = """
            你是小说连续性证据复核器。
            你只能复核输入中给出的一个冲突候选，不得自行扫描正文，不得提出候选之外的任何问题。
            只对照当前正文证据、历史证据、相关事件和当前故事时间，判断当前证据是否明确破坏历史状态。
            如果明确破坏，返回 CONFLICT；如果能够由事件或时间推进合理解释，返回 NOT_CONFLICT；
            如果证据不足以作出判断，返回 UNCERTAIN。

            只返回 JSON，且只能包含 decision 字段：
            {"decision":"CONFLICT"}
            """;

    private final IChapterModelPort chapterModelPort;

    @Autowired
    public ModelContinuitySemanticVerifier(IChapterModelPort chapterModelPort) {
        this.chapterModelPort = Objects.requireNonNull(chapterModelPort, "chapterModelPort 不能为空");
    }

    @Override
    public ContinuitySemanticResult verify(
            ConflictCandidate candidate,
            ReviewContext reviewContext
    ) {
        Objects.requireNonNull(candidate, "candidate 不能为空");
        String userPrompt = buildUserPrompt(candidate);
        ContinuitySemanticResult result = chapterModelPort.call(
                SYSTEM_PROMPT,
                userPrompt,
                ContinuitySemanticResult.class,
                "CONTINUITY_VALIDATION",
                1);
        if (result == null || result.decision() == null) {
            return ContinuitySemanticResult.uncertain();
        }
        return new ContinuitySemanticResult(
                ContinuitySemanticDecision.valueOf(result.decision().name()));
    }

    String buildUserPrompt(ConflictCandidate candidate) {
        StringBuilder prompt = new StringBuilder(2048);
        prompt.append("请只复核下面这一个连续性候选。\n")
                .append("候选类型：").append(typeLabel(candidate.type())).append('\n')
                .append("实体：").append(candidate.entity()).append('\n')
                .append("当前正文证据：").append(candidate.currentEvidence()).append('\n')
                .append("历史证据：").append(candidate.historicalEvidence()).append('\n')
                .append("相关事件：").append(candidate.relevantEvents().isEmpty()
                        ? "无" : String.join("；", candidate.relevantEvents())).append('\n')
                .append("当前故事时间：").append(value(candidate.currentStoryTime())).append('\n')
                .append("decision 只能是 CONFLICT、NOT_CONFLICT、UNCERTAIN 之一，"
                        + "只返回例如 {\"decision\":\"CONFLICT\"} 的 JSON。");
        return prompt.toString();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "CHARACTER_STATE" -> "人物状态";
            case "ITEM_STATE" -> "物品状态";
            case "ABILITY_RULE" -> "能力或世界规则";
            case "KNOWLEDGE" -> "知识来源";
            case "TIMELINE" -> "时间线";
            default -> "连续性";
        };
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }
}
