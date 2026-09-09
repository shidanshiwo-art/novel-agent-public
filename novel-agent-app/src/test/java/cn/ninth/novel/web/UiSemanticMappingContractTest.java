package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UiSemanticMappingContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("<template[^>]*>(.*?)</template>", Pattern.DOTALL);
    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
    private static final Pattern DIRECT_FIELD_PATTERN = Pattern.compile("\\b(?:[A-Za-z_$][\\w$]*\\.)?(status|nodeKind|workflowStatus|lifeStatus)\\b");
    private static final Pattern RAW_INTERNAL_LABEL_PATTERN = Pattern.compile(
            ">\\s*(READY|COMPLETED|PLANNED|UNPLANNED|DRAFTING|REVIEWING|REVISING|WAITING_HUMAN|REVIEW_FAILED|BOOK|VOLUME|ARC|ChapterPlan|ChapterMemory|COMPRESSION|PERSIST)\\s*<"
    );
    private static final List<String> UI_MAPPERS = List.of(
            "statusLabel(",
            "statusDescription(",
            "statusTone(",
            "volumePlanStatusLabel(",
            "chapterPlanStatusLabel",
            "chapterPlanHistoryStatus(",
            "lifeStatusLabel(",
            "outlineTreeLabel(",
            "outlineKindLabel(",
            "nextChildKindLabel("
    );

    @Test
    void shouldDefineProductLanguageForStatusesAndDomainNames() throws IOException {
        String semantics = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/utils/uiSemantics.ts"));

        System.out.printf("前端语义映射检查：source=%d%n", semantics.length());

        assertThat(semantics)
                .contains(
                        "export function statusLabel",
                        "export type StatusCategory = 'pending' | 'progress' | 'completed' | 'error'",
                        "export function statusTone",
                        "export function statusCategory",
                        "export function statusDescription",
                        "export function userMessage",
                        "READY: '可生成'",
                        "COMPLETED: '已完成'",
                        "UNPLANNED: '待规划'",
                        "PLANNED: '已规划'",
                        "REVIEWING: '正在检查'",
                        "WAITING_HUMAN: '等待确认'",
                        "REVIEW_FAILED: '检查未完成'",
                        "ACTIVE: '进行中'",
                        "BOOK: '故事总纲'",
                        "VOLUME: '卷'",
                        "ARC: '章纲'",
                        "ChapterPlan: '章节计划'",
                        "ChapterMemory: '章节记忆'",
                        "REVIEW: '审稿'",
                        "REVISION: '修改'",
                        "COMPRESSION: '更新章节记忆'",
                        "PERSIST: '保存章节'"
                )
                .doesNotContain("return STATUS_LABELS[semanticKey(status)] ?? status");

        System.out.println("前端语义映射通过：未知状态也不会直接回显后端枚举");
    }

    @Test
    void shouldKeepInternalNamesOutOfVisiblePageCopy() throws IOException {
        String generate = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/GenerateView.vue"));
        String read = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/ReadView.vue"));
        String setup = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/SetupView.vue"));
        String outline = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/views/OutlineView.vue"));
        String directory = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/components/workbench/ChapterDirectory.vue"));

        System.out.printf("页面可见文案检查：generate=%d, read=%d, setup=%d, outline=%d%n",
                generate.length(), read.length(), setup.length(), outline.length());

        assertThat(generate)
                .contains("{{ statusLabel(workflowStatus) }}")
                .doesNotContain("<h2>ChapterPlan</h2>", "Draft Preview", "状态已更新为 READY", "ARC 重生成 Draft payload");
        assertThat(read)
                .contains("statusLabel(selectedChapter.status)")
                .doesNotContain("<span class=\"node-prefix\">书 ·</span>");
        assertThat(directory)
                .contains("<span class=\"node-prefix\">故事总纲 ·</span>")
                .doesNotContain(">BOOK<", ">VOLUME<", ">ARC<");
        assertThat(setup)
                .contains("{{ statusLabel('DRAFT') }}")
                .doesNotContain(">DRAFT<");
        assertThat(outline)
                .contains("domainLabel('BOOK')", "草稿预览", "章纲调整")
                .doesNotContain("DRAFT PREVIEW", "ARC REGENERATION", "Draft payload");

        System.out.println("页面可见文案通过：状态、Drawer、错误提示和节点类型均使用产品语言");
    }

    @Test
    void shouldKeepBackendNamesOutOfVisibleTemplateExpressions() throws IOException {
        Path sourceRoot = PROJECT_ROOT.resolve("novel-agent-web/src");
        int vueFileCount = 0;
        int interpolationCount = 0;

        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".vue")).toList()) {
                vueFileCount++;
                String source = Files.readString(path);
                Matcher templateMatcher = TEMPLATE_PATTERN.matcher(source);
                if (!templateMatcher.find()) continue;

                String template = templateMatcher.group(1);
                Matcher interpolationMatcher = INTERPOLATION_PATTERN.matcher(template);
                while (interpolationMatcher.find()) {
                    interpolationCount++;
                    String expression = interpolationMatcher.group(1);
                    Matcher directFieldMatcher = DIRECT_FIELD_PATTERN.matcher(expression);
                    if (directFieldMatcher.find()) {
                        assertThat(UI_MAPPERS.stream().anyMatch(expression::contains))
                                .as("模板不能直接展示内部字段：%s -> %s", path, expression.trim())
                                .isTrue();
                    }
                }

                assertThat(RAW_INTERNAL_LABEL_PATTERN.matcher(template).find())
                        .as("模板中不能直接显示后端枚举或领域名：%s", path)
                        .isFalse();
            }
        }

        String http = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/api/http.ts"));
        assertThat(http)
                .contains("import { userMessage } from '../utils/uiSemantics'")
                .contains("? userMessage(value)");

        System.out.printf("前端后端语义扫描通过：Vue 文件=%d，模板插值=%d，错误消息经 userMessage 转换%n",
                vueFileCount, interpolationCount);
    }
}
