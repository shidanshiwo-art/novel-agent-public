package cn.ninth.novel.infrastructure.adapter.port;

import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVIEW_SYSTEM_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ReviewSeverityModelContractTest {

    @Test
    void shouldExposeOnlySafeModelMetadata() {
        ModelObservabilityMetadata metadata = ModelObservabilityMetadata.from(
                new MockEnvironment()
                        .withProperty("spring.ai.model.chat", "openai")
                        .withProperty("spring.ai.openai.chat.model", "gpt-test")
                        .withProperty(
                                "spring.ai.openai.base-url",
                                "https://api.example.test/v1?token=redacted"
                        )
        );

        assertThat(metadata.provider()).isEqualTo("openai");
        assertThat(metadata.modelName()).isEqualTo("gpt-test");
        assertThat(metadata.baseUrlHost())
                .isEqualTo("api.example.test")
                .doesNotContain("https", "token", "redacted");
        System.out.printf(
                "模型基础信息脱敏检查：provider=%s, modelName=%s, baseUrlHost=%s%n",
                metadata.provider(),
                metadata.modelName(),
                metadata.baseUrlHost()
        );
    }

    @Test
    void shouldUseOneOrderedModelLogFormatAndOmitUnavailableFields() {
        ModelObservabilityMetadata metadata = ModelObservabilityMetadata.from(
                new MockEnvironment()
                        .withProperty("spring.ai.model.chat", "openai")
                        .withProperty("spring.ai.openai.chat.model", "gpt-test")
                        .withProperty(
                                "spring.ai.openai.base-url",
                                "https://api.example.test/v1?token=redacted"
                        )
        );

        String message = ModelObservabilityLogFormatter.format(
                "failed",
                "structured",
                "CHAPTER_PLAN",
                1,
                "REQUEST_FAILED",
                null,
                metadata,
                "ChapterPlanVO",
                3,
                5,
                30_021L,
                null,
                null,
                null,
                30_021L,
                null,
                0,
                null,
                null
        );

        assertThat(message)
                .isEqualTo("[MODEL] status=failed mode=structured stage=CHAPTER_PLAN attempt=1 "
                        + "failureType=REQUEST_FAILED model=gpt-test provider=openai "
                        + "baseUrlHost=api.example.test responseType=ChapterPlanVO systemChars=3 "
                        + "userChars=5 requestCostMs=30021 totalCostMs=30021 contentChars=0")
                .doesNotContain("null", "systemPrompt", "userPrompt", "responseContent", "token=redacted");
        System.out.println("模型日志字段顺序与空字段省略检查通过：" + message);

        String planningMessage = ModelObservabilityLogFormatter.format(
                "success",
                "structured",
                "ROOT_OUTLINE",
                1,
                null,
                null,
                metadata,
                "RootOutlineDraftVO",
                3,
                5,
                100L,
                null,
                12L,
                3L,
                null,
                115L,
                null,
                7,
                null,
                null
        );
        assertThat(planningMessage)
                .contains("requestCostMs=100", "contentReadMs=12", "parseCostMs=3", "totalCostMs=115")
                .doesNotContain("readCostMs=");
        System.out.println("Planning contentReadMs 与 parseCostMs 字段检查通过：" + planningMessage);
    }

    @Test
    void shouldSendBusinessSeverityProtocolWithoutBackendEnumValues() {
        AtomicReference<String> sentPrompt = new AtomicReference<>();
        ReviewReportVO result = portReturning("{\"issues\":[]}", sentPrompt)
                .call(REVIEW_SYSTEM_PROMPT, "待审正文", ReviewReportVO.class);

        System.out.println("实际发送的审核 Prompt 与自动生成 Schema：\n" + sentPrompt.get());
        assertThat(sentPrompt.get()).contains("严重", "一般", "轻微", "\"severity\"")
                .doesNotContain("BLOCKER", "MAJOR", "MINOR", "SeverityEnum");
        assertThat(result.getReviewIssueVOList()).isEmpty();
        assertThat(REVIEW_SYSTEM_PROMPT).doesNotContain("VO", "DTO", "PO", "reviewIssueVOList").contains("issues");
        System.out.println("中文协议检查通过，空报告保持为空列表");
    }

    @Test
    void shouldMapChineseJsonToInternalSeverityWithoutChangingStoredFormat() {
        String json = """
                {"issues":[
                  {"severity":"严重","category":"世界规则","description":"违反设定","evidence":"死者复生。"},
                  {"severity":"一般","category":"剧情","description":"因果跳跃","evidence":"他突然离开。"},
                  {"severity":"轻微","category":"文风","description":"重复用词","evidence":"夜色很黑很黑。"}
                ]}
                """;
        System.out.println("离线模型返回：\n" + json);
        ReviewReportVO result = portReturning(json, new AtomicReference<>())
                .call(REVIEW_SYSTEM_PROMPT, "待审正文", ReviewReportVO.class);

        System.out.println("后端映射结果：" + result);
        assertThat(result.getReviewIssueVOList()).extracting(ReviewIssueVO::getSeverity)
                .containsExactly(SeverityEnum.BLOCKER, SeverityEnum.MAJOR, SeverityEnum.MINOR);
        assertThat(result.getReviewIssueVOList().get(0))
                .extracting(ReviewIssueVO::getCategory, ReviewIssueVO::getDescription, ReviewIssueVO::getEvidence)
                .containsExactly("世界规则", "违反设定", "死者复生。");
        assertThat(result.getReviewIssueVOList().get(0))
                .extracting(ReviewIssueVO::getIssueType, ReviewIssueVO::getCheckerType,
                        ReviewIssueVO::getCurrentEvidence, ReviewIssueVO::getReason)
                .containsExactly("WORLD_RULE", "SEMANTIC", "死者复生。", "违反设定");
        assertThat(result.hasBlock()).isTrue();
        assertThat(result.requiresRevision()).isTrue();
        String storedJson = new ObjectMapper().writeValueAsString(result);
        System.out.println("后端序列化结果：" + storedJson);
        assertThat(storedJson).contains("\"BLOCKER\"", "\"MAJOR\"", "\"MINOR\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"BLOCKER", "MAJOR", "MINOR", "未知", "严重 | 一般 | 轻微"})
    void shouldRejectInvalidModelSeverity(String severity) {
        String json = "{\"issues\":[{\"severity\":"
                + new ObjectMapper().writeValueAsString(severity)
                + ",\"category\":\"剧情\",\"description\":\"问题\",\"evidence\":\"原文\"}]}";
        System.out.println("检查非法模型等级：" + severity);
        AppException failure = catchThrowableOfType(() -> portReturning(json, new AtomicReference<>())
                .call(REVIEW_SYSTEM_PROMPT, "原文", ReviewReportVO.class), AppException.class);
        assertThat(failure.getInternalDetail()).contains("SCHEMA_VALIDATION_FAILED");
        System.out.println("非法或缺失模型等级已拒绝：" + failure.getInternalDetail());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRejectEmptyStructuredModelContentBeforeJackson(String content) {
        System.out.println("检查 REVIEW 空模型响应，contentLength="
                + (content == null ? 0 : content.length()));

        AppException exception = catchThrowableOfType(
                () -> portReturning(content, new AtomicReference<>())
                        .call(REVIEW_SYSTEM_PROMPT, "原文", ReviewReportVO.class),
                AppException.class
        );

        assertThat(exception.getCode()).isEqualTo(ResponseCode.E0001.getCode());
        assertThat(exception.getInternalDetail()).isEqualTo("章节模型返回空内容");
        assertThat(exception.getCause()).isNull();
        System.out.println("空模型响应已在 Jackson 转换前被明确拒绝：" + exception.getInternalDetail());
    }

    @Test
    void shouldClassifyNonEmptyMalformedStructuredModelContentSeparately() {
        String malformedJson = "{\"issues\":[";
        System.out.println("检查非空但结构非法的 REVIEW 响应：" + malformedJson);

        AppException exception = catchThrowableOfType(
                () -> portReturning(malformedJson, new AtomicReference<>())
                        .call(REVIEW_SYSTEM_PROMPT, "原文", ReviewReportVO.class),
                AppException.class
        );

        assertThat(exception.getCode()).isEqualTo(ResponseCode.E0007.getCode());
        assertThat(exception.getInternalDetail()).contains("结构化响应无效");
        assertThat(exception.getCause()).isNotNull();
        System.out.println("非空结构非法响应已分类为：" + exception.getCode());
    }

    @Test
    void shouldClassifyModelRequestFailureAsRetryableModelError() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("upstream unavailable");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.error(new IllegalStateException("upstream unavailable"));
            }
        };

        AppException exception = catchThrowableOfType(
                () -> new ChapterModelPort(ChatClient.builder(model).build())
                        .call(REVIEW_SYSTEM_PROMPT, "原文", ReviewReportVO.class),
                AppException.class
        );

        assertThat(exception.getCode()).isEqualTo(ResponseCode.E0001.getCode());
        assertThat(exception.getCause()).isNotNull();
        System.out.println("模型请求失败已分类为可重试错误：" + exception.getCode());
    }

    @Test
    void shouldClassifyEmptyTextModelContentAsRetryableModelError() {
        AppException exception = catchThrowableOfType(
                () -> portReturning("   ", new AtomicReference<>())
                        .call("写作系统提示", "写作用户提示"),
                AppException.class
        );

        assertThat(exception.getCode()).isEqualTo(ResponseCode.E0001.getCode());
        assertThat(exception.getInternalDetail()).isEqualTo("章节模型返回空内容");
        System.out.println("空文本模型响应已分类为可重试错误：" + exception.getCode());
    }

    private ChapterModelPort portReturning(String json, AtomicReference<String> sentPrompt) {
        ChatModel model = new ChatModel() {
            private ChatResponse response(Prompt prompt) {
                sentPrompt.set(prompt.getContents());
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                return response(prompt);
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> Flux.just(response(prompt)));
            }
        };
        return new ChapterModelPort(ChatClient.builder(model).build());
    }
}
