package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateViewHumanRevisionInstructionContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldSubmitOptionalHumanRevisionInstruction() throws IOException {
        String view = Files.readString(
                PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue")
        );

        assertThat(view)
                .contains("人工修改意见")
                .contains("v-model=\"revisionInstruction\"")
                .contains("留空则按审阅问题重写")
                .contains("v-else class=\"revision-limit-notice\"")
                .contains("人工返修机会已用完")
                .contains("revisionInstruction: revisionInstruction.value.trim() || undefined")
                .contains("revisionInstruction.value = ''");
    }
}
