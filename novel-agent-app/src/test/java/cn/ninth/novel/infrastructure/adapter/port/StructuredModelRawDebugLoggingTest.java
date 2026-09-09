package cn.ninth.novel.infrastructure.adapter.port;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class StructuredModelRawDebugLoggingTest {

    @Test
    void devAndLocalIncludeRawAndNormalizedResponsesOnlyForJsonParseFailures() {
        String raw = "说明：\n```json\n{\"summary\":\"未闭合\"\n```";
        String normalized = "{\"summary\":\"未闭合\"";
        for (String profile : List.of("dev", "local")) {
            Logger logger = logger();
            Level originalLevel = logger.getLevel();
            ListAppender<ILoggingEvent> appender = appender(logger);
            try {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles(profile);
                PlanningModelPort port = new PlanningModelPort(client(raw), environment);
                catchThrowableOfType(
                        () -> port.call("system", "user", RootOutlineDraftVO.class, "DEBUG_TEST", 1),
                        AppException.class
                );

                List<String> messages = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .toList();
                assertThat(messages).filteredOn(message -> message.startsWith("[MODEL_RAW]"))
                        .containsExactly("[MODEL_RAW] " + raw, "[MODEL_RAW] " + raw);
                assertThat(messages).filteredOn(message -> message.startsWith("[MODEL_NORMALIZED]"))
                        .containsExactly("[MODEL_NORMALIZED] " + normalized, "[MODEL_NORMALIZED] " + normalized);
                assertThat(messages).filteredOn(message -> message.contains("failureType=JSON_PARSE_FAILED"))
                        .allSatisfy(message -> assertThat(message)
                                .contains("stage=DEBUG_TEST", "contentChars=" + raw.length(),
                                        "line=1", "column=17"));
                assertThat(messages).anyMatch(message -> message.contains("failureType=JSON_PARSE_FAILED")
                        && message.contains("attempt=1"));
                assertThat(messages).anyMatch(message -> message.contains("failureType=JSON_PARSE_FAILED")
                        && message.contains("attempt=2"));
                System.out.println("开发环境原始与规范化响应日志通过：profile=" + profile);
            } finally {
                logger.detachAppender(appender);
                logger.setLevel(originalLevel);
                appender.stop();
            }
        }
    }

    @Test
    void prodOmitsRawBodiesButKeepsLengthAndParseLocation() {
        String raw = "{\"summary\":\"未闭合";
        Logger logger = logger();
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = appender(logger);
        try {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles("prod");
            PlanningModelPort port = new PlanningModelPort(client(raw), environment);
            catchThrowableOfType(
                    () -> port.call("system", "user", RootOutlineDraftVO.class, "PROD_TEST", 1),
                    AppException.class
            );
            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(messages).noneMatch(message -> message.startsWith("[MODEL_RAW]"));
            assertThat(messages).noneMatch(message -> message.startsWith("[MODEL_NORMALIZED]"));
            assertThat(messages).filteredOn(message -> message.contains("failureType=JSON_PARSE_FAILED"))
                    .allSatisfy(message -> assertThat(message)
                            .contains("stage=PROD_TEST", "contentChars=" + raw.length(),
                                    "line=1", "column=16")
                            .doesNotContain(raw));
            assertThat(messages).anyMatch(message -> message.contains("failureType=JSON_PARSE_FAILED")
                    && message.contains("attempt=1"));
            assertThat(messages).anyMatch(message -> message.contains("failureType=JSON_PARSE_FAILED")
                    && message.contains("attempt=2"));
            System.out.println("生产环境仅记录长度、位置和失败类型，不包含正文");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    void chapterPortUsesTheSameDevRawDebugPolicy() {
        String raw = "{\"summary\":\"章节结果未闭合";
        Logger logger = (Logger) LoggerFactory.getLogger(ChapterModelPort.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = appender(logger);
        try {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles("dev");
            ChapterModelPort port = new ChapterModelPort(client(raw), environment);
            catchThrowableOfType(
                    () -> port.call("system", "user", RootOutlineDraftVO.class, "CHAPTER_DEBUG_TEST", 1),
                    AppException.class
            );
            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(messages).filteredOn(message -> message.startsWith("[MODEL_RAW]"))
                    .containsExactly("[MODEL_RAW] " + raw, "[MODEL_RAW] " + raw);
            assertThat(messages).filteredOn(message -> message.startsWith("[MODEL_NORMALIZED]"))
                    .containsExactly("[MODEL_NORMALIZED] " + raw, "[MODEL_NORMALIZED] " + raw);
            assertThat(messages).filteredOn(message -> message.contains("failureType=JSON_PARSE_FAILED"))
                    .allSatisfy(message -> assertThat(message)
                                    .contains("stage=CHAPTER_DEBUG_TEST", "contentChars=" + raw.length(),
                                    "line=1", "column=20"));
            System.out.println("Chapter 入口复用 dev raw/normalized 调试日志策略");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    void parserCarriesNormalizedResponseAndLocationWithoutJacksonMessage() {
        String raw = "前置说明\n{\"summary\":\"错误\"";
        StructuredModelResponseParser.ParseException error = catchThrowableOfType(
                () -> StructuredModelResponseParser.parse(raw, RootOutlineDraftVO.class),
                StructuredModelResponseParser.ParseException.class
        );
        assertThat(error.failureType()).isEqualTo("JSON_PARSE_FAILED");
        assertThat(error.normalizedResponse()).isEqualTo("{\"summary\":\"错误\"");
        assertThat(error.line()).isEqualTo(1);
        assertThat(error.column()).isEqualTo(16);
        assertThat(error.getMessage()).doesNotContain("错误");
        System.out.println("解析异常保留 normalized、line、column，未携带 Jackson 原文消息");
    }

    private ChatClient client(String raw) {
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(raw)))
        );
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response;
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(response);
            }
        };
        return ChatClient.builder(model).build();
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(PlanningModelPort.class);
    }

    private ListAppender<ILoggingEvent> appender(Logger logger) {
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
