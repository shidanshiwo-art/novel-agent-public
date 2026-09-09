package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.service.agent.ChapterModelRetryExecutor;
import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class StructuredModelResponseRetryTest {
    private ChatClient client(AtomicInteger requests, String... responses) {
        ChatModel model = new ChatModel() {
            private ChatResponse response() {
                int index = requests.getAndIncrement();
                String raw = responses[Math.min(index, responses.length - 1)];
                System.out.printf("模型请求 attempt=%d, response=%s%n", index + 1, raw);
                if (raw.equals("REQUEST_ERROR")) throw new IllegalStateException("upstream unavailable");
                return new ChatResponse(List.of(new Generation(new AssistantMessage(raw))));
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                return response();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> Flux.just(response()));
            }
        };
        return ChatClient.builder(model).build();
    }

    @Test
    void retriesOnceWithANewModelResponseInBothPorts() {
        for (boolean planning : List.of(true, false)) {
            AtomicInteger requests = new AtomicInteger();
            ChatClient client = client(requests, "```json\n{\"summary\":\n```", "{\"summary\":\"新结果\"}");
            RootOutlineDraftVO result = planning
                    ? new PlanningModelPort(client).call("system", "user", RootOutlineDraftVO.class)
                    : new ChapterModelPort(client).call("system", "user", RootOutlineDraftVO.class);
            assertThat(result.summary()).isEqualTo("新结果");
            assertThat(requests.get()).isEqualTo(2);
            System.out.println("重新请求后恢复成功，planning=" + planning);
        }
    }

    @Test
    void stopsAfterTwoRequestsIncludingOuterChapterRetryAndSecondRequestErrors() {
        for (String second : List.of("{", "REQUEST_ERROR", "{}")) {
            AtomicInteger requests = new AtomicInteger();
            ChapterModelPort port = new ChapterModelPort(client(requests, "{", second));
            AppException failure = catchThrowableOfType(() -> new ChapterModelRetryExecutor().execute(
                    () -> port.call("system", "user", RootOutlineDraftVO.class)), AppException.class);
            assertThat(requests.get()).isEqualTo(2);
            assertThat(failure).isInstanceOf(ChapterModelResponseException.class);
            assertThat(((ChapterModelResponseException) failure).parseRetryExhausted()).isTrue();
            System.out.println("外层未叠加重试，第二次结果=" + second);
        }
        AtomicInteger planningRequests = new AtomicInteger();
        assertThatThrownBy(() -> new PlanningModelPort(client(planningRequests, "{"))
                .call("system", "user", RootOutlineDraftVO.class)).isInstanceOf(AppException.class);
        assertThat(planningRequests.get()).isEqualTo(2);
        System.out.println("Planning 持续解析失败也只请求两次");
    }

    @Test
    void doesNotRetryCleanupOrBusinessValidationInPorts() {
        for (String raw : List.of("```json\n{}", "{}", "```json\n{\"summary\":\"有效\"}\n```")) {
            for (boolean planning : List.of(true, false)) {
                AtomicInteger requests = new AtomicInteger();
                ChatClient client = client(requests, raw);
                try {
                    if (planning) new PlanningModelPort(client).call("system", "user", RootOutlineDraftVO.class);
                    else new ChapterModelPort(client).call("system", "user", RootOutlineDraftVO.class);
                } catch (AppException expected) {
                    assertThat(expected.getInternalDetail()).doesNotContain("JSON_PARSE_FAILED");
                }
                assertThat(requests.get()).isEqualTo(1);
                System.out.println("无额外解析重试，planning=" + planning + ", raw=" + raw);
            }
        }
    }

    @Test
    void reviewUsesSharedParserAndParserLeavesBusinessValidationToDomain() {
        assertThat(StructuredModelResponseParser.parse("{}", RootOutlineDraftVO.class).summary()).isNull();
        AtomicInteger requests = new AtomicInteger();
        ReviewReportVO review = new ChapterModelPort(client(requests, "{", "```json\n{\"issues\":[]}\n```"))
                .call("system", "user", ReviewReportVO.class);
        assertThat(review.getReviewIssueVOList()).isEmpty();
        assertThat(requests.get()).isEqualTo(2);
        System.out.println("Review 共用解析器；解析器不承担业务字段校验");
    }
}
