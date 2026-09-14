package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateStatus;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryEvent;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryOperation;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P0.2 写入前的最小确定性 Reconcile。
 *
 * <p>只比较候选证据与明确相关的既有 Event/Fact，不执行向量检索、复杂合并、
 * 跨事实推理或 Canonical Commit。返回的结果仍然是 staging 决策。</p>
 */
public final class MemoryReconciler {

    private static final Pattern ENGLISH_STATE = Pattern.compile(
            "^(.+?)\\s+(located_at|holds|owns|alive|dead|is_alive|is_dead|related_to|relation)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_STATE = Pattern.compile(
            "^(.+?)(位于|持有|拥有|死亡|存活)(.+)$");

    /**
     * 根据候选、相关历史事件和相关事实生成一条 P0.2 决策。
     *
     * @param candidate 当前待 Reconcile 的来源绑定候选
     * @param relatedEvents 已按外部规则筛出的相关 Event
     * @param relatedFacts 已按外部规则筛出的相关 Fact
     */
    public MemoryOperationDecision reconcile(
            MemoryCandidate candidate,
            Collection<MemoryEvent> relatedEvents,
            Collection<MemoryFact> relatedFacts
    ) {
        Objects.requireNonNull(candidate, "candidate 不能为空");
        List<MemoryEvent> events = immutableNonNull(relatedEvents, "relatedEvents");
        List<MemoryFact> facts = immutableNonNull(relatedFacts, "relatedFacts");

        if (candidate.candidateStatus() == MemoryCandidateStatus.STALE
                || candidate.candidateStatus() == MemoryCandidateStatus.REJECTED) {
            return invalidateStaleCandidate(candidate, facts);
        }

        return switch (candidate.candidateType()) {
            case EVENT -> reconcileEvent(candidate, events);
            case FACT -> reconcileFact(candidate, facts);
            case FACT_INVALIDATION -> reconcileInvalidation(candidate, facts);
            case OPEN_LOOP_CHANGE -> decision(
                    MemoryOperation.ADD,
                    candidate,
                    "Open Loop 变更没有在本次输入中提供可替代的旧 Fact");
        };
    }

    public MemoryOperationDecision reconcile(
            MemoryCandidate candidate,
            Collection<MemoryFact> relatedFacts
    ) {
        return reconcile(candidate, List.of(), relatedFacts);
    }

    private MemoryOperationDecision reconcileEvent(
            MemoryCandidate candidate,
            List<MemoryEvent> relatedEvents
    ) {
        String statement = evidenceText(candidate);
        Optional<MemoryEvent> duplicate = relatedEvents.stream()
                .filter(Objects::nonNull)
                .filter(event -> sameEventEvidence(candidate, event, statement))
                .findFirst();
        if (duplicate.isPresent()) {
            return new MemoryOperationDecision(
                    MemoryOperation.NOOP,
                    candidate,
                    null,
                    duplicate.get(),
                    "同一章节版本和证据范围的 Event 已存在");
        }
        return decision(MemoryOperation.ADD, candidate, "没有同一历史发生记录");
    }

    private MemoryOperationDecision reconcileFact(
            MemoryCandidate candidate,
            List<MemoryFact> relatedFacts
    ) {
        String statement = evidenceText(candidate);
        List<MemoryFact> usableFacts = usableFacts(relatedFacts);

        Optional<MemoryFact> sameFact = usableFacts.stream()
                .filter(fact -> normalized(fact.proposition()).equals(normalized(statement)))
                .findFirst();
        if (sameFact.isPresent()) {
            MemoryFact fact = sameFact.get();
            if (sameEvidence(candidate, fact)) {
                return decision(
                        MemoryOperation.NOOP,
                        candidate,
                        fact,
                        "同一 Candidate 和同一 evidence 已被记录，没有新增语义或证据");
            }
            return decision(
                    MemoryOperation.REINFORCE,
                    candidate,
                    fact,
                    "新 evidence 支持已有同一 Fact");
        }

        Optional<MemoryFact> supersededFact = usableFacts.stream()
                .filter(fact -> sameStateSlot(statement, fact.proposition()))
                .filter(fact -> fact.status() == MemoryFactStatus.FACT_ACTIVE)
                .findFirst();
        if (supersededFact.isPresent()) {
            return decision(
                    MemoryOperation.SUPERSEDE,
                    candidate,
                    supersededFact.get(),
                    "同一状态槽位出现合法新状态，保留旧 Fact 并创建新 Fact");
        }

        return decision(MemoryOperation.ADD, candidate, "没有相关旧 Fact");
    }

    private MemoryOperationDecision reconcileInvalidation(
            MemoryCandidate candidate,
            List<MemoryFact> relatedFacts
    ) {
        List<MemoryFact> candidates = usableFacts(relatedFacts);
        if (candidates.size() != 1) {
            return decision(
                    MemoryOperation.NOOP,
                    candidate,
                    candidates.isEmpty()
                            ? "没有可失效的旧 Fact"
                            : "相关 Fact 不唯一，P0 不执行不确定的 INVALIDATE");
        }
        return decision(
                MemoryOperation.INVALIDATE,
                candidate,
                candidates.get(0),
                "候选明确声明旧 Fact 被证明错误、撤回或来源失效");
    }

    private MemoryOperationDecision invalidateStaleCandidate(
            MemoryCandidate candidate,
            List<MemoryFact> relatedFacts
    ) {
        Optional<MemoryFact> sourceFact = usableFacts(relatedFacts).stream()
                .filter(fact -> sameEvidence(candidate, fact))
                .findFirst();
        if (sourceFact.isEmpty()) {
            return decision(
                    MemoryOperation.NOOP,
                    candidate,
                    "候选来源已失效，但没有能按同一 evidence 定位的旧 Fact");
        }
        return decision(
                MemoryOperation.INVALIDATE,
                candidate,
                sourceFact.get(),
                "Candidate 来源版本已失效或被撤回，旧 Fact 退出有效视图");
    }

    private static boolean sameEventEvidence(
            MemoryCandidate candidate,
            MemoryEvent event,
            String statement
    ) {
        if (!candidate.chapterVersion().equals(event.chapterVersion())) {
            return false;
        }
        String expectedRange = candidate.evidenceRange().startOffset()
                + ":" + candidate.evidenceRange().endOffset();
        return expectedRange.equals(event.evidenceRange())
                && normalized(statement).equals(normalized(event.description()));
    }

    private static boolean sameEvidence(MemoryCandidate candidate, MemoryFact fact) {
        return fact.sourceIds().stream().anyMatch(source ->
                source.equals(candidate.candidateId())
                        || source.equals(evidenceBinding(candidate)));
    }

    private static String evidenceBinding(MemoryCandidate candidate) {
        return candidate.chapterVersion()
                + "#"
                + candidate.contentHash()
                + "#"
                + candidate.evidenceRange().startOffset()
                + ":"
                + candidate.evidenceRange().endOffset();
    }

    private static boolean sameStateSlot(String candidate, String existing) {
        StateSlot candidateSlot = stateSlot(candidate);
        StateSlot existingSlot = stateSlot(existing);
        return candidateSlot != null
                && existingSlot != null
                && candidateSlot.subject().equals(existingSlot.subject())
                && candidateSlot.predicate().equals(existingSlot.predicate())
                && !candidateSlot.object().equals(existingSlot.object());
    }

    private static StateSlot stateSlot(String proposition) {
        String value = normalized(proposition);
        Matcher english = ENGLISH_STATE.matcher(value);
        if (english.matches()) {
            return new StateSlot(
                    normalized(english.group(1)),
                    english.group(2).toLowerCase(Locale.ROOT),
                    normalized(english.group(3)));
        }
        Matcher chinese = CHINESE_STATE.matcher(value);
        if (chinese.matches()) {
            return new StateSlot(
                    normalized(chinese.group(1)),
                    chinese.group(2),
                    normalized(chinese.group(3)));
        }
        return null;
    }

    private static String evidenceText(MemoryCandidate candidate) {
        String excerpt = candidate.evidenceRange().excerpt();
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException(
                    "Candidate evidenceRange 必须包含 excerpt 才能执行 P0.2 Reconcile");
        }
        return normalized(excerpt);
    }

    private static List<MemoryFact> usableFacts(Collection<MemoryFact> facts) {
        return facts.stream()
                .filter(Objects::nonNull)
                .filter(fact -> fact.status() != MemoryFactStatus.FACT_INVALIDATED)
                .toList();
    }

    private static <T> List<T> immutableNonNull(Collection<T> values, String field) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " 不能包含 null");
        }
        return List.copyOf(values);
    }

    private static String normalized(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("[。.!！?？]+$", "").replaceAll("\\s+", " ");
    }

    private static MemoryOperationDecision decision(
            MemoryOperation operation,
            MemoryCandidate candidate,
            String reason
    ) {
        return new MemoryOperationDecision(operation, candidate, reason);
    }

    private static MemoryOperationDecision decision(
            MemoryOperation operation,
            MemoryCandidate candidate,
            MemoryFact relatedFact,
            String reason
    ) {
        return new MemoryOperationDecision(operation, candidate, relatedFact, reason);
    }

    private record StateSlot(String subject, String predicate, String object) {
    }
}
