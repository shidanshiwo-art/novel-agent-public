package cn.ninth.novel.infrastructure.adapter.port;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import cn.ninth.novel.infrastructure.config.ReasoningLevel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningModelConfigurationTest {

    @Test
    void shouldBindYamlStyleModelKeysAndDeepSeekThinkingFields() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "novel.planning.models.fast-structured.model=deepseek-v4-flash",
                        "novel.planning.models.fast-structured.temperature=0.2",
                        "novel.planning.models.fast-structured.reasoning=OFF",
                        "novel.planning.models.creative-planning.model=deepseek-v4-pro",
                        "novel.planning.models.creative-planning.temperature=0.7",
                        "novel.planning.models.creative-planning.reasoning=MEDIUM",
                        "novel.planning.stage-reasoning.CHAPTER_PLAN=MEDIUM"
                )
                .run(context -> {
                    PlanningModelProperties properties =
                            context.getBean(PlanningModelProperties.class);
                    assertThat(properties.getModels()).containsKeys(
                            PlanningModelProperties.PlanningModelProfile.FAST_STRUCTURED,
                            PlanningModelProperties.PlanningModelProfile.CREATIVE_PLANNING
                    );
                    assertThat(properties.modelForStage("CHARACTER_GENERATION").getReasoning())
                            .isEqualTo(ReasoningLevel.OFF);
                    assertThat(properties.reasoningForStage("CHAPTER_PLAN"))
                            .isEqualTo(ReasoningLevel.MEDIUM);
                    assertThat(properties.forStage("CHAPTER_PLAN").reasoning())
                            .isEqualTo(ReasoningLevel.MEDIUM);
                    assertThat(properties.forStage("CHAPTER_PLAN").temperature())
                            .isEqualTo(0.2);
                    assertThat(properties.modelForStage("ROOT_OUTLINE").getReasoning())
                            .isEqualTo(ReasoningLevel.MEDIUM);
                    System.out.println("Planning YAML 风格配置绑定通过：两类 profile 和 stage 思考强度均已读取");
                });
    }

    @Test
    void shouldBuildDeepSeekOptionsFromTheStageProfile() {
        PlanningModelProperties properties = new PlanningModelProperties();
        properties.getModels().put(
                PlanningModelProperties.PlanningModelProfile.FAST_STRUCTURED,
                modelConfig(
                        "deepseek-v4-flash-fast",
                        0.2,
                        ReasoningLevel.OFF
                )
        );
        properties.getModels().put(
                PlanningModelProperties.PlanningModelProfile.CREATIVE_PLANNING,
                modelConfig(
                        "deepseek-v4-flash-creative",
                        0.7,
                        ReasoningLevel.MEDIUM
                )
        );

        CapturingChatModel model = new CapturingChatModel();
        PlanningModelPort port = new PlanningModelPort(
                ChatClient.builder(model).build(),
                properties
        );

        assertThat(properties.forStage("CHARACTER_GENERATION").model())
                .isEqualTo("deepseek-v4-flash-fast");
        assertThat(properties.forStage("ROOT_OUTLINE").reasoning())
                .isEqualTo(ReasoningLevel.MEDIUM);

        RootOutlineDraftVO fastResult = port.call(
                "fast system", "fast user", RootOutlineDraftVO.class,
                "CHARACTER_GENERATION", 1
        );
        OpenAiChatOptions fastOptions = model.lastOptions.get();
        assertThat(fastResult.summary()).isEqualTo("ok");
        assertThat(fastOptions.getModel()).isEqualTo("deepseek-v4-flash-fast");
        assertThat(fastOptions.getTemperature()).isEqualTo(0.2);
        assertThat(fastOptions.getReasoningEffort()).isNull();
        assertThat(fastOptions.getMaxTokens()).isNull();
        assertThat(fastOptions.getMaxCompletionTokens()).isNull();
        assertThat(fastOptions.getExtraBody())
                .containsEntry("thinking", Map.of("type", "disabled"));

        RootOutlineDraftVO creativeResult = port.call(
                "creative system", "creative user", RootOutlineDraftVO.class,
                "ROOT_OUTLINE", 1
        );
        OpenAiChatOptions creativeOptions = model.lastOptions.get();
        assertThat(creativeResult.summary()).isEqualTo("ok");
        assertThat(creativeOptions.getModel()).isEqualTo("deepseek-v4-flash-creative");
        assertThat(creativeOptions.getTemperature()).isEqualTo(0.7);
        assertThat(creativeOptions.getReasoningEffort()).isEqualTo("high");
        assertThat(creativeOptions.getMaxTokens()).isNull();
        assertThat(creativeOptions.getMaxCompletionTokens()).isNull();
        assertThat(creativeOptions.getExtraBody())
                .containsEntry("thinking", Map.of("type", "enabled"));

        System.out.printf(
                "Planning stage 配置通过：CHARACTER_GENERATION=%s/%s/%s，ROOT_OUTLINE=%s/%s/%s，未设置 token 上限%n",
                fastOptions.getModel(),
                fastOptions.getTemperature(),
                fastOptions.getExtraBody(),
                creativeOptions.getModel(),
                creativeOptions.getReasoningEffort(),
                creativeOptions.getExtraBody()
        );
    }

    @Test
    void shouldKeepTheEightPlanningStagesInTwoProfiles() {
        assertThat(PlanningModelProperties.profileForStage("CHARACTER_GENERATION"))
                .isEqualTo(PlanningModelProperties.PlanningModelProfile.FAST_STRUCTURED);
        assertThat(PlanningModelProperties.profileForStage("CHAPTER_PLAN"))
                .isEqualTo(PlanningModelProperties.PlanningModelProfile.FAST_STRUCTURED);

        for (String stage : List.of(
                "STORY_BIBLE_INIT",
                "STORY_BIBLE_REVISION",
                    "ROOT_OUTLINE",
                    "NEXT_VOLUME",
                    "VOLUME_OUTLINE",
                    "NEXT_CHAPTER_OUTLINE",
                "ARC_REGENERATION"
        )) {
            assertThat(PlanningModelProperties.profileForStage(stage))
                    .as("stage=%s", stage)
                    .isEqualTo(PlanningModelProperties.PlanningModelProfile.CREATIVE_PLANNING);
        }

        System.out.println("Planning 九个 stage 已收敛为 FAST_STRUCTURED 与 CREATIVE_PLANNING 两类配置");
    }

    @Test
    void shouldLogEffectivePlanningConfigurationWithoutSensitiveOptions() {
        PlanningModelProperties properties = new PlanningModelProperties();
        properties.getModels().put(
                PlanningModelProperties.PlanningModelProfile.FAST_STRUCTURED,
                modelConfig(
                        "deepseek-v4-flash",
                        0.7,
                        ReasoningLevel.OFF
                )
        );

        Logger logger = (Logger) LoggerFactory.getLogger(PlanningModelPort.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new PlanningModelPort(
                    ChatClient.builder(new CapturingChatModel()).build(),
                    properties
            ).call("system", "user", RootOutlineDraftVO.class, "CHARACTER_GENERATION", 1);

            String startLog = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("status=start"))
                    .findFirst()
                    .orElseThrow();
            String successLog = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("status=success"))
                    .findFirst()
                    .orElseThrow();
            assertThat(List.of(startLog, successLog))
                    .allSatisfy(message -> assertThat(message)
                    .contains(
                            "stage=CHARACTER_GENERATION",
                            "profile=FAST_STRUCTURED",
                            "model=deepseek-v4-flash",
                            "temperature=0.7",
                            "reasoning=OFF",
                            "timeoutMs=180000"
                    )
                    .doesNotContain("apiKey", "extraBody", "thinking"));
            System.out.println("Planning 配置摘要日志通过：包含实际 profile/model/temperature/reasoning，未输出敏感 options");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private PlanningModelProperties.ModelConfig modelConfig(
            String model,
            double temperature,
            ReasoningLevel reasoning
    ) {
        PlanningModelProperties.ModelConfig config = new PlanningModelProperties.ModelConfig();
        config.setModel(model);
        config.setTemperature(temperature);
        config.setReasoning(reasoning);
        return config;
    }

    private static final class CapturingChatModel implements ChatModel {
        private final AtomicReference<OpenAiChatOptions> lastOptions = new AtomicReference<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("Planning 结构化调用必须使用 stream()");
        }

        @Override
        public OpenAiChatOptions getOptions() {
            return OpenAiChatOptions.builder().build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastOptions.set((OpenAiChatOptions) prompt.getOptions());
            return Flux.just(new ChatResponse(List.of(
                    new Generation(new AssistantMessage("{\"summary\":\"ok\"}"))
            )));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlanningModelProperties.class)
    static class PropertiesConfiguration {
    }
}
