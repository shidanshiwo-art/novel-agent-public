package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.chapter.model.valobj.*;
import cn.ninth.novel.domain.chapter.service.agent.ChapterMemoryResponse;
import cn.ninth.novel.domain.common.validation.StructuredModelOutputValidator;
import cn.ninth.novel.domain.planning.model.valobj.*;
import cn.ninth.novel.domain.project.model.valobj.*;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import static org.assertj.core.api.Assertions.*;

class StructuredOutputValidationTest {
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

    @Test
    void restoresOnlyDeterministicWrappersInBothPorts() {
        String json = "{\"summary\":\"他看见 {钟楼}，说：\\\"走吧\\\"。```\"}";
        for (String raw : List.of(json, " \uFEFF " + json + " \n",
                "```json\n" + json + "\n```", "说明如下：\n```JSON\n" + json + "\n```\n以上为结果。",
                "结果如下：\n" + json + "\n以上为结果。")) {
            assertThat(StructuredModelResponseParser.normalize(raw)).isEqualTo(json);
            RootOutlineDraftVO planning = new PlanningModelPort(client(raw)).call("system", "user", RootOutlineDraftVO.class);
            ChapterModelResponse<RootOutlineDraftVO> chapter = new ChapterModelPort(client(raw))
                    .callWithRawResponse("system", "user", RootOutlineDraftVO.class, "TEST", 1);
            assertThat(chapter.value()).isEqualTo(planning);
            assertThat(chapter.rawText()).isEqualTo(raw);
            System.out.println("外壳恢复成功，正文及原始响应保持完整：" + raw);
        }
    }

    @Test
    void rejectsBrokenJsonWithoutRepairAndClassifiesFailures() {
        for (String raw : List.of("{\"summary\":\"a\" \"title\":\"b\"}", "{summary:\"a\"}",
                "{\"summary\":\"a\"", "{\"summary\":\"a}", "这是一段自然语言")) {
            failure(raw, "JSON_PARSE_FAILED");
        }
        for (String raw : List.of("```json\n{}", "{} {}", "{},", "结果：{}\n另一个：{}")) {
            failure(raw, "FORMAT_CLEANUP_FAILED");
        }
        for (String raw : List.of("{}", "null", "[]", "{\"summary\":[]}", "{\"summary\":\"  \"}")) {
            failure(raw, "SCHEMA_VALIDATION_FAILED");
        }
    }

    private void failure(String raw, String type) {
        AppException planning = catchThrowableOfType(() -> new PlanningModelPort(client(raw))
                .call("system", "user", RootOutlineDraftVO.class), AppException.class);
        AppException chapter = catchThrowableOfType(() -> new ChapterModelPort(client(raw))
                .call("system", "user", RootOutlineDraftVO.class), AppException.class);
        assertThat(planning.getInternalDetail()).contains(type);
        assertThat(chapter.getInternalDetail()).contains(type);
        assertThat(((ChapterModelResponseException) chapter).rawText()).isEqualTo(raw);
        System.out.printf("两个入口均拒绝：%s -> %s%n", raw, type);
    }

    @Test
    void validatesRequiredFieldsAndExistingLengthLimits() {
        List<Object> invalid = List.of(new ChapterPlanDraftVO("title", " "),
                new ChapterPlanDraftVO("字".repeat(301), "summary"),
                new CharacterDraftVO(" ", null, null, null, null, null, null, null),
                new CharacterDraftListVO(List.of()), new ReviewReportVO(null),
                new ChapterMemoryResponse("摘要", null, List.of(), "钩子", null),
                new ChapterMemoryVO(1, "字".repeat(2001), List.of(), List.of(), "钩子"),
                new ChapterMemoryResponse("摘要", List.of(" "), List.of(), "钩子", null));
        for (Object value : invalid) {
            AppException error = catchThrowableOfType(() -> StructuredModelOutputValidator.validate(value), AppException.class);
            assertThat(error.getInternalDetail()).contains("SCHEMA_VALIDATION_FAILED");
            System.out.println(value.getClass().getSimpleName() + " 校验拒绝：" + error.getInternalDetail());
        }
        StructuredModelOutputValidator.validate(new RootOutlineDraftVO("有效总纲"));
        StructuredModelOutputValidator.validate(new ReviewReportVO(List.of()));
        StructuredModelOutputValidator.validate(new ChapterMemoryResponse("字".repeat(2000), List.of(), List.of(), "钩子", null));
        StructuredModelOutputValidator.validate(new ChapterPlanDraftVO("字".repeat(300), "摘要"));
        System.out.println("无 title 总纲、无问题审稿报告及长度边界均通过");
    }
}
