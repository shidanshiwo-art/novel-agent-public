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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelPlanningController.class)
@ActiveProfiles("test")
class ChildOutlineGenerationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPlanningService planningService;

    @Test
    void shouldCreateOneNextVolumeStructureThroughDedicatedEndpoint() throws Exception {
        OutlineNodeVO nextVolume = new OutlineNodeVO(
                "VOL_002", "BOOK_001", OutlineNodeKindEnum.VOLUME, 2,
                "卷二", "", 20, 120, "UNPLANNED"
        );
        when(planningService.createNextVolume(
                "novel-001", "BOOK_001"
        )).thenReturn(nextVolume);

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/BOOK_001/volumes/create"
                )
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.nodeCode").value("VOL_002"))
                .andExpect(jsonPath("$.data.parentNodeCode").value("BOOK_001"))
                .andExpect(jsonPath("$.data.nodeKind").value("VOLUME"))
                .andExpect(jsonPath("$.data.sequenceNo").value(2))
                .andExpect(jsonPath("$.data.title").value("卷二"))
                .andExpect(jsonPath("$.data.status").value("UNPLANNED"));

        verify(planningService).createNextVolume(eq("novel-001"), eq("BOOK_001"));
        System.out.println("HTTP 下一卷创建契约已验证：服务端返回 VOL_002/卷二 UNPLANNED 结构节点");
    }

    @Test
    void shouldGenerateOneNextChapterWithOnlyRequirement() throws Exception {
        String requirement = "推进主角第一次调查";
        OutlineNodeVO nextChapter = new OutlineNodeVO(
                "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                "第一章", "主角开始调查", 1, 1, "PLANNED"
        );
        when(planningService.generateNextOutline(
                "novel-001", "VOL_001", requirement
        )).thenReturn(new PlanningDraftVO(
                "draft-next-chapter", "novel-001", "NEXT_OUTLINE", nextChapter,
                Instant.parse("2026-09-04T01:00:00Z")
        ));

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/VOL_001/children/generate"
                )
                        .contentType("application/json")
                        .content("""
                                {"requirement":"推进主角第一次调查"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value("draft-next-chapter"))
                .andExpect(jsonPath("$.data.payload.nodeKind").value("ARC"))
                .andExpect(jsonPath("$.data.payload.startChapter").value(1))
                .andExpect(jsonPath("$.data.payload.endChapter").value(1));

        verify(planningService).generateNextOutline(
                eq("novel-001"), eq("VOL_001"), eq(requirement)
        );
        System.out.println("HTTP 下一章生成契约已验证：VOLUME 只返回一个 ARC");
    }

    @Test
    void shouldGenerateCurrentVolumeOutlineThroughDedicatedEndpoint() throws Exception {
        String requirement = "突出边城调查";
        OutlineNodeVO volume = new OutlineNodeVO(
                "VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME, 1,
                "边城调查", "卷级剧情", 1, 60, "UNPLANNED"
        );
        when(planningService.generateVolumeOutline(
                "novel-001", "VOL_001", requirement
        )).thenReturn(new PlanningDraftVO(
                "draft-volume", "novel-001", "VOLUME_OUTLINE", volume,
                Instant.parse("2026-09-04T01:00:00Z")
        ));

        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/outlines/volumes/VOL_001/generate"
                )
                        .contentType("application/json")
                        .content("""
                                {"requirement":"突出边城调查"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftType").value("VOLUME_OUTLINE"))
                .andExpect(jsonPath("$.data.payload.nodeCode").value("VOL_001"))
                .andExpect(jsonPath("$.data.payload.nodeKind").value("VOLUME"))
                .andExpect(jsonPath("$.data.payload.sequenceNo").value(1));

        verify(planningService).generateVolumeOutline(
                eq("novel-001"), eq("VOL_001"), eq(requirement)
        );
        System.out.println("HTTP 当前卷卷纲生成契约已验证：结构字段仍绑定 VOL_001");
    }
}
