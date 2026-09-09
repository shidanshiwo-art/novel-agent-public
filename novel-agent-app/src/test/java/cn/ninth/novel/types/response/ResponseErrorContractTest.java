package cn.ninth.novel.types.response;

import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseErrorContractTest {

    @Test
    void shouldUseResponseCodeMessageWhenAppExceptionHasNoUserMessage() {
        Response<Void> response = Response.failure(
                new AppException(ResponseCode.E0003.getCode())
        );

        assertThat(response.code()).isEqualTo(ResponseCode.E0003.getCode());
        assertThat(response.info()).isEqualTo(ResponseCode.E0003.getMessage());
        System.out.println(
                "无业务提示的 AppException 已回退到 ResponseCode.message："
                        + response.info()
        );
    }

    @Test
    void shouldKeepExplicitUserMessageAndHideInternalDetail() {
        Response<Void> userResponse = Response.failure(AppException.user(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "角色被引用，请先处理相关引用后再删除"
        ));
        AppException internalException = AppException.internal(
                ResponseCode.E0004.getCode(),
                "JSON 解析失败，reviewIssueVOList[0].evidence=nodeCode"
        );
        Response<Void> internalResponse = Response.failure(internalException);

        assertThat(userResponse.info())
                .isEqualTo("角色被引用，请先处理相关引用后再删除");
        assertThat(internalResponse.info())
                .isEqualTo(ResponseCode.E0004.getMessage())
                .doesNotContain("JSON", "nodeCode");
        assertThat(internalException.getInfo()).isNull();
        assertThat(internalException.getInternalDetail())
                .contains("JSON", "nodeCode");
        System.out.println(
                "业务提示保留、内部模型/JSON 详情隐藏：user="
                        + userResponse.info() + "，internal=" + internalResponse.info()
        );
    }

    @Test
    void shouldKeepBusinessConditionMessageUnderIllegalParameterCode() {
        String message = "当前卷尚未规划章节，请先完成本卷章节规划。";

        Response<Void> response = Response.failure(AppException.user(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                message
        ));

        assertThat(response.code()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
        assertThat(response.info())
                .isEqualTo(message)
                .doesNotStartWith(ResponseCode.ILLEGAL_PARAMETER.getMessage());
        System.out.println("明确业务条件文案原样返回：" + response.info());
    }

    @Test
    void shouldHideStructuredModelExceptionDetailsFromResponse() {
        ChapterModelResponseException exception = new ChapterModelResponseException(
                ResponseCode.E0007.getCode(),
                "JSON parse error: MismatchedInputException",
                new IllegalArgumentException("Jackson detail"),
                "{malformed"
        );
        Response<Void> response = Response.failure(exception);

        assertThat(response.code()).isEqualTo(ResponseCode.E0007.getCode());
        assertThat(exception.getUserMessage()).isNull();
        assertThat(exception.getInternalDetail())
                .contains("JSON", "MismatchedInputException");
        assertThat(response.info())
                .isEqualTo(ResponseCode.E0007.getMessage())
                .doesNotContain("JSON", "MismatchedInputException", "Jackson");
        System.out.println("结构化模型异常仅返回 E0007 安全提示，Jackson/JSON 详情未进入响应");
    }

    @Test
    void shouldExposeOnlySafePublicConstructorsAndExplicitFactories() {
        String publicConstructors = Arrays.stream(AppException.class.getConstructors())
                .map(constructor -> Arrays.stream(constructor.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(",")))
                .sorted()
                .collect(Collectors.joining(";"));
        Throwable cause = new IllegalStateException("raw model detail");
        AppException userException = AppException.user("0002", "当前章节不存在", cause);
        AppException internalException = AppException.internal(
                ResponseCode.E0004.getCode(),
                "ReviewReportVO parse failed: raw field detail",
                cause
        );

        assertThat(publicConstructors)
                .isEqualTo("java.lang.String;java.lang.String,java.lang.Throwable");
        assertThat(userException.getUserMessage()).isEqualTo("当前章节不存在");
        assertThat(userException.getInternalDetail()).isNull();
        assertThat(internalException.getUserMessage()).isNull();
        assertThat(internalException.getInternalDetail()).contains("ReviewReportVO", "raw field detail");
        assertThat(internalException.getCause()).isSameAs(cause);
        System.out.println(
                "AppException 仅保留基础公开构造器，user/internal 工厂语义明确："
                        + publicConstructors
        );
    }
}
