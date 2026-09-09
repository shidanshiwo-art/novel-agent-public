package cn.ninth.novel.types.enums;

import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.types.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseCodeContractTest {

    @Test
    void shouldKeepFiniteBusinessAndModelCodeSet() {
        List<String> codes = Arrays.stream(ResponseCode.values())
                .map(ResponseCode::getCode)
                .toList();

        assertThat(codes).containsExactlyInAnyOrder(
                "0000",
                "0001",
                "0002",
                "E0001",
                "E0002",
                "E0003",
                "E0004",
                "E0005",
                "E0006",
                "E0007",
                "E0008"
        );
        assertThat(codes).noneMatch(code -> code.matches("E01\\d{2}"));

        Response<Void> parameterFailure = Response.failure(
                AppException.internal("0002", "nodeCode=ARC_003")
        );
        Response<Void> modelFailure = Response.failure(
                AppException.internal("E0004", "MismatchedInputException: JSON parse error")
        );
        Response<Void> systemFailure = Response.failure(
                AppException.internal("0001", "Reactor Flux timeout")
        );

        assertThat(parameterFailure.code()).isEqualTo("0002");
        assertThat(modelFailure.code()).isEqualTo("E0004");
        assertThat(systemFailure.code()).isEqualTo("0001");
        assertThat(parameterFailure.info()).doesNotContain("nodeCode");
        assertThat(modelFailure.info()).doesNotContain("MismatchedInputException", "JSON");
        assertThat(systemFailure.info()).doesNotContain("Reactor", "Flux", "timeout");

        System.out.println(
                "错误码保持有限集合：参数/业务=0002，模型节点=E0004，系统未知=0001，当前共 "
                        + codes.size() + " 个码"
        );
    }

    @Test
    void shouldFallbackUnknownTechnicalCodeToUnknownSystemCode() {
        Response<Void> response = Response.failure(
                AppException.internal(
                        "E0101",
                        "Jackson MismatchedInputException nodeCode=REVIEW_003"
                )
        );

        assertThat(response.code()).isEqualTo(ResponseCode.UN_ERROR.getCode());
        assertThat(response.info())
                .isEqualTo(ResponseCode.UN_ERROR.getMessage())
                .doesNotContain("E0101", "Jackson", "nodeCode");
        System.out.println("未知技术码 E0101 已回退为系统未知码 0001，内部详情未进入响应");
    }
}
