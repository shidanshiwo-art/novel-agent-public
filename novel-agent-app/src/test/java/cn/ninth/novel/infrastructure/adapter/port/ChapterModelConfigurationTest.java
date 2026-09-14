package cn.ninth.novel.infrastructure.adapter.port;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.infrastructure.config.ChapterModelProperties;
import cn.ninth.novel.infrastructure.config.ReasoningLevel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
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

class ChapterModelConfigurationTest {

    @Test
    void shouldBindChapterReasoningLevelsByStage() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "novel.chapter.model=deepseek-v4-flash",
                        "novel.chapter.temperature=0.7",
                        "novel.chapter.reasoning=LOW",
                        "novel.chapter.stage-reasoning.REVIEW=MEDIUM",
                        "novel.chapter.stage-reasoning.COMPRESSION=OFF"
                )
                .run(context -> {
                    ChapterModelProperties properties =
                            context.getBean(ChapterModelProperties.class);
                    assertThat(properties.getReasoning()).isEqualTo(ReasoningLevel.LOW);
                    assertThat(properties.reasoningForStage("REVIEW"))
                            .isEqualTo(ReasoningLevel.MEDIUM);
                    assertThat(properties.reasoningForStage("COMPRESSION"))
                            .isEqualTo(ReasoningLevel.OFF);
                    System.out.printf(
                            "Chapter stage 思考强度绑定通过：默认=%s，REVIEW=%s，COMPRESSION=%s%n",
                            properties.getReasoning(),
                            properties.reasoningForStage("REVIEW"),
                            properties.reasoningForStage("COMPRESSION")
                    );
                });
    }

    @Test
    void shouldTranslateChapterReasoningLevelToDeepSeekOptions() {
        ChapterModelProperties properties = new ChapterModelProperties();
        properties.setModel("deepseek-v4-flash");
        properties.setTemperature(0.7);
        properties.setReasoning(ReasoningLevel.LOW);
        properties.getStageReasoning().put("REVIEW", ReasoningLevel.MEDIUM);

        CapturingChatModel model = new CapturingChatModel();
        ChapterModelPort port = new ChapterModelPort(
                ChatClient.builder(model).build(),
                properties
        );

        ChapterModelResponse<RootOutlineDraftVO> response = port.callWithRawResponse(
                "system",
                "user",
                RootOutlineDraftVO.class,
                "REVIEW",
                1
        );
        OpenAiChatOptions options = model.lastOptions.get();

        assertThat(response.value().summary()).isEqualTo("ok");
        assertThat(options.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(options.getReasoningEffort()).isEqualTo("high");
        assertThat(options.getMaxTokens()).isNull();
        assertThat(options.getMaxCompletionTokens()).isNull();
        assertThat(options.getExtraBody())
                .containsEntry("thinking", Map.of("type", "enabled"));
        System.out.printf(
                "Chapter 模型选项转换通过：model=%s，temperature=%s，reasoning_effort=%s，thinking=%s，未设置 token 上限%n",
                options.getModel(),
                options.getTemperature(),
                options.getReasoningEffort(),
                options.getExtraBody()
        );
    }

    @Test
    void shouldDisableOnlyReviewReasoningWhenOverrideIsFalse() {
        ChapterModelProperties properties = new ChapterModelProperties();
        properties.setModel("deepseek-v4-flash");
        properties.setTemperature(0.7);
        properties.setReasoning(ReasoningLevel.LOW);
        properties.getStageReasoning().put("REVIEW", ReasoningLevel.MEDIUM);

        CapturingChatModel model = new CapturingChatModel();
        ChapterModelPort port = new ChapterModelPort(
                ChatClient.builder(model).build(),
                properties
        );

        port.callWithRawResponse(
                "system",
                "user",
                RootOutlineDraftVO.class,
                "REVIEW",
                1,
                Boolean.FALSE
        );
        OpenAiChatOptions options = model.lastOptions.get();

        assertThat(options.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(options.getReasoningEffort()).isNull();
        assertThat(options.getExtraBody())
                .containsEntry("thinking", Map.of("type", "disabled"));
        System.out.printf(
                "REVIEW reasoning 覆盖通过：model=%s，temperature=%s，reasoning_effort=%s，thinking=%s%n",
                options.getModel(),
                options.getTemperature(),
                options.getReasoningEffort(),
                options.getExtraBody()
        );
    }

    @Test
    void shouldLogEffectiveChapterConfigurationWithoutSensitiveOptions() {
        ChapterModelProperties properties = new ChapterModelProperties();
        properties.setProfile("CHAPTER");
        properties.setModel("deepseek-v4-flash");
        properties.setTemperature(0.7);
        properties.setReasoning(ReasoningLevel.LOW);
        properties.getStageReasoning().put("REVIEW", ReasoningLevel.MEDIUM);

        Logger logger = (Logger) LoggerFactory.getLogger(ChapterModelPort.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ChapterModelPort(
                    ChatClient.builder(new CapturingChatModel()).build(),
                    properties
            ).callWithRawResponse(
                    "system", "user", RootOutlineDraftVO.class, "REVIEW", 1
            );

            List<String> modelLogs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("[MODEL]"))
                    .toList();
            assertThat(modelLogs).anySatisfy(message -> assertThat(message)
                    .contains(
                            "status=start",
                            "stage=REVIEW",
                            "profile=CHAPTER",
                            "timeoutMs=300000",
                            "reasoning=MEDIUM",
                            "temperature=0.7",
                            "model=deepseek-v4-flash"
                    )
                    .doesNotContain("extraBody", "thinking"));
            assertThat(modelLogs).anySatisfy(message -> assertThat(message)
                    .contains(
                            "status=success",
                            "stage=REVIEW",
                            "profile=CHAPTER",
                            "timeoutMs=300000",
                            "reasoning=MEDIUM",
                            "temperature=0.7",
                            "model=deepseek-v4-flash",
                            "firstResponseMs=",
                            "firstContentMs=",
                            "generationMs=",
                            "totalCostMs=",
                            "completionTokens=42"
                    )
                    .doesNotContain("extraBody", "thinking"));
            System.out.println("Chapter 生效配置日志通过：start/success 均包含 stage、profile、timeoutMs、reasoning、temperature、model，未输出 options");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChapterModelProperties.class)
    static class PropertiesConfiguration {
    }

    private static final class CapturingChatModel implements ChatModel {
        private final AtomicReference<OpenAiChatOptions> lastOptions = new AtomicReference<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("Chapter 结构化调用必须使用 stream()");
        }

        @Override
        public OpenAiChatOptions getOptions() {
            return OpenAiChatOptions.builder().build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastOptions.set((OpenAiChatOptions) prompt.getOptions());
            return Flux.just(new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage("{\"summary\":\"ok\"}"),
                            ChatGenerationMetadata.builder().finishReason("stop").build()
                    )),
                    ChatResponseMetadata.builder()
                            .usage(new DefaultUsage(50, 42, 92))
                            .build()
            ));
        }
    }
}
