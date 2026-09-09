package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.ConfirmChapterPlanRequestDTO;
import cn.ninth.novel.domain.planning.service.IPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class ChapterPlanConfirmationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldExposeOnlyEditableConfirmFields() {
        assertThat(Arrays.stream(ConfirmChapterPlanRequestDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("draftId", "title", "summary");
        System.out.println(
                "single chapter confirm request verified: draftId/title/summary only"
        );
    }

    @Test
    void shouldConfirmOneChapterPlanWithPathChapterNumberAndEditableFieldsOnly() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapter-plans/17/confirm"
                ).contentType("application/json").content("""
                        {
                          "draftId":"draft-chapter-plan",
                          "title":"用户确认标题",
                          "summary":"用户确认摘要"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        verify(planningService).confirmChapterPlan(
                eq("novel-001"),
                eq(17),
                eq("draft-chapter-plan"),
                eq("用户确认标题"),
                eq("用户确认摘要")
        );
        System.out.println(
                "single chapter plan HTTP route verified: POST /chapter-plans/{chapterNumber}/confirm"
        );
    }
}
