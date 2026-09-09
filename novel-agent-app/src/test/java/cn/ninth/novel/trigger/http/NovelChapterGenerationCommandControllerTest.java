package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerationSessionCommandRequestDTO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.types.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NovelChapterGenerationCommandControllerTest {

    @Test
    void shouldAcceptStopCommandForGenerationSession() throws Exception {
        RecordingChapterService service = new RecordingChapterService();
        NovelChapterGenerationCommandController controller =
                new NovelChapterGenerationCommandController(service);

        Response<Void> response = controller.command(
                "workflow-stop",
                new GenerationSessionCommandRequestDTO("STOP")
        );

        Method method = NovelChapterGenerationCommandController.class
                .getDeclaredMethod(
                        "command",
                        String.class,
                        GenerationSessionCommandRequestDTO.class
                );
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/{workflowId}/commands");
        assertThat(service.stoppedWorkflowId).isEqualTo("workflow-stop");
        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isNull();
        System.out.println("STOP command Controller 契约通过：workflowId 已传入领域服务");
    }

    @Test
    void shouldAcceptAcceptCommandForGenerationSession() {
        RecordingChapterService service = new RecordingChapterService();
        NovelChapterGenerationCommandController controller =
                new NovelChapterGenerationCommandController(service);

        Response<Void> response = controller.command(
                "workflow-accept",
                new GenerationSessionCommandRequestDTO("ACCEPT")
        );

        assertThat(service.acceptedWorkflowId).isEqualTo("workflow-accept");
        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isNull();
        System.out.println("ACCEPT command Controller 契约通过：workflowId 已传入领域服务");
    }

    @Test
    void shouldRejectUnsupportedCommand() {
        NovelChapterGenerationCommandController controller =
                new NovelChapterGenerationCommandController(new RecordingChapterService());

        assertThatThrownBy(() -> controller.command(
                "workflow-stop",
                new GenerationSessionCommandRequestDTO("PAUSE")
        )).isInstanceOf(cn.ninth.novel.types.exception.AppException.class);
        System.out.println("不支持的 Session command 会被拒绝");
    }

    private static final class RecordingChapterService implements IChapterService {
        private String stoppedWorkflowId;
        private String acceptedWorkflowId;

        @Override
        public ChapterGenerationResultVO generateChapter(
                String projectCode,
                int chapterNumber
        ) {
            return null;
        }

        @Override
        public void stopGenerationSession(String workflowId) {
            stoppedWorkflowId = workflowId;
        }

        @Override
        public void acceptGenerationSession(String workflowId) {
            acceptedWorkflowId = workflowId;
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
