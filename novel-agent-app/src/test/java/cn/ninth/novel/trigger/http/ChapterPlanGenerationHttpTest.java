package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.GenerateChapterPlanRequestDTO;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.service.IPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class ChapterPlanGenerationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldExposeOnlyRequirementForGenerateRequest() {
        assertThat(Arrays.stream(GenerateChapterPlanRequestDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("requirement");
        System.out.println("single chapter generate request verified: requirement only");
    }

    @Test
    void shouldGenerateOneChapterPlanWithRequirement() throws Exception {
        when(planningService.generateChapterPlan("novel-001", 17, "继续推进冲突"))
                .thenReturn(new PlanningDraftVO(
                        "draft-chapter-plan", "novel-001", "CHAPTER_PLAN",
                        new ChapterOutlineVO(17, "ARC_001", "第十七章", "冲突升级", "PLANNED"),
                        Instant.parse("2026-08-28T01:00:00Z")
                ));

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapter-plans/17/generate"
                ).contentType("application/json").content("""
                        {"requirement":"继续推进冲突"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.draftId").value("draft-chapter-plan"))
                .andExpect(jsonPath("$.data.draftType").value("CHAPTER_PLAN"))
                .andExpect(jsonPath("$.data.payload.chapterNumber").value(17))
                .andExpect(jsonPath("$.data.payload.title").value("第十七章"))
                .andExpect(jsonPath("$.data.payload.summary").value("冲突升级"));

        verify(planningService).generateChapterPlan(
                eq("novel-001"), eq(17), eq("继续推进冲突")
        );
        System.out.println(
                "single chapter plan HTTP route verified: POST /chapter-plans/{chapterNumber}/generate"
        );
    }

    @Test
    void shouldAllowGenerateRequestWithoutRequirement() throws Exception {
        when(planningService.generateChapterPlan("novel-001", 17, null))
                .thenReturn(new PlanningDraftVO(
                        "draft-chapter-plan", "novel-001", "CHAPTER_PLAN",
                        new ChapterOutlineVO(17, "ARC_001", "自然续写", "承接前文", "PLANNED"),
                        Instant.parse("2026-08-28T01:00:00Z")
                ));

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapter-plans/17/generate"
                ).contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payload.title").value("自然续写"));

        verify(planningService).generateChapterPlan(
                eq("novel-001"), eq(17), eq(null)
        );
        System.out.println("single chapter plan HTTP request accepts missing optional requirement");
    }
}
