package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PageErrorPresentationContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldUseApiMessageForOrdinaryPageActions() throws IOException {
        String outline = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String setup = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String read = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));

        assertThat(outline)
                .contains("import { ApiBusinessError } from '../api/http'",
                        "ElMessage.error(error.message)",
                        "ElMessage.error('保存失败，请稍后重试')");
        assertThat(setup)
                .contains("import { ApiBusinessError } from '../api/http'",
                        "ElMessage.error(error.message)",
                        "ElMessage.error('故事设定保存失败，请稍后重试')")
                .doesNotContain("API 错误由统一拦截器提示");
        assertThat(read)
                .contains("@click=\"refreshChapterList\"",
                        "async function refreshChapterList()",
                        "ElMessage.error(error.message)",
                        "ElMessage.error('章节保存失败，请稍后重试')");

        System.out.println("普通页面操作错误契约通过：页面消费安全 ApiBusinessError.message，并保留各自 fallback");
    }

    @Test
    void shouldKeepWorkflowAndDecisionErrorsInsideThePage() throws IOException {
        String generate = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));

        assertThat(generate)
                .contains("workflowFailureMessage.value = error.message",
                        "workflowStateReady.value = true",
                        "workflowFailureMessage && !isReviewFailed",
                        "<section v-else-if=\"workflowStatus === 'REVIEW_FAILED'\"",
                        "workflowStatus === 'FAILED' || workflowStatus === 'CANCELLED'",
                        "@click=\"generateSingleChapter\"",
                        "重新生成",
                        "自动审稿失败，但正文已保留。",
                        "采用当前正文",
                        "重新审稿")
                .doesNotContain(
                        "if (!(error instanceof ApiBusinessError))",
                        "原因：{{ workflowFailureMessage }}",
                        "event.content?.trim() ||"
                );

        System.out.println("正文工作流错误契约通过：失败信息进入页面状态，审稿失败保留重试/采用/停止操作");
    }
}
