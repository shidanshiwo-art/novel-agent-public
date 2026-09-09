package cn.ninth.novel.config;

import cn.ninth.novel.infrastructure.config.ModelTimeoutProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTimeoutPropertiesTest {

    @Test
    void shouldBindStageTimeoutsAndDeriveHttpClientUpperBound() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "novel.model.structured-timeouts.CHARACTER_GENERATION=180s",
                        "novel.model.structured-timeouts.ROOT_OUTLINE=240s",
                        "novel.model.structured-timeouts.REVIEW=300s"
                )
                .run(context -> {
                    ModelTimeoutProperties properties =
                            context.getBean(ModelTimeoutProperties.class);

                    assertThat(properties.timeoutForStage("character_generation"))
                            .isEqualTo(Duration.ofSeconds(180));
                    assertThat(properties.timeoutForStage("ROOT_OUTLINE"))
                            .isEqualTo(Duration.ofSeconds(240));
                    assertThat(properties.timeoutForStage("REVIEW"))
                            .isEqualTo(Duration.ofSeconds(300));
                    assertThat(properties.maximumStructuredTimeout())
                            .isEqualTo(Duration.ofSeconds(300));
                    assertThat(properties.httpReadAndRequestTimeout())
                            .isEqualTo(Duration.ofSeconds(330));
                    System.out.printf(
                            "模型 stage 超时配置绑定通过：CHARACTER_GENERATION=%s，ROOT_OUTLINE=%s，REVIEW=%s，HTTP 上限=%s%n",
                            properties.timeoutForStage("CHARACTER_GENERATION"),
                            properties.timeoutForStage("ROOT_OUTLINE"),
                            properties.timeoutForStage("REVIEW"),
                            properties.httpReadAndRequestTimeout()
                    );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ModelTimeoutProperties.class)
    static class PropertiesConfiguration {
    }
}
