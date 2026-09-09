package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelChapterGenerationSessionController.class)
@Import(NovelChapterGenerationSessionControllerHttpTest.ControllerTestConfiguration.class)
@ActiveProfiles("test")
class NovelChapterGenerationSessionControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingChapterService chapterService;

    @Test
    void shouldReturnWorkflowIdWithoutWaitingForChapterResult() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapters/3/generation-sessions"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.workflowId").value("workflow-http-session"))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.content").doesNotExist());

        assertThat(chapterService.projectCode).isEqualTo("novel-001");
        assertThat(chapterService.chapterNumber).isEqualTo(3);
        System.out.println("HTTP Session API 立即返回 workflowId，响应未包含同步章节正文或工作流结果");
    }

    @Test
    void shouldReturnActiveWorkflowIdOrNullForProjectChapterQuery() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/novels/projects/novel-001/chapters/3/generation-sessions"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.workflowId").value("workflow-active-session"))
                .andExpect(jsonPath("$.data.chapterNumber").value(3))
                .andExpect(jsonPath("$.data.status").value("REVIEW_FAILED"))
                .andExpect(jsonPath("$.data.currentNode").value("REVIEW"))
                .andExpect(jsonPath("$.data.accumulatedContent").value("已生成正文"))
                .andExpect(jsonPath("$.data.completedStages[0]").value("DRAFT"))
                .andExpect(jsonPath("$.data.completedStages[1]").value("REVIEW"))
                .andExpect(jsonPath("$.data.reviewIssues[0]").value("人物动机不足"))
                .andExpect(jsonPath("$.data.failureMessage").value(
                        "自动审稿失败，但正文已保留。你可以重新审稿、直接采用当前正文或停止本次生成。"));

        assertThat(chapterService.projectCode).isEqualTo("novel-001");
        assertThat(chapterService.chapterNumber).isEqualTo(3);
        System.out.println("HTTP 活动 Session 查询路径和 workflowId 响应契约通过");
    }

    @TestConfiguration
    static class ControllerTestConfiguration {

        @Bean
        RecordingChapterService chapterService() {
            return new RecordingChapterService();
        }
    }

    static final class RecordingChapterService implements IChapterService {
        private String projectCode;
        private int chapterNumber;

        @Override
        public ChapterGenerationResultVO generateChapter(
                String projectCode,
                int chapterNumber
        ) {
            return null;
        }

        @Override
        public String createGenerationSession(String projectCode, int chapterNumber) {
            this.projectCode = projectCode;
            this.chapterNumber = chapterNumber;
            return "workflow-http-session";
        }

        @Override
        public Optional<ChapterGenerationSessionSnapshot> findActiveGenerationSession(String projectCode, int chapterNumber) {
            this.projectCode = projectCode;
            this.chapterNumber = chapterNumber;
            return Optional.of(new ChapterGenerationSessionSnapshot(
                    "workflow-active-session",
                    chapterNumber,
                    "REVIEW_FAILED",
                    "REVIEW",
                    "已生成正文",
                    List.of("DRAFT", "REVIEW"),
                    List.of("人物动机不足"),
                    "自动审稿失败，但正文已保留。你可以重新审稿、直接采用当前正文或停止本次生成。"
            ));
        }

        @Override
        public ChapterGenerationResultVO resumeChapter(
                String workflowId,
                HumanDecisionEnum decision
        ) {
            return null;
        }
    }
}
