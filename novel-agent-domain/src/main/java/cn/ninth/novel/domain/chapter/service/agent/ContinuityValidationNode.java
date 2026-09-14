package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.ConflictCandidate;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.ContinuitySemanticResult;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySemanticDecision;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.springframework.beans.factory.annotation.Autowired;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 新 Pipeline 的连续性验证节点，按 Phase A 候选生成、Phase B 语义复核执行。 */
@Component
public class ContinuityValidationNode implements NodeAction<ChapterGraphState> {

    public static final String NODE = "CONTINUITY_VALIDATION";

    private final ConflictCandidateGenerator candidateGenerator;
    private final ContinuitySemanticVerifier semanticVerifier;

    /** 保留离线骨架入口：不调用模型，候选只会得到保守的 UNCERTAIN。 */
    public ContinuityValidationNode() {
        this(new ConflictCandidateGenerator(), ContinuitySemanticVerifier.noOp());
    }

    public ContinuityValidationNode(
            ConflictCandidateGenerator candidateGenerator,
            ContinuitySemanticVerifier semanticVerifier
    ) {
        this.candidateGenerator = candidateGenerator;
        this.semanticVerifier = semanticVerifier;
    }

    public ContinuityValidationNode(ContinuitySemanticVerifier semanticVerifier) {
        this(new ConflictCandidateGenerator(), semanticVerifier);
    }

    @Autowired
    public ContinuityValidationNode(
            ConflictCandidateGenerator candidateGenerator,
            ModelContinuitySemanticVerifier semanticVerifier
    ) {
        this(candidateGenerator, (ContinuitySemanticVerifier) semanticVerifier);
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        ReviewContext reviewContext = state.reviewContext().orElse(null);
        // 兼容尚未经过 PREPARE_REVIEW_CONTEXT 的离线骨架测试；生产路径不会走这里。
        if (reviewContext == null) {
            return Map.of(
                    ChapterGraphKeys.CONTINUITY_FINDINGS, state.continuityFindings(),
                    ChapterGraphKeys.CONFLICT_CANDIDATES, state.conflictCandidates(),
                    ChapterGraphKeys.CURRENT_NODE, NODE,
                    ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE)
            );
        }

        List<ConflictCandidate> candidates = candidateGenerator.generate(reviewContext);
        List<ContinuityFinding> findings = new ArrayList<>();
        for (ConflictCandidate candidate : candidates) {
            ContinuitySemanticResult result = semanticVerifier.verify(candidate, reviewContext);
            ContinuitySemanticDecision decision = result == null || result.decision() == null
                    ? ContinuitySemanticDecision.UNCERTAIN : result.decision();
            if (decision == ContinuitySemanticDecision.NOT_CONFLICT) {
                continue;
            }
            findings.add(toFinding(candidate, decision));
        }
        return Map.of(
                ChapterGraphKeys.CONFLICT_CANDIDATES, candidates,
                ChapterGraphKeys.CONTINUITY_FINDINGS, findings,
                ChapterGraphKeys.CURRENT_NODE, NODE,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE)
        );
    }

    private ContinuityFinding toFinding(
            ConflictCandidate candidate,
            ContinuitySemanticDecision decision
    ) {
        boolean hard = decision == ContinuitySemanticDecision.CONFLICT
                && candidate.canonicalHistoricalState();
        String reason = decision == ContinuitySemanticDecision.CONFLICT
                ? "语义复核确认当前证据明确破坏 canonical state"
                : "证据不足，暂不能确认当前证据是否破坏历史状态";
        return ContinuityFinding.builder()
                .type(candidate.type())
                .entity(candidate.entity())
                .severity(hard
                        ? cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity.HARD
                        : cn.ninth.novel.domain.chapter.model.valobj.enums.ContinuitySeverity.SOFT)
                .currentEvidence(candidate.currentEvidence())
                .historicalEvidence(candidate.historicalEvidence())
                .sourceChapter(candidate.sourceChapter())
                .reason(reason)
                .confidence(decision == ContinuitySemanticDecision.CONFLICT
                        ? (hard ? 0.95 : 0.75) : 0.5)
                .build();
    }
}
