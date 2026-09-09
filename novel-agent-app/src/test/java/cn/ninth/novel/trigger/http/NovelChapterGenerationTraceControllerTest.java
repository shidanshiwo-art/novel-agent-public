package cn.ninth.novel.trigger.http;

import cn.ninth.novel.api.dto.PromptTraceResponseDTO;
import cn.ninth.novel.domain.chapter.adapter.repository.IPromptTraceRepository;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.types.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelChapterGenerationTraceControllerTest {

    @Test
    void shouldExposeDevelopmentTraceQueryEndpoint() throws Exception {
        GetMapping mapping = NovelChapterGenerationTraceController.class
                .getDeclaredMethod("traces", String.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/{workflowId}/traces");
        assertThat(NovelChapterGenerationTraceController.class
                .getAnnotation(RequestMapping.class)
                .value())
                .containsExactly("/api/v1/novels/generation-sessions");
        assertThat(NovelChapterGenerationTraceController.class
                .getAnnotation(Profile.class)
                .value())
                .containsExactly("dev");
        System.out.println("Prompt Trace 开发查询接口路径契约通过：GET /api/v1/novels/generation-sessions/{workflowId}/traces");
    }

    @Test
    void shouldReturnPromptAndResponseFieldsForWorkflowTraces() {
        PromptTraceRecord trace = PromptTraceRecord.now(
                "workflow-trace-query",
                "project-1",
                1,
                "REVIEW",
                0,
                "system prompt",
                "user prompt"
        ).completed(
                "{\"issues\":[]}",
                true,
                null,
                null,
                321L
        );
        IPromptTraceRepository repository = new IPromptTraceRepository() {
            @Override
            public void save(PromptTraceRecord value) {
            }

            @Override
            public List<PromptTraceRecord> findByWorkflowId(String workflowId) {
                return List.of(trace);
            }
        };

        Response<List<PromptTraceResponseDTO>> response =
                new NovelChapterGenerationTraceController(repository)
                        .traces("workflow-trace-query");

        assertThat(response.data()).singleElement().satisfies(item -> {
            assertThat(item.node()).isEqualTo("REVIEW");
            assertThat(item.attempt()).isZero();
            assertThat(item.success()).isTrue();
            assertThat(item.durationMs()).isEqualTo(321L);
            assertThat(item.errorMessage()).isNull();
            assertThat(item.systemPrompt()).isEqualTo("system prompt");
            assertThat(item.userPrompt()).isEqualTo("user prompt");
            assertThat(item.responseText()).isEqualTo("{\"issues\":[]}");
        });
        System.out.printf(
                "Prompt Trace 查询字段检查通过：workflowId=%s, traces=%d, rawPromptAndResponse=true%n",
                "workflow-trace-query",
                response.data().size()
        );
    }
}
