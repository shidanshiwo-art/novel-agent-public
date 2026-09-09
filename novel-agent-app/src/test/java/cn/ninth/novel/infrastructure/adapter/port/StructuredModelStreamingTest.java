package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredModelStreamingTest {

    @Test
    void parsesOnlyAfterAllChunksAndRecordsStructuredStreamMetricsInBothPorts() {
        String[] chunks = {"{\"summary\":\"流", "式结果\"}"};
        String expectedContent = String.join("", chunks);

        for (boolean planning : List.of(true, false)) {
            Logger logger = (Logger) LoggerFactory.getLogger(
                    planning ? PlanningModelPort.class : ChapterModelPort.class
            );
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                ChatModel model = new StreamingOnlyChatModel(chunks);
                ChatClient client = ChatClient.builder(model).build();
                RootOutlineDraftVO result = planning
                        ? new PlanningModelPort(client).call(
                        "system", "user", RootOutlineDraftVO.class, "STREAM_TEST", 1)
                        : new ChapterModelPort(client).call(
                        "system", "user", RootOutlineDraftVO.class, "STREAM_TEST", 1);

                assertThat(result.summary()).isEqualTo("流式结果");
                String successLog = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .filter(message -> message.contains("status=success mode=structured"))
                        .findFirst()
                        .orElseThrow();
                assertThat(successLog)
                        .contains(
                                "stage=STREAM_TEST",
                                "ttftMs=",
                                "generationMs=",
                                "chunkCount=2",
                                "contentChars=" + expectedContent.length(),
                                "totalCostMs=",
                                "contentReadMs="
                        );
                if (planning) {
                    assertThat(successLog)
                            .contains("firstResponseMs=", "firstContentMs=");
                }
                System.out.printf(
                        "结构化流式解析通过：planning=%s, chunkCount=2, contentChars=%d, log=%s%n",
                        planning,
                        expectedContent.length(),
                        successLog
                );
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
        }
    }

    private static final class StreamingOnlyChatModel implements ChatModel {
        private final String[] chunks;

        private StreamingOnlyChatModel(String[] chunks) {
            this.chunks = chunks;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("结构化响应必须使用 stream()，不能回退到 call()");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromArray(chunks)
                    .map(chunk -> new ChatResponse(List.of(
                            new Generation(new AssistantMessage(chunk))
                    )));
        }
    }
}
