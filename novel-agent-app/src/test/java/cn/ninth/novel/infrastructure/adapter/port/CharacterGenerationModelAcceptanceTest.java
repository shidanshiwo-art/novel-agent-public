package cn.ninth.novel.infrastructure.adapter.port;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftListVO;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CHARACTER_GENERATION 结构化流的离线验收契约；真实模型联调仍使用 dev profile。 */
class CharacterGenerationModelAcceptanceTest {

    @Test
    void shouldLogLifecycleMetricsAndReturnCharacterDraftListAfterStreamCompletes() {
        String systemPrompt = "S".repeat(1_086);
        String userPrompt = "U".repeat(1_379);
        String[] chunks = {
                "{\"characters\":[{\"name\":\"林澈\"},{\"name\":\"顾言\"},",
                "{\"name\":\"监察使\"}]}"
        };
        Logger logger = (Logger) LoggerFactory.getLogger(PlanningModelPort.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CharacterDraftListVO result = new PlanningModelPort(
                    ChatClient.builder(new CharacterGenerationChatModel(chunks)).build()
            ).call(
                    systemPrompt,
                    userPrompt,
                    CharacterDraftListVO.class,
                    "CHARACTER_GENERATION",
                    1
            );

            String startLog = findLog(appender, "status=start mode=structured");
            String successLog = findLog(appender, "status=success mode=structured");

            assertThat(result.characters()).hasSize(3);
            assertThat(startLog)
                    .contains(
                            "stage=CHARACTER_GENERATION",
                            "systemChars=1086",
                            "userChars=1379"
                    );
            assertThat(successLog)
                    .contains(
                        "stage=CHARACTER_GENERATION",
                        "ttftMs=",
                        "firstResponseMs=",
                        "firstContentMs=",
                        "generationMs=",
                        "chunkCount=2",
                        "contentChars=" + (chunks[0].length() + chunks[1].length()),
                        "totalCostMs=",
                        "completionTokens=42",
                        "usage=",
                        "finishReason=stop"
                );
            System.out.printf(
                    "CHARACTER_GENERATION 离线验收通过：systemChars=%d, userChars=%d, characters=%d%n%s%n%s%n",
                    systemPrompt.length(),
                    userPrompt.length(),
                    result.characters().size(),
                    startLog,
                    successLog
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private String findLog(ListAppender<ILoggingEvent> appender, String marker) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到模型日志: " + marker));
    }

    private static final class CharacterGenerationChatModel implements ChatModel {
        private final String[] chunks;

        private CharacterGenerationChatModel(String[] chunks) {
            this.chunks = chunks;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("CHARACTER_GENERATION 必须使用结构化 stream()");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromArray(chunks)
                    .map(CharacterGenerationChatModel::response);
        }

        private static ChatResponse response(String chunk) {
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage(chunk),
                            ChatGenerationMetadata.builder().finishReason("stop").build()
                    )),
                    ChatResponseMetadata.builder()
                            .usage(new DefaultUsage(50, 42, 92))
                            .build()
            );
        }
    }
}
