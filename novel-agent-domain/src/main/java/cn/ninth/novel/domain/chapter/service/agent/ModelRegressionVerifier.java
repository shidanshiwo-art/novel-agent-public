package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckInput;
import cn.ninth.novel.domain.chapter.model.valobj.RegressionCheckResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 使用回归专用窄输入完成 Q1/Q2 检查。 */
@Component
public class ModelRegressionVerifier implements RegressionVerifier {

    static final String SYSTEM_PROMPT = """
            你是章节返修后的窄范围回归检查器。
            只回答两个问题：原始 REQUIRED 返修项是否已经解决，以及返修是否产生新的 HARD 连续性回归。
            只能根据输入的返修项、返修前后受影响片段和相关连续性证据判断。
            不得重新审核整章，不得寻找新的质量问题，不得读取或推测完整章纲、完整 Memory 或未提供的正文。

            只返回 JSON 对象，包含 repairId、status、unresolvedRepairIds、newHardRegression、reason。
            status 只能是 RESOLVED 或 UNRESOLVED；未解决的原始返修项 id 放入 unresolvedRepairIds；
            没有新的 HARD 连续性回归时 newHardRegression 必须为 false。
            """;

    private final IChapterModelPort chapterModelPort;

    @Autowired
    public ModelRegressionVerifier(IChapterModelPort chapterModelPort) {
        this.chapterModelPort = Objects.requireNonNull(chapterModelPort,
                "chapterModelPort 不能为空");
    }

    @Override
    public RegressionCheckResult verify(RegressionCheckInput input) {
        if (input == null) {
            return null;
        }
        return chapterModelPort.call(
                SYSTEM_PROMPT,
                buildUserPrompt(input),
                RegressionCheckResult.class,
                "REGRESSION_CHECK",
                1);
    }

    String buildUserPrompt(RegressionCheckInput input) {
        StringBuilder prompt = new StringBuilder(8192);
        prompt.append("请只检查下面的原始 REQUIRED 返修项。\n")
                .append("## 原始返修项\n");
        input.originalRepairPlan().requiredItems().forEach(item -> prompt
                .append("- repairId：").append(value(item.getId())).append('\n')
                .append("  问题：").append(value(item.getProblem())).append('\n')
                .append("  证据：").append(value(item.getEvidence())).append('\n')
                .append("  受影响范围：").append(value(item.getAffectedRange())).append('\n')
                .append("  返修意图：").append(value(item.getRepairIntent())).append('\n'));
        prompt.append("## 返修前受影响片段\n");
        input.beforeRevisionAffectedText().forEach((id, text) -> prompt
                .append("- ").append(id).append("：").append(value(text)).append('\n'));
        prompt.append("## 返修后受影响片段\n");
        input.afterRevisionAffectedText().forEach((id, text) -> prompt
                .append("- ").append(id).append("：").append(value(text)).append('\n'));
        prompt.append("## 相关连续性证据\n");
        if (input.relevantContinuityEvidence().isEmpty()) {
            prompt.append("无\n");
        } else {
            input.relevantContinuityEvidence().forEach(evidence ->
                    prompt.append("- ").append(value(evidence)).append('\n'));
        }
        prompt.append("只返回符合协议的 JSON 对象。");
        return prompt.toString();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }
}
