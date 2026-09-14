package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySemanticDecision;

import java.util.Objects;

/**
 * Phase B 模型输出协议。
 * 模型只能返回一个 decision，不允许在此阶段额外发现或描述其他问题。
 */
public record ContinuitySemanticResult(ContinuitySemanticDecision decision) {

    public ContinuitySemanticResult {
        decision = Objects.requireNonNull(decision, "decision 不能为空");
    }

    public static ContinuitySemanticResult conflict() {
        return new ContinuitySemanticResult(ContinuitySemanticDecision.CONFLICT);
    }

    public static ContinuitySemanticResult notConflict() {
        return new ContinuitySemanticResult(ContinuitySemanticDecision.NOT_CONFLICT);
    }

    public static ContinuitySemanticResult uncertain() {
        return new ContinuitySemanticResult(ContinuitySemanticDecision.UNCERTAIN);
    }
}
