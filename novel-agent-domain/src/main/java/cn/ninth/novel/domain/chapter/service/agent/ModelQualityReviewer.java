package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.QualityContext;
import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 使用固定 QualityContext 执行一次质量审核。 */
@Component
public class ModelQualityReviewer implements QualityReviewer {

    static final String SYSTEM_PROMPT = """
            你是小说章节质量审核器。
            只检查输入正文在以下六个维度上的问题：Outline Completion、Causal Logic、
            Character Motivation、Plot Progression、Redundant Investigation、Prose Naturalness。
            不检查连续性、历史事实、人物位置、物品状态、能力规则、知识来源或时间线；
            这些内容由另一个审核器负责。不要自行检索或补充输入之外的背景。

            MUST_FIX 只用于：章纲核心事件缺失、因果无法成立、人物行为明显没有动机、
            或大量重复导致剧情没有推进。文风、措辞、轻度重复、信息密度和可优化描写只能使用 OPTIONAL。
            没有明确问题时返回空数组。

            只返回 JSON 数组。每一项只能包含 type、evidence、problem、repairIntent、
            affectedRange、severity；severity 只能是 MUST_FIX 或 OPTIONAL，type 只能是：
            OUTLINE_COMPLETION、CAUSAL_LOGIC、CHARACTER_MOTIVATION、PLOT_PROGRESSION、
            REDUNDANT_INVESTIGATION、PROSE_NATURALNESS。
            """;

    private final IChapterModelPort chapterModelPort;

    @Autowired
    public ModelQualityReviewer(IChapterModelPort chapterModelPort) {
        this.chapterModelPort = Objects.requireNonNull(chapterModelPort,
                "chapterModelPort 不能为空");
    }

    @Override
    public List<QualityFinding> review(ReviewContext reviewContext) {
        QualityContext qualityContext = reviewContext == null
                ? null : reviewContext.getQualityContext() instanceof QualityContext value
                ? value : null;
        if (qualityContext == null) {
            return List.of();
        }
        QualityFinding[] findings = chapterModelPort.call(
                SYSTEM_PROMPT,
                buildUserPrompt(qualityContext),
                QualityFinding[].class,
                "QUALITY_REVIEW",
                1);
        return findings == null ? List.of() : Arrays.stream(findings)
                .filter(Objects::nonNull)
                .toList();
    }

    String buildUserPrompt(QualityContext context) {
        StringBuilder prompt = new StringBuilder(8192);
        prompt.append("请只审核下面固定质量上下文中的当前正文。\n")
                .append("## 当前章纲\n")
                .append("标题：").append(value(context.getChapterPlan() == null
                        ? null : context.getChapterPlan().getTitle())).append('\n')
                .append("摘要：").append(value(context.getChapterPlan() == null
                        ? null : context.getChapterPlan().getSummary())).append('\n')
                .append("## ARC 目标\n").append(value(context.getArcGoal())).append('\n')
                .append("## 本卷目标\n").append(value(context.getVolumeGoal())).append('\n')
                .append("## 上一章 ending bridge\n")
                .append(value(context.getPreviousChapterEndingBridge())).append('\n')
                .append("## 必要角色设定\n");
        appendCharacters(prompt, context.getRelevantCharacters());
        prompt.append("## 当前正文\n").append(value(context.getCurrentDraft())).append('\n')
                .append("只返回符合协议的 JSON 数组；不要返回审核范围之外的问题。");
        return prompt.toString();
    }

    private void appendCharacters(StringBuilder prompt, List<StoryCharacterEntity> characters) {
        if (characters == null || characters.isEmpty()) {
            prompt.append("无\n");
            return;
        }
        for (StoryCharacterEntity character : characters) {
            if (character == null) {
                continue;
            }
            prompt.append("- 姓名：").append(value(character.getName()))
                    .append("；角色：").append(value(character.getRoleType()))
                    .append("；性格：").append(value(character.getPersonality()))
                    .append("；背景：").append(value(character.getBackgroundStory()))
                    .append("\n");
        }
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }
}
