package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.ConfirmArcRegenerationRequestDTO;
import cn.ninth.novel.api.dto.GenerateArcRegenerationRequestDTO;
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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class ArcRegenerationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldKeepRegenerationRequestsLimitedToRequirementAndEditableContent() {
        assertThat(Arrays.stream(GenerateArcRegenerationRequestDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("requirement");
        assertThat(Arrays.stream(ConfirmArcRegenerationRequestDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("draftId", "title", "summary");
        System.out.println("ARC regeneration request fields verified: no chapter-number input");
    }

    @Test
    void shouldGenerateArcRegenerationDraftWithServerAssignedStructure() throws Exception {
        OutlineNodeVO payload = new OutlineNodeVO(
                "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                "重生成标题", "重生成概要", 21, 21, "READY"
        );
        when(planningService.generateArcRegeneration(
                "novel-001", "ARC_001", "加强悬念"
        )).thenReturn(new PlanningDraftVO(
                "draft-arc-regeneration", "novel-001", "ARC_REGENERATION", payload,
                Instant.parse("2026-08-30T01:00:00Z")
        ));

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/nodes/ARC_001/regenerate"
                )
                        .contentType("application/json")
                        .content("""
                                {"requirement":"加强悬念"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.draftType").value("ARC_REGENERATION"))
                .andExpect(jsonPath("$.data.payload.nodeCode").value("ARC_001"))
                .andExpect(jsonPath("$.data.payload.nodeKind").value("ARC"))
                .andExpect(jsonPath("$.data.payload.startChapter").value(21))
                .andExpect(jsonPath("$.data.payload.endChapter").value(21));

        verify(planningService).generateArcRegeneration(
                eq("novel-001"), eq("ARC_001"), eq("加强悬念")
        );
        System.out.println("ARC regeneration HTTP draft route verified: fixed chapter structure returned");
    }

    @Test
    void shouldConfirmArcRegenerationUsingOnlyEditedTitleAndSummary() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/nodes/ARC_001/regenerate/confirm"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "draftId":"draft-arc-regeneration",
                                  "title":"用户确认标题",
                                  "summary":"用户确认概要"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        verify(planningService).confirmArcRegeneration(
                eq("novel-001"), eq("ARC_001"), eq("draft-arc-regeneration"),
                eq("用户确认标题"), eq("用户确认概要")
        );
        System.out.println("ARC regeneration confirmation HTTP route verified: UPDATE flow request");
    }
}
