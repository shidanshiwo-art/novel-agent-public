package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.service.INovelProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelProjectController.class)
@ActiveProfiles("test")
class CharacterDraftConfirmationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private INovelProjectService projectService;

    @Test
    void shouldConfirmEditedCharacterDraft() throws Exception {
        CharacterDraftVO character = new CharacterDraftVO(
                "林澈·修订", "男主角", "男", "二十八岁", "更显疲惫",
                "理性但开始动摇", "修订后的人物经历", "确认后的备注"
        );
        when(projectService.confirmCharacters("novel-001", "draft-character-001", List.of(character)))
                .thenReturn(List.of(new StoryCharacterVO(
                        "char-generated", "林澈·修订", "MALE_LEAD", "MALE", "二十八岁",
                        "更显疲惫", "理性但开始动摇", "修订后的人物经历", "确认后的备注",
                        "{}", "ALIVE", "ACTIVE"
                )));

        mockMvc.perform(post("/api/v1/novels/projects/novel-001/characters/confirm")
                        .contentType("application/json")
                        .content("""
                                {
                                  "draftId":"draft-character-001",
                                  "characters":[{
                                    "name":"林澈·修订",
                                    "role":"男主角",
                                    "gender":"男",
                                    "ageDescription":"二十八岁",
                                    "appearance":"更显疲惫",
                                    "personality":"理性但开始动摇",
                                    "backgroundStory":"修订后的人物经历",
                                    "note":"确认后的备注"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].characterCode").value("char-generated"))
                .andExpect(jsonPath("$.data[0].name").value("林澈·修订"))
                .andExpect(jsonPath("$.data[0].lifeStatus").value("ALIVE"));

        verify(projectService).confirmCharacters(
                eq("novel-001"), eq("draft-character-001"), eq(List.of(character))
        );
        System.out.println("character draft confirmation HTTP route verified");
    }

    @Test
    void shouldDiscardCharacterDraft() throws Exception {
                mockMvc.perform(post("/api/v1/novels/projects/novel-001/characters/discard")
                        .contentType("application/json")
                        .content("{\"draftId\":\"draft-character-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        verify(projectService).discardCharacterDraft("novel-001", "draft-character-001");
        System.out.println("character draft discard HTTP route verified");
    }

    @Test
    void shouldKeepConfirmationRequestInBusinessFields() {
        assertThat(cn.ninth.novel.api.dto.ConfirmCharacterDraftDTO.class.getRecordComponents())
                .hasSize(8);
        System.out.println("character draft confirmation request fields verified");
    }
}
