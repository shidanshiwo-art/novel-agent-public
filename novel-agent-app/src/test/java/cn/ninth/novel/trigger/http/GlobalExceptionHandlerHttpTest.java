package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.chapter.service.IChapterService;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelChapterController.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IChapterService chapterService;

    @Test
    void shouldReturnCodeMessageInsteadOfEmptyAppExceptionInfo() throws Exception {
        when(chapterService.generateChapter("demo-story", 1))
                .thenThrow(new AppException(ResponseCode.E0003.getCode()));

        mockMvc.perform(post("/api/v1/novels/chapters/generate")
                        .contentType("application/json")
                        .content("{\"projectId\":\"demo-story\",\"chapterNumber\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.E0003.getCode()))
                .andExpect(jsonPath("$.info").value(ResponseCode.E0003.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
        System.out.println("HTTP 异常无 info 时已返回 E0003 的 ResponseCode.message");
    }

    @Test
    void shouldNotReturnUnderlyingExceptionMessage() throws Exception {
        when(chapterService.generateChapter("demo-story", 1))
                .thenThrow(new RuntimeException(
                        "SQLIntegrityConstraintViolationException: Cannot invoke ..."
                ));

        String responseBody = mockMvc.perform(post("/api/v1/novels/chapters/generate")
                        .contentType("application/json")
                        .content("{\"projectId\":\"demo-story\",\"chapterNumber\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.UN_ERROR.getCode()))
                .andExpect(jsonPath("$.info").value(
                        "系统暂时出现异常，请稍后重试。"
                ))
                .andExpect(jsonPath("$.info").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.containsString("SQLIntegrityConstraintViolationException"),
                                org.hamcrest.Matchers.containsString("Cannot invoke"),
                                org.hamcrest.Matchers.containsString("NullPointerException")
                        )
                )))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(responseBody).contains("\"data\":null");
        System.out.println("HTTP 未预期 RuntimeException 已统一返回 0001，完整堆栈仅进入 ERROR 日志");
    }

    @Test
    void shouldReturnSafeMessageForTechnicalAppException() throws Exception {
        when(chapterService.generateChapter("demo-story", 1))
                .thenThrow(AppException.internal(
                        ResponseCode.E0004.getCode(),
                        "MismatchedInputException: JSON parse error, nodeCode=ARC_003"
                ));

        String responseBody = mockMvc.perform(post("/api/v1/novels/chapters/generate")
                        .contentType("application/json")
                        .content("{\"projectId\":\"demo-story\",\"chapterNumber\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.E0004.getCode()))
                .andExpect(jsonPath("$.info").value(
                        "章节审稿失败，请重新审稿或采用当前正文。"
                ))
                .andExpect(jsonPath("$.info").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.containsString("MismatchedInputException"),
                                org.hamcrest.Matchers.containsString("JSON"),
                                org.hamcrest.Matchers.containsString("nodeCode")
                        )
                )))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(responseBody).contains("\"data\":null");
        System.out.println("HTTP 统一异常响应已验证：E0004 用户提示安全、data=null，内部技术详情未进入响应");
    }
}
