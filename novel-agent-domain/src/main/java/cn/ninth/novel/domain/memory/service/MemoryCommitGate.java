package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.adapter.repository.MemoryCommitRepository;
import cn.ninth.novel.domain.memory.model.CanonicalEvent;
import cn.ninth.novel.domain.memory.model.CanonicalFact;
import cn.ninth.novel.domain.memory.model.CanonicalProjection;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryCommitPlan;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemoryCommitResult;
import cn.ninth.novel.domain.memory.model.MemoryConflict;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryFactStatusChange;
import cn.ninth.novel.domain.memory.model.MemoryEvidenceRef;
import cn.ninth.novel.domain.memory.model.MemoryOperation;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;
import cn.ninth.novel.domain.memory.model.MemoryOutboxEntry;
import cn.ninth.novel.domain.memory.model.MemoryProjectionStatus;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cn.ninth.novel.domain.memoryservice.MemoryCommitRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * P0.5 Canonical Commit 的唯一领域入口。
 *
 * <p>该类只负责把最终正文、版本绑定、Evidence、Reconcile 结果和校验结果
 * 组装成不可旁路的提交计划；实际写入由事务仓储完成。任何 BLOCKING 结果都
 * 在调用仓储前拒绝，因此不会污染正式 Recall。</p>
 */
@Service
public final class MemoryCommitGate {

    public static final String MISSING_EVIDENCE = "MISSING_EVIDENCE";
    public static final String VERSION_MISMATCH = "VERSION_MISMATCH";
    public static final String STALE_CANDIDATE = "STALE_CANDIDATE";
    public static final String CANDIDATE_NOT_READY = "CANDIDATE_NOT_READY";
    public static final String EVIDENCE_OUT_OF_RANGE = "EVIDENCE_OUT_OF_RANGE";
    public static final String UNSUPPORTED_CANDIDATE_TYPE = "UNSUPPORTED_CANDIDATE_TYPE";
    public static final String MISSING_RECONCILIATION = "MISSING_RECONCILIATION";
    public static final String INVALID_RECONCILIATION = "INVALID_RECONCILIATION";
    public static final String MISSING_RELATED_MEMORY = "MISSING_RELATED_MEMORY";
    public static final String TIME_IMPOSSIBLE = "TIME_IMPOSSIBLE";
    public static final String EXCLUSIVE_POSITION_CONFLICT = "EXCLUSIVE_POSITION_CONFLICT";
    public static final String UNEXPLAINED_LIFE_STATUS_CHANGE = "UNEXPLAINED_LIFE_STATUS_CHANGE";
    public static final String WORLD_RULE_VIOLATION = "WORLD_RULE_VIOLATION";
    public static final String MISSING_KNOWLEDGE_SOURCE = "MISSING_KNOWLEDGE_SOURCE";
    public static final String ENTITY_MATCH_UNCERTAIN = "ENTITY_MATCH_UNCERTAIN";
    public static final String TIME_AMBIGUOUS = "TIME_AMBIGUOUS";
    public static final String LOW_RISK_SEMANTIC_DIFFERENCE = "LOW_RISK_SEMANTIC_DIFFERENCE";

    private final MemoryCommitRepository commitRepository;
    private final MemorySemanticValidator semanticValidator;
    private final MemoryReconcileMetricsCollector metricsCollector;
    private final MemoryReconciler reconciler = new MemoryReconciler();

    /** 保留领域单元测试及非 Spring 调用方的最小构造入口。 */
    public MemoryCommitGate(MemoryCommitRepository commitRepository) {
        this(commitRepository, MemorySemanticValidator.noOp(),
                new MemoryReconcileMetricsCollector());
    }

    @Autowired
    public MemoryCommitGate(
            MemoryCommitRepository commitRepository,
            MemoryReconcileMetricsCollector metricsCollector
    ) {
        this(commitRepository, MemorySemanticValidator.noOp(), metricsCollector);
    }

    public MemoryCommitGate(
            MemoryCommitRepository commitRepository,
            MemorySemanticValidator semanticValidator
    ) {
        this(commitRepository, semanticValidator, new MemoryReconcileMetricsCollector());
    }

    public MemoryCommitGate(
            MemoryCommitRepository commitRepository,
            MemorySemanticValidator semanticValidator,
            MemoryReconcileMetricsCollector metricsCollector
    ) {
        this.commitRepository = Objects.requireNonNull(
                commitRepository, "commitRepository 不能为空");
        this.semanticValidator = Objects.requireNonNull(
                semanticValidator, "semanticValidator 不能为空");
        this.metricsCollector = Objects.requireNonNull(
                metricsCollector, "metricsCollector 不能为空");
    }

    /**
     * 执行完整 Gate 并提交。accept-current 也必须从此入口进入。
     */
    public MemoryCommitResult commit(MemoryCommitRequest request) {
        MemoryCommitPlan plan = prepare(request);
        MemoryCommitResult result = commitRepository.commit(plan);
        if (!result.alreadyCommitted()) {
            metricsCollector.record(request.projectCode(), request.chapterNumber(), request.decisions());
        }
        return result;
    }

    /**
     * 暴露校验后的计划，便于上层在真正事务提交前观察 Gate 结果；不执行写入。
     */
    public MemoryCommitPlan prepare(MemoryCommitRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<MemoryConflict> conflicts = new ArrayList<>(request.conflicts());
        MemorySourceVersion finalVersion = request.finalSourceVersion();
        if (!finalVersion.matchesContent(request.finalContent())) {
            conflicts.add(MemoryConflict.blocking(
                    VERSION_MISMATCH, "finalSourceVersion 与最终正文 contentHash 不匹配"));
        }

        if (request.candidates().isEmpty()) {
            conflicts.add(MemoryConflict.blocking(
                    MISSING_EVIDENCE, "没有可绑定最终正文的 Candidate evidence"));
        }

        Map<String, MemoryCandidate> validCandidates = new HashMap<>();
        for (MemoryCandidate candidate : request.candidates()) {
            validateCandidate(candidate, finalVersion, request.finalContent(), conflicts);
            if (candidate.candidateStatus() == MemoryCandidateStatus.READY_FOR_GATE
                    && candidate.sourceVersion().equals(finalVersion)
                    && candidate.evidenceRange().isWithin(request.finalContent())) {
                validCandidates.put(candidate.candidateId(), candidate);
            }
        }

        List<MemoryOperationDecision> decisions = effectiveDecisions(request, conflicts);
        validateDecisionCoverage(request.candidates(), decisions, conflicts);

        List<CanonicalEvent> events = new ArrayList<>();
        List<CanonicalFact> facts = new ArrayList<>();
        List<MemoryFactStatusChange> statusChanges = new ArrayList<>();
        List<CanonicalProjection> projections = new ArrayList<>(request.projections());
        List<MemoryOutboxEntry> outbox = new ArrayList<>();
        for (MemoryOperationDecision decision : decisions) {
            MemoryCandidate candidate = validCandidates.get(decision.candidate().candidateId());
            if (candidate == null) {
                continue;
            }
            materializeDecision(
                    request, decision, candidate, events, facts, statusChanges,
                    projections, outbox, conflicts);
        }

        for (CanonicalProjection projection : request.projections()) {
            outbox.add(outbox(
                    request, "PROJECTION", projection.projectionId(), projection.content()));
        }

        List<MemoryConflict> semanticConflicts = semanticValidator.validate(request);
        if (semanticConflicts != null) {
            if (semanticConflicts.stream().anyMatch(Objects::isNull)) {
                conflicts.add(MemoryConflict.blocking(
                        "SEMANTIC_VALIDATION_ERROR", "语义校验返回了 null 冲突"));
            } else {
                conflicts.addAll(semanticConflicts);
            }
        }

        List<MemoryConflict> blocking = conflicts.stream()
                .filter(MemoryConflict::isBlocking)
                .toList();
        if (!blocking.isEmpty()) {
            throw new MemoryCommitRejectedException(blocking);
        }

        List<MemoryConflict> nonBlocking = conflicts.stream()
                .filter(conflict -> !conflict.isBlocking())
                .toList();
        return new MemoryCommitPlan(
                request.projectCode(),
                request.chapterNumber(),
                request.finalContent(),
                request.finalSourceVersion(),
                request.commitKey(),
                events,
                facts,
                statusChanges,
                projections,
                outbox,
                nonBlocking
        );
    }

    private void validateCandidate(
            MemoryCandidate candidate,
            MemorySourceVersion finalVersion,
            String finalContent,
            List<MemoryConflict> conflicts
    ) {
        if (candidate == null) {
            conflicts.add(MemoryConflict.blocking(
                    MISSING_EVIDENCE, "Candidate 不能为空"));
            return;
        }
        if (candidate.candidateStatus() != MemoryCandidateStatus.READY_FOR_GATE) {
            String code = candidate.candidateStatus() == MemoryCandidateStatus.STALE
                    ? STALE_CANDIDATE
                    : CANDIDATE_NOT_READY;
            conflicts.add(MemoryConflict.blocking(
                    code, "Candidate 必须处于 READY_FOR_GATE 才能提交"));
        }
        if (!candidate.sourceVersion().equals(finalVersion)
                || !finalVersion.matchesContent(finalContent)) {
            conflicts.add(MemoryConflict.blocking(
                    VERSION_MISMATCH, "Candidate 未绑定最终正文版本"));
            return;
        }
        try {
            MemoryCandidate revalidated = candidate.revalidate(finalVersion, finalContent);
            if (revalidated.candidateStatus() == MemoryCandidateStatus.STALE) {
                conflicts.add(MemoryConflict.blocking(
                        STALE_CANDIDATE, "Candidate 在最终正文版本校验后已失效"));
            }
            candidate.evidenceRange().resolve(finalContent);
        } catch (IllegalArgumentException exception) {
            conflicts.add(MemoryConflict.blocking(
                    EVIDENCE_OUT_OF_RANGE, exception.getMessage()));
        }
    }

    private List<MemoryOperationDecision> effectiveDecisions(
            MemoryCommitRequest request,
            List<MemoryConflict> conflicts
    ) {
        if (!request.decisions().isEmpty()) {
            return request.decisions();
        }
        List<MemoryOperationDecision> defaults = new ArrayList<>();
        for (MemoryCandidate candidate : request.candidates()) {
            try {
                MemoryCandidate grounded = groundedCandidate(candidate, request.finalContent());
                // 没有显式相关历史时仍经过最小 Reconcile；有相关历史时必须由调用方
                // 传入 decisions，Gate 不会自行猜测旧 Canonical。
                defaults.add(reconciler.reconcile(grounded, List.of(), List.of()));
            } catch (IllegalArgumentException exception) {
                conflicts.add(MemoryConflict.blocking(
                        MISSING_RECONCILIATION, exception.getMessage()));
            }
        }
        return defaults;
    }

    private MemoryCandidate groundedCandidate(MemoryCandidate candidate, String content) {
        if (candidate.evidenceRange().excerpt() != null) {
            return candidate;
        }
        String excerpt = candidate.evidenceRange().resolve(content);
        return new MemoryCandidate(
                candidate.candidateId(),
                candidate.sourceVersion(),
                new MemoryEvidenceRef(
                        candidate.evidenceRange().startOffset(),
                        candidate.evidenceRange().endOffset(),
                        excerpt),
                candidate.candidateType(),
                candidate.candidateStatus());
    }

    private void validateDecisionCoverage(
            List<MemoryCandidate> candidates,
            List<MemoryOperationDecision> decisions,
            List<MemoryConflict> conflicts
    ) {
        Set<String> candidateIds = candidates.stream()
                .filter(Objects::nonNull)
                .map(MemoryCandidate::candidateId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (MemoryOperationDecision decision : decisions) {
            if (decision == null || decision.candidate() == null) {
                conflicts.add(MemoryConflict.blocking(
                        INVALID_RECONCILIATION, "Reconcile decision 缺少 Candidate"));
                continue;
            }
            String candidateId = decision.candidate().candidateId();
            if (!candidateIds.contains(candidateId)) {
                conflicts.add(MemoryConflict.blocking(
                        INVALID_RECONCILIATION, "Reconcile decision 使用了未提交的 Candidate"));
            }
            if (!seen.add(candidateId)) {
                conflicts.add(MemoryConflict.blocking(
                        INVALID_RECONCILIATION, "同一个 Candidate 不能重复执行 Reconcile 操作"));
            }
        }
        for (String candidateId : candidateIds) {
            if (!seen.contains(candidateId)) {
                conflicts.add(MemoryConflict.blocking(
                        MISSING_RECONCILIATION, "每个 Candidate 都必须有明确 Reconcile 结果"));
            }
        }
    }

    private void materializeDecision(
            MemoryCommitRequest request,
            MemoryOperationDecision decision,
            MemoryCandidate candidate,
            List<CanonicalEvent> events,
            List<CanonicalFact> facts,
            List<MemoryFactStatusChange> statusChanges,
            List<CanonicalProjection> projections,
            List<MemoryOutboxEntry> outbox,
            List<MemoryConflict> conflicts
    ) {
        MemoryOperation operation = decision.operation();
        MemoryCandidateType type = candidate.candidateType();
        if (!operationAllowed(type, operation)) {
            conflicts.add(MemoryConflict.blocking(
                    INVALID_RECONCILIATION,
                    "Candidate 类型与 Reconcile operation 不匹配: " + type + "/" + operation));
            return;
        }

        switch (operation) {
            case ADD -> {
                if (type == MemoryCandidateType.EVENT) {
                    CanonicalEvent event = toEvent(request, candidate);
                    events.add(event);
                    outbox.add(outbox(request, "EVENT", event.eventId(), event.evidenceBinding()));
                } else if (type == MemoryCandidateType.FACT) {
                    CanonicalFact fact = toFact(request, candidate, MemoryFactStatus.FACT_ACTIVE);
                    facts.add(fact);
                    outbox.add(outbox(request, "FACT", fact.factId(), fact.proposition()));
                } else if (type == MemoryCandidateType.OPEN_LOOP_CHANGE) {
                    CanonicalProjection projection = toOpenLoopProjection(request, candidate);
                    projections.add(projection);
                    outbox.add(outbox(
                            request, "PROJECTION", projection.projectionId(), projection.content()));
                } else {
                    conflicts.add(MemoryConflict.blocking(
                            UNSUPPORTED_CANDIDATE_TYPE,
                            "不支持的 Candidate 类型"));
                }
            }
            case REINFORCE -> {
                MemoryFact relatedFact = requireRelatedFact(decision, conflicts);
                if (relatedFact != null) {
                    List<String> sources = new ArrayList<>(relatedFact.sourceIds());
                    addEvidenceSources(sources, candidate);
                    CanonicalFact fact = new CanonicalFact(
                            relatedFact.factId(), request.projectCode(),
                            relatedFact.proposition(), sources, relatedFact.status(),
                            candidate.candidateId());
                    facts.add(fact);
                    outbox.add(outbox(request, "FACT", fact.factId(), fact.proposition()));
                }
            }
            case SUPERSEDE -> {
                MemoryFact relatedFact = requireRelatedFact(decision, conflicts);
                if (relatedFact != null) {
                    if (relatedFact.status() != MemoryFactStatus.FACT_ACTIVE) {
                        conflicts.add(MemoryConflict.blocking(
                                INVALID_RECONCILIATION,
                                "只有 FACT_ACTIVE 才能被 SUPERSEDE"));
                        return;
                    }
                    statusChanges.add(new MemoryFactStatusChange(
                            relatedFact.factId(), MemoryFactStatus.FACT_ARCHIVED, decision.reason()));
                    CanonicalFact fact = toFact(request, candidate, MemoryFactStatus.FACT_ACTIVE);
                    facts.add(fact);
                    outbox.add(outbox(request, "FACT", relatedFact.factId(), "FACT_ARCHIVED"));
                    outbox.add(outbox(request, "FACT", fact.factId(), fact.proposition()));
                }
            }
            case INVALIDATE -> {
                MemoryFact relatedFact = requireRelatedFact(decision, conflicts);
                if (relatedFact != null) {
                    if (relatedFact.status() != MemoryFactStatus.FACT_INVALIDATED) {
                        statusChanges.add(new MemoryFactStatusChange(
                                relatedFact.factId(), MemoryFactStatus.FACT_INVALIDATED,
                                decision.reason()));
                        outbox.add(outbox(
                                request, "FACT", relatedFact.factId(), "FACT_INVALIDATED"));
                    }
                }
            }
            case NOOP -> {
                // 已存在且没有新增来源时不产生 Canonical 或 outbox 写入。
            }
        }
    }

    private boolean operationAllowed(MemoryCandidateType type, MemoryOperation operation) {
        return switch (type) {
            case EVENT -> operation == MemoryOperation.ADD || operation == MemoryOperation.NOOP;
            case FACT -> operation == MemoryOperation.ADD
                    || operation == MemoryOperation.REINFORCE
                    || operation == MemoryOperation.SUPERSEDE
                    || operation == MemoryOperation.NOOP;
            case FACT_INVALIDATION -> operation == MemoryOperation.INVALIDATE
                    || operation == MemoryOperation.NOOP;
            case OPEN_LOOP_CHANGE -> operation == MemoryOperation.ADD
                    || operation == MemoryOperation.NOOP;
        };
    }

    private CanonicalEvent toEvent(MemoryCommitRequest request, MemoryCandidate candidate) {
        String evidence = resolveEvidence(candidate.evidenceRange(), request.finalContent());
        return new CanonicalEvent(
                candidate.candidateId(),
                request.projectCode(),
                request.chapterNumber(),
                evidence,
                candidate.sourceVersion(),
                new MemoryEvidenceRef(
                        candidate.evidenceRange().startOffset(),
                        candidate.evidenceRange().endOffset(),
                        evidence),
                null,
                candidate.candidateId());
    }

    private CanonicalFact toFact(
            MemoryCommitRequest request,
            MemoryCandidate candidate,
            MemoryFactStatus status
    ) {
        return new CanonicalFact(
                candidate.candidateId(),
                request.projectCode(),
                resolveEvidence(candidate.evidenceRange(), request.finalContent()),
                evidenceSources(candidate),
                status,
                candidate.candidateId());
    }

    private CanonicalProjection toOpenLoopProjection(
            MemoryCommitRequest request,
            MemoryCandidate candidate
    ) {
        String evidence = resolveEvidence(candidate.evidenceRange(), request.finalContent());
        String projectionId = "runtime:open-loop:"
                + MemorySourceVersion.hash(request.projectCode() + ":"
                + request.chapterNumber() + ":" + candidate.candidateId());
        return new CanonicalProjection(
                projectionId,
                request.projectCode(),
                "开放线索（正文尚未解决）：" + evidence,
                List.of(candidate.candidateId(), evidenceBinding(candidate)),
                MemoryProjectionStatus.PROJECTION_ACTIVE);
    }

    private List<String> evidenceSources(MemoryCandidate candidate) {
        return List.of(candidate.candidateId(), evidenceBinding(candidate));
    }

    private void addEvidenceSources(List<String> sources, MemoryCandidate candidate) {
        for (String source : evidenceSources(candidate)) {
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
    }

    private MemoryFact requireRelatedFact(
            MemoryOperationDecision decision,
            List<MemoryConflict> conflicts
    ) {
        if (decision.relatedFact() == null) {
            conflicts.add(MemoryConflict.blocking(
                    MISSING_RELATED_MEMORY, "该 Reconcile operation 缺少相关旧 Fact"));
        }
        return decision.relatedFact();
    }

    private MemoryOutboxEntry outbox(
            MemoryCommitRequest request,
            String aggregateType,
            String aggregateId,
            String payload
    ) {
        String identity = request.commitKey() + ":" + aggregateType + ":" + aggregateId;
        return new MemoryOutboxEntry(
                MemorySourceVersion.hash(identity),
                request.commitKey(), aggregateType, aggregateId, payload);
    }

    private String resolveEvidence(MemoryEvidenceRef evidence, String content) {
        return evidence.resolve(content);
    }

    private String evidenceBinding(MemoryCandidate candidate) {
        return candidate.sourceVersion().chapterVersion()
                + "#" + candidate.sourceVersion().contentHash()
                + "#" + candidate.evidenceRange().startOffset()
                + ":" + candidate.evidenceRange().endOffset();
    }
}
