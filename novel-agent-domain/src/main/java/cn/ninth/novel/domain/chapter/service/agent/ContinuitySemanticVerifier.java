package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuitySemanticResult;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;

/** Phase B 连续性语义验证端口。 */
@FunctionalInterface
public interface ContinuitySemanticVerifier {

    ContinuitySemanticResult verify(ConflictCandidate candidate, ReviewContext reviewContext);

    /** 测试与离线骨架使用的保守验证器，不把候选直接升级为 HARD。 */
    static ContinuitySemanticVerifier noOp() {
        return (candidate, context) -> ContinuitySemanticResult.uncertain();
    }
}
