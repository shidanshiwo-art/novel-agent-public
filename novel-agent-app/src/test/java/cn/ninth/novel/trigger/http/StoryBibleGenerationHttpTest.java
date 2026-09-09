package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.project.service.INovelProjectService;
import cn.ninth.novel.domain.project.model.valobj.PowerSystemDraftVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleDraftVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelProjectController.class)
@ActiveProfiles("test")
class StoryBibleGenerationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private INovelProjectService projectService;

    @Test
    void shouldReturnStoryBibleDraftWithoutFormalMetadata() throws Exception {
        String requirement = "一座会记忆的城市和一个寻找父亲的少年";
        StoryBibleDraftVO draft = new StoryBibleDraftVO(
                "少年在城市记忆中寻找父亲", "记忆与归属", "少年对抗抹除记忆的组织",
                "城市恢复记忆", "现代城市下埋藏着会记忆的旧建筑",
                new PowerSystemDraftVO("回响", "读取建筑记忆", "听见", "混淆自身记忆"),
                List.of("历史不能直接改写"), "第三人称限知"
        );
        when(projectService.generateStoryBible("novel-001", requirement))
                .thenReturn(new PlanningDraftVO(
                        "draft-bible", "novel-001", "STORY_BIBLE", draft,
                        Instant.parse("2026-08-29T01:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/novels/projects/novel-001/bible/generate")
                        .contentType("application/json")
                        .content("""
                                {"requirement":"一座会记忆的城市和一个寻找父亲的少年"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value("draft-bible"))
                .andExpect(jsonPath("$.data.draftType").value("STORY_BIBLE"))
                .andExpect(jsonPath("$.data.payload.oneSentencePremise")
                        .value("少年在城市记忆中寻找父亲"))
                .andExpect(jsonPath("$.data.payload.powerSystem.levels")
                        .value("听见"))
                .andExpect(jsonPath("$.data.payload.status").doesNotExist())
                .andExpect(jsonPath("$.data.payload.projectCode").doesNotExist());

        verify(projectService).generateStoryBible("novel-001", requirement);
        System.out.println("story bible generation HTTP draft route verified");
    }
}
