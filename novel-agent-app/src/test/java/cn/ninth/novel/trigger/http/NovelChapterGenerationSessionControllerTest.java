package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerationSessionResponseDTO;
import cn.ninth.novel.api.dto.GenerationSessionSnapshotResponseDTO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.domain.chapter.service.session.ChapterGenerationSessionSnapshot;
import cn.ninth.novel.types.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NovelChapterGenerationSessionControllerTest {

    @Test
    void shouldCreateGenerationSessionWithProjectChapterPath() throws Exception {
        RecordingChapterService service = new RecordingChapterService();
        NovelChapterGenerationSessionController controller =
                new NovelChapterGenerationSessionController(service);

        Response<GenerationSessionResponseDTO> response =
                controller.createGenerationSession("novel-001", 3);

        Method method = NovelChapterGenerationSessionController.class
                .getDeclaredMethod("createGenerationSession", String.class, int.class);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/{projectCode}/chapters/{chapterNumber}/generation-sessions");
        assertThat(service.projectCode).isEqualTo("novel-001");
        assertThat(service.chapterNumber).isEqualTo(3);
        assertThat(response.data().workflowId()).isEqualTo("workflow-session-001");
        System.out.println("Session API 已创建 workflowId=workflow-session-001，且传递了项目编码和章节号");
    }

    @Test
    void shouldFindActiveGenerationSessionWithProjectChapterPath() throws Exception {
        RecordingChapterService service = new RecordingChapterService();
        NovelChapterGenerationSessionController controller =
                new NovelChapterGenerationSessionController(service);

        Response<?> response =
                controller.findActiveGenerationSession("novel-001", 3);

        Method method = NovelChapterGenerationSessionController.class
                .getDeclaredMethod("findActiveGenerationSession", String.class, int.class);
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/{projectCode}/chapters/{chapterNumber}/generation-sessions");
        assertThat(service.projectCode).isEqualTo("novel-001");
        assertThat(service.chapterNumber).isEqualTo(3);
        assertThat(response.data()).isInstanceOf(GenerationSessionSnapshotResponseDTO.class);
        GenerationSessionSnapshotResponseDTO snapshot =
                (GenerationSessionSnapshotResponseDTO) response.data();
        assertThat(snapshot.workflowId()).isEqualTo("workflow-active-001");
        assertThat(snapshot.chapterNumber()).isEqualTo(3);
        assertThat(snapshot.status()).isEqualTo("WAITING_HUMAN");
        assertThat(snapshot.currentNode()).isEqualTo("HUMAN");
        assertThat(snapshot.accumulatedContent()).isEqualTo("当前正文");
        assertThat(snapshot.completedStages()).containsExactly("DRAFT", "REVIEW");
        assertThat(snapshot.reviewIssues()).containsExactly("时间线冲突");
        assertThat(snapshot.failureMessage()).isEmpty();
        System.out.println("活动 Session API 已返回 workflowId、章节号、状态、正文、阶段、Review 问题和失败原因");
    }

    private static final class RecordingChapterService implements IChapterService {
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
            return "workflow-session-001";
        }

        @Override
        public Optional<ChapterGenerationSessionSnapshot> findActiveGenerationSession(String projectCode, int chapterNumber) {
            this.projectCode = projectCode;
            this.chapterNumber = chapterNumber;
            return Optional.of(new ChapterGenerationSessionSnapshot(
                    "workflow-active-001",
                    chapterNumber,
                    "WAITING_HUMAN",
                    "HUMAN",
                    "当前正文",
                    List.of("DRAFT", "REVIEW"),
                    List.of("时间线冲突"),
                    ""
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
