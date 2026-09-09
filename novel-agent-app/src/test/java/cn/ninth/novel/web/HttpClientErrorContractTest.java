package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientErrorContractTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"))
            .getParent();

    @Test
    void shouldLeaveBusinessErrorPresentationToThePage() throws IOException {
        String source = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/http.ts"));

        assertThat(source)
                .contains("new ApiBusinessError(", "resolveMessage(body.info, code)",
                        "export function resolveFallbackMessage(code: string)")
                .doesNotContain("ElMessage.error", "err.message", "import { ElMessage }");
        System.out.println("http.ts 业务异常只抛出 ApiBusinessError，未自动调用 ElMessage.error");
    }

    @Test
    void shouldKeepTransportFallbackMessageTechnicalDetailFree() throws IOException {
        String source = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/http.ts"));

        assertThat(source)
                .contains("'0001': '系统暂时出现异常，请稍后重试。'",
                        "return Promise.reject(new ApiBusinessError(")
                .doesNotContain("网络错误", "请求失败", "backendInfo");
        System.out.println("http.ts HTTP/网络异常统一使用安全 fallback，未消费 Axios 原始错误文本");
    }
}
