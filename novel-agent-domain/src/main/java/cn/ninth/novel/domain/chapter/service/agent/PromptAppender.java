package cn.ninth.novel.domain.chapter.service.agent;

import java.util.List;

/**
 * 领域 Agent prompt 的基础文本渲染工具。
 */
final class PromptAppender {

    private PromptAppender() {
    }

    static void appendLine(StringBuilder prompt, String label, Object content) {
        prompt.append(label)
                .append("：")
                .append(value(content))
                .append('\n');
    }

    static void appendLineIfPresent(StringBuilder prompt, String label, Object content) {
        if (content == null || content.toString().isBlank()) {
            return;
        }
        appendLine(prompt, label, content);
    }

    static void appendIndentedLine(StringBuilder prompt, String label, Object content) {
        prompt.append("  ")
                .append(label)
                .append("：")
                .append(value(content))
                .append('\n');
    }

    static void appendList(StringBuilder prompt, String label, List<String> values) {
        prompt.append(label).append("：\n");
        if (values == null || values.isEmpty()) {
            prompt.append("- 无\n");
            return;
        }

        boolean appended = false;
        for (String item : values) {
            if (item != null && !item.isBlank()) {
                prompt.append("- ").append(item.trim()).append('\n');
                appended = true;
            }
        }
        if (!appended) {
            prompt.append("- 无\n");
        }
    }

    static String value(Object content) {
        if (content == null) {
            return "无";
        }

        String text = content.toString().trim();
        return text.isEmpty() ? "无" : text;
    }
}
