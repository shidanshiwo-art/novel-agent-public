package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerateChapterRequestDTO;
import cn.ninth.novel.api.dto.GenerateChapterResponseDTO;
import cn.ninth.novel.api.dto.ResumeChapterRequestDTO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.types.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NovelChapterControllerTest {

    @Test
    void shouldExposeGenerateEndpointAndMapWorkflowResult() throws Exception {
        StubChapterService service = new StubChapterService();
        NovelChapterController controller = new NovelChapterController(service);

        Response<GenerateChapterResponseDTO> response = controller.generateChapter(
                new GenerateChapterRequestDTO("novel-001", 3)
        );

        assertPostMapping("generateChapter", "/generate", GenerateChapterRequestDTO.class);
        assertThat(response.data().workflowId()).isEqualTo("workflow-generate");
        assertThat(response.data().status()).isEqualTo("WAITING_HUMAN");
        assertThat(response.data().projectId()).isEqualTo("novel-001");
        assertThat(response.data().reviewIssues()).containsExactly("时间线冲突");
        assertThat(response.data().completedStages()).containsExactly("REVIEW");
    }

    @Test
    void shouldConvertOneShotDecisionAndExposeResumeEndpoint() throws Exception {
        StubChapterService service = new StubChapterService();
        NovelChapterController controller = new NovelChapterController(service);

        Response<GenerateChapterResponseDTO> response = controller.resumeChapter(
                "workflow-resume",
                new ResumeChapterRequestDTO("REVISE")
        );

        assertPostMapping(
                "resumeChapter",
                "/{workflowId}/resume",
                String.class,
                ResumeChapterRequestDTO.class
        );
        assertThat(service.lastWorkflowId).hasValue("workflow-resume");
        assertThat(service.lastDecision).hasValue(HumanDecisionEnum.REVISE);
        assertThat(response.data().workflowId()).isEqualTo("workflow-resume");
    }

    private void assertPostMapping(
            String methodName,
            String expectedPath,
            Class<?>... parameterTypes
    ) throws Exception {
        Method method = NovelChapterController.class
                .getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly(expectedPath);
    }

    private static final class StubChapterService implements IChapterService {
        private final AtomicReference<String> lastWorkflowId = new AtomicReference<>();
        private final AtomicReference<HumanDecisionEnum> lastDecision = new AtomicReference<>();

        @Override
        public ChapterGenerationResultVO generateChapter(
                String projectCode,
                int chapterNumber
        ) {
            return result("workflow-generate", projectCode, chapterNumber);
        }

        @Override
        public ChapterGenerationResultVO resumeChapter(
                String workflowId,
                HumanDecisionEnum decision
        ) {
            lastWorkflowId.set(workflowId);
            lastDecision.set(decision);
            return result(workflowId, "novel-001", 3);
        }

        private ChapterGenerationResultVO result(
                String workflowId,
                String projectCode,
                int chapterNumber
        ) {
            return new ChapterGenerationResultVO(
                    workflowId,
                    ChapterWorkflowStatusEnum.WAITING_HUMAN,
                    projectCode,
                    chapterNumber,
                    "待人工确认正文",
                    ReviewReportVO.builder()
                            .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                                    .description("时间线冲突")
                                    .build()))
                            .build(),
                    ChapterMemoryVO.builder()
                            .shortSummary("林越拿到密钥")
                            .keyEvents(List.of("林越拿到密钥"))
                            .build(),
                    List.of("REVIEW")
            );
        }
    }
}
