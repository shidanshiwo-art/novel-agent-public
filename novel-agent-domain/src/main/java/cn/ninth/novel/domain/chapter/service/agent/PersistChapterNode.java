package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemoryEvent;
import cn.ninth.novel.domain.memory.model.MemoryFact;
import cn.ninth.novel.domain.memory.model.MemoryFactStatus;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryOperationDecision;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.domain.memory.service.FinalChapterCandidateExtractor;
import cn.ninth.novel.domain.memory.service.MemoryReconciler;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PERSIST 章节生成结果持久化节点。
 */
@Component
public class PersistChapterNode implements NodeAction<ChapterGraphState> {

    private static final String NODE_NAME = "PERSIST";

    private final IChapterPersistRepository chapterPersistRepository;
    private final FinalChapterCandidateExtractor candidateExtractor;
    private final MemoryReconciler reconciler = new MemoryReconciler();

    public PersistChapterNode(IChapterPersistRepository chapterPersistRepository) {
        this(chapterPersistRepository, new FinalChapterCandidateExtractor());
    }

    @Autowired
    public PersistChapterNode(
            IChapterPersistRepository chapterPersistRepository,
            FinalChapterCandidateExtractor candidateExtractor
    ) {
        this.chapterPersistRepository = chapterPersistRepository;
        this.candidateExtractor = candidateExtractor;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        String projectCode = state.projectCode()
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 projectCode"));
        int chapterNumber = state.chapterNumber()
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 chapterNumber"));
        String content = state.draft()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 draft"));
        ChapterMemoryVO chapterMemory = state.chapterMemory()
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 chapterMemory"));
        StoryStateSnapshot storyStateSnapshot = state.storyStateSnapshot()
                .orElseGet(StoryStateSnapshot::empty);

        if (state.memoryMode() == MemoryMode.LEGACY
                || !state.generationVariant().continuousCanonicalCommitEnabled()) {
            // V1-current 用于对照：保留 V1 Recall，但不启用本次持续 Canonical Commit。
            chapterPersistRepository.persist(
                    projectCode, chapterNumber, content, chapterMemory, storyStateSnapshot);
            return Map.of(
                    ChapterGraphKeys.CURRENT_NODE, NODE_NAME,
                    ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE_NAME)
            );
        }

        MemorySourceVersion sourceVersion = state.sourceVersion()
                .orElseGet(() -> MemorySourceVersion.create(
                        "chapter:" + projectCode + ":" + chapterNumber, content));
        if (!sourceVersion.matchesContent(content)) {
            sourceVersion = MemorySourceVersion.create(
                    "chapter:" + projectCode + ":" + chapterNumber, content);
        }
        List<MemoryCandidate> candidates = state.memoryCandidates();
        if (candidates.isEmpty()) {
            candidates = candidateExtractor.extract(
                    projectCode, chapterNumber, content, sourceVersion);
        }
        String commitKey = state.workflowId()
                .filter(value -> !value.isBlank())
                .orElse(projectCode + ":" + chapterNumber + ":" + sourceVersion.chapterVersion());
        List<MemoryOperationDecision> decisions = candidates.stream()
                .map(candidate -> reconciler.reconcile(
                        candidate, relatedEvents(state), relatedFacts(state)))
                .toList();
        MemoryCommitRequest memoryCommitRequest = new MemoryCommitRequest(
                projectCode,
                chapterNumber,
                content,
                sourceVersion,
                candidates,
                decisions,
                List.of(),
                List.of(),
                state.humanDecision().map("ACCEPT_CURRENT"::equals).orElse(false),
                commitKey);

        chapterPersistRepository.persistWithMemoryCommit(
                projectCode, chapterNumber, content, chapterMemory,
                storyStateSnapshot, memoryCommitRequest);

        return Map.of(
                ChapterGraphKeys.CURRENT_NODE, NODE_NAME,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE_NAME)
        );
    }

    private List<MemoryEvent> relatedEvents(ChapterGraphState state) {
        return contextItems(state).stream()
                .filter(item -> item.category() == MemoryContextCategory.EPISODES)
                .map(item -> new MemoryEvent(
                        item.itemId(), item.content(), item.sourceVersion(), null, item.storyTime()))
                .toList();
    }

    private List<MemoryFact> relatedFacts(ChapterGraphState state) {
        return contextItems(state).stream()
                .filter(item -> item.category() == MemoryContextCategory.CURRENT_STATES)
                .filter(item -> factStatus(item.status()) == MemoryFactStatus.FACT_ACTIVE)
                .map(item -> new MemoryFact(
                        item.itemId(), item.content(), List.of(item.itemId()), factStatus(item.status())))
                .toList();
    }

    private List<MemoryContextItem> contextItems(ChapterGraphState state) {
        Map<String, MemoryContextItem> items = new LinkedHashMap<>();
        state.context().ifPresent(context -> {
            if (context.getMemoryContextPack() != null) {
                context.getMemoryContextPack().items()
                        .forEach(item -> items.put(item.itemId(), item));
            }
            if (context.getReviewMemoryContextPack() != null) {
                context.getReviewMemoryContextPack().items()
                        .forEach(item -> items.put(item.itemId(), item));
            }
        });
        return List.copyOf(items.values());
    }

    private MemoryFactStatus factStatus(String status) {
        if (status == null || status.isBlank()) {
            return MemoryFactStatus.FACT_ACTIVE;
        }
        try {
            return MemoryFactStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return MemoryFactStatus.FACT_ACTIVE;
        }
    }

    private AppException illegalParameter(String detail) {
        return AppException.internal(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                detail
        );
    }
}
