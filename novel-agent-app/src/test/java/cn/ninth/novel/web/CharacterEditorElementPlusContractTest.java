package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterEditorElementPlusContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseElementPlusForCharacterCardAndDrawerClose() throws IOException {
        String view = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String characterArea = section(view, "<!-- 角色库 -->", "      </el-tab-pane>");

        System.out.printf("character editor controls checked: areaLength=%d%n", characterArea.length());
        assertThat(characterArea)
                .contains("<el-card", ":icon=\"Close\"", "<el-button")
                .doesNotContain("<button", "</button>", ">×<");
    }

    private String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex);
    }
}
