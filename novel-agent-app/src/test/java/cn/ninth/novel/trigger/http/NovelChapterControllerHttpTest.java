package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterGenerationResultVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.HumanDecisionEnum;
import cn.ninth.novel.domain.chapter.service.IChapterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelChapterController.class)
@Import(NovelChapterControllerHttpTest.ControllerTestConfiguration.class)
@ActiveProfiles("test")
class NovelChapterControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecordingChapterService chapterService;

    @Test
    void shouldGenerateAChapterThroughHttp() throws Exception {
        mockMvc.perform(post("/api/v1/novels/chapters/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"demo-story\",\"chapterNumber\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.workflowId").value("workflow-demo-story-1"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.canHumanRevise").value(false))
                .andExpect(jsonPath("$.data.projectId").value("demo-story"))
                .andExpect(jsonPath("$.data.chapterNumber").value(1))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.chapterMemory").doesNotExist())
                .andExpect(jsonPath("$.data.completedStages[0]").value("LOAD_CONTEXT"))
                .andExpect(jsonPath("$.data.completedStages[6]").value("PERSIST"));
    }

    @Test
    void shouldResumeWithRevisionInstructionThroughHttp() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/chapters/workflow-demo-story-1/resume"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "humanDecision": "REVISE",
                                  "revisionInstruction": "加强钟楼环境压迫感，并减少解释性对白"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId")
                        .value("workflow-demo-story-1"));

        assertThat(chapterService.lastDecision)
                .isEqualTo(HumanDecisionEnum.REVISE);
        assertThat(chapterService.lastRevisionInstruction)
                .isEqualTo("加强钟楼环境压迫感，并减少解释性对白");
    }

    @TestConfiguration
    static class ControllerTestConfiguration {

        @Bean
        RecordingChapterService chapterService() {
            return new RecordingChapterService();
        }
    }

    static final class RecordingChapterService implements IChapterService {

        private HumanDecisionEnum lastDecision;
        private String lastRevisionInstruction;

        @Override
        public ChapterGenerationResultVO generateChapter(
                String projectCode,
                int chapterNumber
        ) {
            return completedResult(
                    "workflow-" + projectCode + "-" + chapterNumber,
                    projectCode,
                    chapterNumber
            );
        }

        @Override
        public ChapterGenerationResultVO resumeChapter(
                String workflowId,
                HumanDecisionEnum decision
        ) {
            lastDecision = decision;
            lastRevisionInstruction = null;
            return completedResult(workflowId, "demo-story", 1);
        }

        @Override
        public ChapterGenerationResultVO resumeChapter(
                String workflowId,
                HumanDecisionEnum decision,
                String revisionInstruction
        ) {
            lastDecision = decision;
            lastRevisionInstruction = revisionInstruction;
            return completedResult(workflowId, "demo-story", 1);
        }

        private ChapterGenerationResultVO completedResult(
                String workflowId,
                String projectCode,
                int chapterNumber
        ) {
            return new ChapterGenerationResultVO(
                    workflowId,
                    ChapterWorkflowStatusEnum.COMPLETED,
                    projectCode,
                    chapterNumber,
                    "第一章正文",
                    null,
                    ChapterMemoryVO.builder()
                            .shortSummary("主角获得线索")
                            .keyEvents(List.of("主角获得线索"))
                            .build(),
                    List.of(
                            "LOAD_CONTEXT",
                            "PLAN",
                            "DRAFT",
                            "REVIEW",
                            "COMPRESSION",
                            "PERSIST_PREPARE",
                            "PERSIST"
                    )
            );
        }
    }
}
