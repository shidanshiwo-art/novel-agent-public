package cn.ninth.novel.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 已确认线程的 staging 摘要。
 *
 * <p>result 和 directImpact 由上游已确认输入提供，本对象只保存它们及来源，
 * 不执行多事实推理。</p>
 */
public record MemoryConsolidationResult(
        String result,
        String directImpact,
        List<String> sourceEvidence
) {

    public MemoryConsolidationResult {
        result = required(result, "result");
        directImpact = required(directImpact, "directImpact");
        sourceEvidence = immutableSources(sourceEvidence);
    }

    public String getResult() {
        return result;
    }

    public String getDirectImpact() {
        return directImpact;
    }

    public List<String> getSourceEvidence() {
        return sourceEvidence;
    }

    /** Projection 重建使用的确定性内容，不添加任何推导文本。 */
    public String projectionContent() {
        return "result: " + result + System.lineSeparator()
                + "directImpact: " + directImpact;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static List<String> immutableSources(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("sourceEvidence 至少需要一个来源");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(required(value, "sourceEvidence"));
        }
        return Collections.unmodifiableList(List.copyOf(normalized));
    }
}
