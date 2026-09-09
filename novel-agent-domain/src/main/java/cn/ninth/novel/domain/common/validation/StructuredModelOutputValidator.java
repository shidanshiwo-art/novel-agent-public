package cn.ninth.novel.domain.common.validation;

import cn.ninth.novel.domain.chapter.model.valobj.*;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import cn.ninth.novel.domain.chapter.service.agent.ChapterMemoryResponse;
import cn.ninth.novel.domain.planning.model.valobj.*;
import cn.ninth.novel.domain.project.model.valobj.*;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import java.util.List;

/** 模型结构化结果的业务字段校验，不修补或补全模型内容。 */
public final class StructuredModelOutputValidator {
    private StructuredModelOutputValidator() { }

    public static void validate(Object value) {
        try {
            validateFields(value);
        } catch (AppException exception) {
            String code = value instanceof ReviewReportVO ? ResponseCode.E0004.getCode()
                    : value instanceof ChapterMemoryResponse || value instanceof ChapterMemoryVO
                    ? ResponseCode.E0006.getCode() : ResponseCode.E0007.getCode();
            throw AppException.internal(code, exception.getInternalDetail(), exception);
        }
    }

    private static void validateFields(Object value) {
        require(value != null, "结构化结果不能为空");
        if (value instanceof RootOutlineDraftVO v) {
            text(v.summary(), "summary");
        } else if (value instanceof ChapterPlanDraftVO v) {
            titleSummary(v.title(), v.summary());
        } else if (value instanceof ChildOutlineDraftVO v) {
            titleSummary(v.title(), v.summary());
        } else if (value instanceof CharacterDraftListVO v) {
            require(v.characters() != null && !v.characters().isEmpty(), "characters 不能为空");
            v.characters().forEach(StructuredModelOutputValidator::validate);
        } else if (value instanceof CharacterDraftVO v) {
            text(v.name(), "name");
            max(v.name(), 100, "name");
            max(v.ageDescription(), 100, "ageDescription");
        } else if (value instanceof StoryBibleDraftVO v) {
            text(v.oneSentencePremise(), "oneSentencePremise");
            max(v.oneSentencePremise(), 1000, "oneSentencePremise");
        } else if (value instanceof ReviewReportVO v) {
            require(v.getReviewIssueVOList() != null, "issues 必须为数组");
            for (ReviewIssueVO issue : v.getReviewIssueVOList()) {
                require(issue != null, "issues 条目不能为空");
                require(issue.getSeverity() != null && issue.getSeverity() != SeverityEnum.UNKNOWN,
                        "severity 必须是有效的问题等级");
                text(issue.getCategory(), "category");
                text(issue.getDescription(), "description");
                text(issue.getEvidence(), "evidence");
            }
        } else if (value instanceof ChapterMemoryResponse v) {
            memory(v.shortSummary(), v.keyEvents(), v.unresolved(), v.endingHook());
            if (v.state() != null) {
                textList(v.state().resources(), "state.resources");
                textList(v.state().abilities(), "state.abilities");
                textList(v.state().knowledge(), "state.knowledge");
                textList(v.state().presence(), "state.presence");
            }
        } else if (value instanceof ChapterMemoryVO v) {
            memory(v.getShortSummary(), v.getKeyEvents(), v.getUnresolved(), v.getEndingHook());
        }
    }

    private static void titleSummary(String title, String summary) {
        text(title, "title");
        max(title, 300, "title");
        text(summary, "summary");
    }

    private static void memory(String summary, List<String> events, List<String> unresolved, String hook) {
        text(summary, "shortSummary");
        max(summary, 2000, "shortSummary");
        text(hook, "endingHook");
        textList(events, "keyEvents");
        textList(unresolved, "unresolved");
    }

    private static void textList(List<String> values, String field) {
        require(values != null, field + " 必须为数组");
        values.forEach(value -> text(value, field + " 条目"));
    }

    private static void text(String value, String field) {
        require(value != null && !value.isBlank(), field + " 不能为空");
    }

    private static void max(String value, int limit, String field) {
        require(value == null || value.codePointCount(0, value.length()) <= limit,
                field + " 长度不能超过 " + limit);
    }

    private static void require(boolean valid, String detail) {
        if (!valid) throw AppException.internal(ResponseCode.E0007.getCode(),
                "SCHEMA_VALIDATION_FAILED: " + detail);
    }
}
