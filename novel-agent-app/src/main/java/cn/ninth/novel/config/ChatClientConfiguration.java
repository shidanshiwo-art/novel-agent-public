package cn.ninth.novel.config;



import com.openai.core.Timeout;
import cn.ninth.novel.infrastructure.config.ChapterModelProperties;
import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import cn.ninth.novel.infrastructure.config.PlanningModelProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({
        PlanningModelProperties.class,
        ChapterModelProperties.class,
        ModelTimeoutProperties.class
})
public class ChatClientConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public OpenAiHttpClientBuilderCustomizer openAiHttpClientTimeoutCustomizer(
            ModelTimeoutProperties modelTimeoutProperties
    ) {
        return builder -> builder.timeout(Timeout.builder()
                .connect(modelTimeoutProperties.httpConnectTimeout())
                .read(modelTimeoutProperties.httpReadAndRequestTimeout())
                .request(modelTimeoutProperties.httpReadAndRequestTimeout())
                .build());
    }

    /** 保留给不启动 Spring 容器的配置契约测试和本地调用方。 */
    public OpenAiHttpClientBuilderCustomizer openAiHttpClientTimeoutCustomizer() {
        return openAiHttpClientTimeoutCustomizer(new ModelTimeoutProperties());
    }
}
