package cn.ninth.novel.infrastructure.adapter.port;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class StructuredModelLoggingTest {
    @Test
    void recordsOriginalLengthAndFailureMetadataWithoutResponseOrExceptionPayload() {
        String secret = "PRIVATE_MODEL_OUTPUT_123456789";
        List<String> responses = List.of(
                " \uFEFF ```json\n{\"summary\":" + secret + "}\n```\n ",
                "```json\n{\"summary\":\"" + secret + "\"}",
                "{\"summary\":{\"" + secret + "\":true}}");
        List<String> types = List.of("JSON_PARSE_FAILED", "FORMAT_CLEANUP_FAILED", "SCHEMA_VALIDATION_FAILED");
        for (boolean planning : List.of(true, false)) {
            for (int index = 0; index < responses.size(); index++) {
                String raw = responses.get(index);
                String failureType = types.get(index);
                Logger logger = (Logger) LoggerFactory.getLogger(planning ? PlanningModelPort.class : ChapterModelPort.class);
                ListAppender<ILoggingEvent> appender = new ListAppender<>();
                appender.start();
                logger.addAppender(appender);
                try {
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
                    ChatClient client = ChatClient.builder(model).build();
                    AppException error = catchThrowableOfType(() -> {
                        if (planning) new PlanningModelPort(client).call("system", "user", RootOutlineDraftVO.class, "LOG_TEST", 1);
                        else new ChapterModelPort(client).call("system", "user", RootOutlineDraftVO.class, "LOG_TEST", 1);
                    }, AppException.class);
                    int expectedCalls = index == 0 ? 2 : 1;
                    List<ILoggingEvent> failures = appender.list.stream()
                            .filter(event -> event.getFormattedMessage().contains("failureType=" + failureType)).toList();
                    assertThat(failures).hasSize(expectedCalls);
                    for (int attempt = 0; attempt < failures.size(); attempt++) {
                        ILoggingEvent event = failures.get(attempt);
                        assertThat(event.getFormattedMessage()).contains("stage=LOG_TEST", "attempt=" + (attempt + 1),
                                "responseType=RootOutlineDraftVO", "contentChars=" + raw.length(), "failureType=" + failureType)
                                .doesNotContain(secret, "prefixChars=", raw);
                        assertThat(event.getThrowableProxy()).isNull();
                    }
                    StringWriter stack = new StringWriter();
                    error.printStackTrace(new PrintWriter(stack));
                    assertThat(stack.toString()).doesNotContain(secret);
                    System.out.printf("日志验证通过：planning=%s, failureType=%s, contentChars=%d, attempts=%d，无原文及异常片段%n",
                            planning, failureType, raw.length(), expectedCalls);
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }
        }
    }
}
