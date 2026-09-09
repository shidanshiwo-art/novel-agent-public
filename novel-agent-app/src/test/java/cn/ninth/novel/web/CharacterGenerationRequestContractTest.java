package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterGenerationRequestContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldExposePreferredCountAcrossCharacterGenerationRequest() throws IOException {
        String dto = Files.readString(PROJECT_ROOT.resolve(
                "novel-agent-api/src/main/java/cn/ninth/novel/api/dto/GenerateCharacterRequestDTO.java"));
        String types = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/types/index.ts"));
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));

        System.out.printf("人物生成请求契约检查：DTO=%s，前端类型=%s，生成界面=%s%n",
                dto.contains("preferredCount"),
                types.contains("preferredCount"),
                view.contains("characterPreferredCount"));

        assertThat(dto)
                .contains("Integer preferredCount")
                .contains("String requirement");
        assertThat(types).contains("preferredCount: number");
        assertThat(view)
                .contains("v-model=\"characterPreferredCount\"")
                .contains(":min=\"1\"")
                .contains(":max=\"15\"")
                .contains("preferredCount: characterPreferredCount.value");
    }
}
