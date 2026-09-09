package cn.ninth.novel.config;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTimeoutConfigurationContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldConfigureTimeoutsOnSpringAiManagedOpenAiHttpClient() {
        ChatClientConfiguration configuration = new ChatClientConfiguration();
        SpringAiOpenAiHttpClient.Builder builder = SpringAiOpenAiHttpClient.builder();
        configuration.openAiHttpClientTimeoutCustomizer().customize(builder);

        SpringAiOpenAiHttpClient client = builder.build();
        try {
            OkHttpClient httpClient = client.getOkHttpClient();
            System.out.printf(
                    "Spring AI OpenAI HTTP 超时：connect=%dms, read=%dms, call=%dms%n",
                    httpClient.connectTimeoutMillis(),
                    httpClient.readTimeoutMillis(),
                    httpClient.callTimeoutMillis()
            );
            assertThat(httpClient.connectTimeoutMillis()).isEqualTo(Duration.ofSeconds(10).toMillis());
            assertThat(httpClient.readTimeoutMillis()).isEqualTo(Duration.ofSeconds(330).toMillis());
            assertThat(httpClient.callTimeoutMillis()).isEqualTo(Duration.ofSeconds(330).toMillis());
        } finally {
            client.close();
        }
    }

    @Test
    void shouldKeepFrontendTimeoutsLongerThanModelRequestTimeout() throws IOException {
        String dev = Files.readString(PROJECT_ROOT.resolve("novel-agent-app/src/main/resources/application-dev.yml"));
        String prod = Files.readString(PROJECT_ROOT.resolve("novel-agent-app/src/main/resources/application-prod.yml"));
        String axios = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/http.ts"));
        String vite = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/vite.config.ts"));

        System.out.println("前端/代理超时检查：模型请求上限 330s，Axios 与 Vite 代理 360s");
        assertThat(dev).contains("timeout: 330s");
        assertThat(prod).contains("timeout: 330s");
        assertThat(axios).contains("timeout: 360000");
        assertThat(vite).contains("timeout: 360000", "proxyTimeout: 360000");
    }
}
