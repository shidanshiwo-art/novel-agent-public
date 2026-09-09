package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.service.INovelProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelProjectController.class)
@ActiveProfiles("test")
class StoryBibleConfirmationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private INovelProjectService projectService;

    @Test
    void shouldConfirmEditedStoryBibleAndReturnFormalResponse() throws Exception {
        when(projectService.confirmStoryBible(
                eq("novel-001"),
                eq("draft-bible"),
                argThat(bible -> "修订后的梗概".equals(bible.oneSentencePremise())
                        && bible.powerSystemJson().contains("回响")
                        && bible.hardRulesJson().contains("历史不能直接改写"))
        )).thenReturn(new StoryBibleVO(
                "修订后的梗概", "主题", "矛盾", "结局", "世界",
                "{\"name\":\"回响\"}", "[\"历史不能直接改写\"]",
                "第三人称限知", "CONFIRMED"
        ));

        mockMvc.perform(post("/api/v1/novels/projects/novel-001/bible/confirm")
                        .contentType("application/json")
                        .content("""
                                {
                                  "draftId":"draft-bible",
                                  "oneSentencePremise":"修订后的梗概",
                                  "coreTheme":"主题",
                                  "mainConflict":"矛盾",
                                  "endingDirection":"结局",
                                  "worldBackground":"世界",
                                  "powerSystemJson":"{\\\"name\\\":\\\"回响\\\",\\\"description\\\":\\\"读取记忆\\\",\\\"levels\\\":\\\"听见\\\",\\\"supplement\\\":\\\"混淆记忆\\\"}",
                                  "hardRulesJson":"[\\\"历史不能直接改写\\\"]",
                                  "styleGuide":"第三人称限知"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oneSentencePremise").value("修订后的梗概"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(projectService).confirmStoryBible(
                eq("novel-001"), eq("draft-bible"), argThat(draft ->
                        "修订后的梗概".equals(draft.oneSentencePremise())
                                && draft.powerSystemJson().contains("回响"))
        );
        System.out.println("story bible confirmation HTTP route verified");
    }
}
