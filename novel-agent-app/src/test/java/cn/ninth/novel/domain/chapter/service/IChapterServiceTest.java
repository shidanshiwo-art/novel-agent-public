package cn.ninth.novel.domain.chapter.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IChapterServiceTest {

    @Test
    void shouldDefineChapterGenerationAsDomainServiceContract() {
        IChapterService chapterService = new IChapterService() {
            @Override
            public ChapterGenerationResultVO generateChapter(String projectCode, int chapterNumber) {
                return result("workflow-generate", projectCode, chapterNumber);
            }

            @Override
            public ChapterGenerationResultVO resumeChapter(
                    String workflowId,
                    HumanDecisionEnum decision
            ) {
                return result(workflowId, "novel-001", 3);
            }

            private ChapterGenerationResultVO result(
                    String workflowId,
                    String projectCode,
                    int chapterNumber
            ) {
                return new ChapterGenerationResultVO(
                        workflowId,
                        ChapterWorkflowStatusEnum.COMPLETED,
                        projectCode,
                        chapterNumber,
                        "generated content",
                        null,
                        null,
                        List.of("PLAN", "DRAFT")
                );
            }
        };

        ChapterGenerationResultVO result = chapterService.generateChapter("novel-001", 3);
        ChapterGenerationResultVO resumed = chapterService.resumeChapter(
                "workflow-resume",
                HumanDecisionEnum.PASS
        );

        assertThat(result.workflowId()).isEqualTo("workflow-generate");
        assertThat(result.status()).isEqualTo(ChapterWorkflowStatusEnum.COMPLETED);
        assertThat(result.projectCode()).isEqualTo("novel-001");
        assertThat(result.chapterNumber()).isEqualTo(3);
        assertThat(result.content()).isEqualTo("generated content");
        assertThat(result.completedStages()).containsExactly("PLAN", "DRAFT");
        assertThat(resumed.workflowId()).isEqualTo("workflow-resume");
    }
}
