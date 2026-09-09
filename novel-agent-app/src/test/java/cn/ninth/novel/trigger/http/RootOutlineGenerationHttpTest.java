package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.planning.service.IPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class RootOutlineGenerationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldGenerateRootOutlineDraftFromRequirement() throws Exception {
        String requirement = "主角从边城查明灭门真相，最终守护天下";
        OutlineNodeVO root = new OutlineNodeVO(
                "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                "归途", "主角查明灭门真相并守护天下的全书主线",
                1, 120, "PLANNED"
        );
        when(planningService.generateRootOutline("novel-001", requirement))
                .thenReturn(new PlanningDraftVO(
                        "draft-root", "novel-001", "ROOT_OUTLINE", root,
                        Instant.parse("2026-08-28T01:00:00Z")
                ));

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/root/generate"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "requirement":"主角从边城查明灭门真相，最终守护天下"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.draftId").value("draft-root"))
                .andExpect(jsonPath("$.data.draftType").value("ROOT_OUTLINE"))
                .andExpect(jsonPath("$.data.payload.nodeCode").value("BOOK_001"))
                .andExpect(jsonPath("$.data.payload.parentNodeCode").doesNotExist())
                .andExpect(jsonPath("$.data.payload.nodeKind").value("BOOK"))
                .andExpect(jsonPath("$.data.payload.startChapter").value(1))
                .andExpect(jsonPath("$.data.payload.endChapter").value(120))
                .andExpect(jsonPath("$.data.payload.title").value("归途"))
                .andExpect(jsonPath("$.data.payload.summary")
                        .value("主角查明灭门真相并守护天下的全书主线"));

        verify(planningService).generateRootOutline("novel-001", requirement);
        System.out.println("root outline HTTP draft route verified: POST /outlines/root/generate");
    }
}
