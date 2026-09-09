package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftListVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import cn.ninth.novel.infrastructure.config.ReasoningLevel;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用 dev profile 对 CHARACTER_GENERATION 结构化流做真实模型人工验收。 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class CharacterGenerationModelRealModelIT {

    @Autowired
    private IPlanningModelPort modelPort;

    @Autowired
    private PlanningModelProperties planningModelProperties;

    @Test
    void printCharacterGenerationPromptsAndOutput() {
        String userPrompt = """
                项目标题=回响之城
                题材=城市奇幻
                本次建议新增 4 名人物
                当前 Story Bible（故事设定）：
                一句话故事=钟楼下的失踪案
                核心主题=记忆与归属
                主要矛盾=主角对抗抹除记忆的组织
                结局方向=保留城市记忆，也接受无法找回的一部分过去
                世界背景=现代城市的钟楼保存着旧城区的集体记忆，每晚只能响一次。
                能力体系=读取钟楼回声，但会丢失当天记忆。
                硬规则=死者不能复生；钟楼每晚只能响一次。
                写作风格=第三人称限知，克制推进悬疑。
                【已有大纲剧情位置】
                全书大纲：标题=回响之城全书；摘要=主角必须查清钟楼失踪案，最终找回城市记忆。
                当前卷：标题=南境回声；摘要=监察使将成为本卷核心阻力，主角必须取得关键证据；章节范围=31～60
                以上是已确认的剧情规划，用于定位本次新增人物；它不是正式人物档案，大纲中尚未定义的人物只能作为待补充的剧情位置。
                已有角色摘要：
                - 姓名：顾言；定位：女主角；性别：女；年龄：二十五岁；外貌：短发；性格：冷静；人物经历：调查钟楼失踪案；备注：与核心谜案有关。
                用户补充要求=请补充掌管南境的监察使、与主角暂时合作的关键盟友，以及一名能推动当前卷冲突的配角；不要重复已有角色功能。
                """;

        System.out.println("\n========== CHARACTER_GENERATION SYSTEM PROMPT ==========");
        System.out.println(PlanningPrompts.CHARACTER_SYSTEM);
        System.out.println("========== CHARACTER_GENERATION USER PROMPT ==========");
        System.out.println(userPrompt);

        Logger logger = (Logger) LoggerFactory.getLogger(PlanningModelPort.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        List<RunResult> results = new ArrayList<>();
        ReasoningLevel originalReasoning = planningModelProperties.getStageReasoning()
                .get("CHARACTER_GENERATION");
        try {
            for (ReasoningLevel reasoning : List.of(ReasoningLevel.LOW, ReasoningLevel.MEDIUM)) {
                planningModelProperties.getStageReasoning().put("CHARACTER_GENERATION", reasoning);
                System.out.println("========== CHARACTER_GENERATION reasoning=" + reasoning + " ==========");
                for (int run = 1; run <= 3; run++) {
                    int previousLogCount = appender.list.size();
                    CharacterDraftListVO result = null;
                    Exception failure = null;
                    try {
                        result = modelPort.call(
                                PlanningPrompts.CHARACTER_SYSTEM,
                                userPrompt,
                                CharacterDraftListVO.class,
                                "CHARACTER_GENERATION",
                                1
                        );
                    } catch (Exception exception) {
                        failure = exception;
                    }

                    List<String> runLogs = appender.list.subList(previousLogCount, appender.list.size()).stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .toList();
                    String successLog = runLogs.stream()
                            .filter(message -> message.contains(
                                    "status=success mode=structured stage=CHARACTER_GENERATION"))
                            .reduce((first, second) -> second)
                            .orElse(null);
                    String failureLog = runLogs.stream()
                            .filter(message -> message.contains(
                                    "status=failed mode=structured stage=CHARACTER_GENERATION"))
                            .reduce((first, second) -> second)
                            .orElse(null);
                    String metricLog = successLog == null ? failureLog : successLog;
                    int characterCount = result == null || result.characters() == null
                            ? 0 : result.characters().size();
                    int completeCharacterCount = completeCharacterCount(result);
                    RunResult runResult = new RunResult(
                            reasoning,
                            run,
                            result != null,
                            characterCount,
                            completeCharacterCount,
                            metricValue(metricLog, "firstContentMs"),
                            metricValue(metricLog, "totalCostMs"),
                            metricValue(metricLog, "completionTokens"),
                            runLogs.stream().anyMatch(message -> message.contains(
                                    "status=parse failed mode=structured stage=CHARACTER_GENERATION"))
                    );
                    results.add(runResult);

                    System.out.println("---------- CHARACTER_GENERATION OUTPUT reasoning="
                            + reasoning + " run=" + run + " ----------");
                    System.out.println(result == null
                            ? "model call failed: " + failure
                            : result);
                    System.out.printf(
                            "reasoning=%s run=%d firstResponseMs=%s firstContentMs=%s generationMs=%s "
                                    + "completionTokens=%s totalCostMs=%s jsonParsed=%s completeCharacters=%d/%d parseRetry=%s%n",
                            reasoning,
                            run,
                            metric(metricLog, "firstResponseMs"),
                            metric(metricLog, "firstContentMs"),
                            metric(metricLog, "generationMs"),
                            metric(metricLog, "completionTokens"),
                            metric(metricLog, "totalCostMs"),
                            runResult.jsonParsed(),
                            runResult.completeCharacterCount(),
                            runResult.characterCount(),
                            runResult.parseRetried()
                    );
                    System.out.println(metricLog == null ? "model log not captured" : metricLog);
                }
            }
        } finally {
            if (originalReasoning == null) {
                planningModelProperties.getStageReasoning().remove("CHARACTER_GENERATION");
            } else {
                planningModelProperties.getStageReasoning().put(
                        "CHARACTER_GENERATION", originalReasoning
                );
            }
            logger.detachAppender(appender);
            appender.stop();
        }

        for (ReasoningLevel reasoning : List.of(ReasoningLevel.LOW, ReasoningLevel.MEDIUM)) {
            List<RunResult> levelResults = results.stream()
                    .filter(result -> result.reasoning() == reasoning)
                    .toList();
            long parsed = levelResults.stream().filter(RunResult::jsonParsed).count();
            long completeCharacters = levelResults.stream()
                    .mapToLong(RunResult::completeCharacterCount)
                    .sum();
            long allCharacters = levelResults.stream()
                    .mapToLong(RunResult::characterCount)
                    .sum();
            System.out.printf(
                    "CHARACTER_GENERATION summary reasoning=%s runs=%d jsonParse=%d/%d "
                            + "completeness=%d/%d firstContentMs=%s totalCostMs=%s completionTokens=%s%n",
                    reasoning,
                    levelResults.size(),
                    parsed,
                    levelResults.size(),
                    completeCharacters,
                    allCharacters,
                    average(levelResults.stream().map(RunResult::firstContentMs).toList()),
                    average(levelResults.stream().map(RunResult::totalCostMs).toList()),
                    average(levelResults.stream().map(RunResult::completionTokens).toList())
            );
        }
        System.out.println("========== END CHARACTER_GENERATION OUTPUT ==========\n");
    }

    private static Long metricValue(String log, String name) {
        String value = metric(log, name);
        return value == null ? null : Long.valueOf(value);
    }

    private static int completeCharacterCount(CharacterDraftListVO result) {
        if (result == null || result.characters() == null) {
            return 0;
        }
        return (int) result.characters().stream()
                .filter(CharacterGenerationModelRealModelIT::isComplete)
                .count();
    }

    private static boolean isComplete(CharacterDraftVO character) {
        return character != null
                && isNonBlank(character.name())
                && isNonBlank(character.role())
                && isNonBlank(character.gender())
                && isNonBlank(character.ageDescription())
                && isNonBlank(character.appearance())
                && isNonBlank(character.personality())
                && isNonBlank(character.backgroundStory())
                && isNonBlank(character.note());
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String metric(String log, String name) {
        if (log == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=(\\d+)(?=\\s|$)")
                .matcher(log);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String average(List<Long> values) {
        List<Long> nonNullValues = values.stream().filter(value -> value != null).toList();
        if (nonNullValues.isEmpty()) {
            return "n/a";
        }
        return String.valueOf(nonNullValues.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private record RunResult(
            ReasoningLevel reasoning,
            int run,
            boolean jsonParsed,
            int characterCount,
            int completeCharacterCount,
            Long firstContentMs,
            Long totalCostMs,
            Long completionTokens,
            boolean parseRetried
    ) {
    }
}
