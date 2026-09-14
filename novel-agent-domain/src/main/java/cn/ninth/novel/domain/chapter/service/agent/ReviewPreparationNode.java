package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.QualityContext;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.springframework.beans.factory.annotation.Autowired;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * 为新 Review Pipeline 准备共享上下文。
 *
 * <p>这里只做上下文装配，不调用模型，也不执行连续性或质量判断。</p>
 */
@Component
public class ReviewPreparationNode implements NodeAction<ChapterGraphState> {

    public static final String NODE = "PREPARE_REVIEW_CONTEXT";

    private final ContinuityContextProvider continuityContextProvider;

    /** 兼容直接构造节点的领域测试与旧装配入口。 */
    public ReviewPreparationNode() {
        this(new ContinuityContextProvider());
    }

    @Autowired
    public ReviewPreparationNode(ContinuityContextProvider continuityContextProvider) {
        this.continuityContextProvider = continuityContextProvider;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        ChapterContextAggregate context = state.context().orElse(null);
        ChapterPlanEntity chapterPlan = context == null ? null : context.getChapterPlan();
        String currentDraft = state.draft().orElse("");
        MemoryContextPack memoryV1 = context == null
                ? null : context.getReviewMemoryContextPack();
        ReviewContext reviewContext = ReviewContext.builder()
                .chapterPlan(chapterPlan)
                .currentDraft(currentDraft)
                .continuityContext(continuityContextProvider.provide(
                        chapterPlan, currentDraft, memoryV1))
                .qualityContext(buildQualityContext(context, chapterPlan, currentDraft))
                .build();

        Map<String, Object> update = new LinkedHashMap<>();
        update.put(
                ChapterGraphKeys.REVIEW_SESSION_ID,
                state.reviewSessionId().orElseGet(
                        () -> state.workflowId().orElseGet(() -> UUID.randomUUID().toString()))
        );
        update.put(ChapterGraphKeys.REVIEW_CONTEXT, reviewContext);
        update.put(ChapterGraphKeys.CURRENT_NODE, NODE);
        update.put(ChapterGraphKeys.COMPLETED_STAGES, java.util.List.of(NODE));
        return update;
    }

    private QualityContext buildQualityContext(
            ChapterContextAggregate context,
            ChapterPlanEntity chapterPlan,
            String currentDraft
    ) {
        OutlineNodeVO currentArc = context == null ? null : context.getArc();
        String referenceText = currentDraft
                + " " + text(chapterPlan == null ? null : chapterPlan.getTitle())
                + " " + text(chapterPlan == null ? null : chapterPlan.getSummary());
        List<StoryCharacterEntity> relevantCharacters = context == null
                ? List.of()
                : relevantCharacters(context.getCharacters(), referenceText);
        return QualityContext.builder()
                .chapterPlan(chapterPlan)
                .relevantCharacters(relevantCharacters)
                .currentArc(currentArc)
                .arcGoal(currentArc == null ? null : currentArc.summary())
                .volumeGoal(null)
                .previousChapterEndingBridge(previousChapterEndingBridge(
                        context == null ? null : context.getHistory()))
                .currentDraft(currentDraft)
                .build();
    }

    private List<StoryCharacterEntity> relevantCharacters(
            List<StoryCharacterEntity> characters,
            String referenceText
    ) {
        if (characters == null || characters.isEmpty()) {
            return List.of();
        }
        return characters.stream()
                .filter(character -> character != null
                        && character.getName() != null
                        && !character.getName().isBlank()
                        && referenceText.contains(character.getName()))
                .toList();
    }

    private String previousChapterEndingBridge(ChapterHistoryVO history) {
        if (history == null || history.getRecentMemories() == null) {
            return null;
        }
        return history.getRecentMemories().stream()
                .filter(memory -> memory != null
                        && memory.getEndingHook() != null
                        && !memory.getEndingHook().isBlank())
                .max(java.util.Comparator.comparing(
                        memory -> memory.getChapterNumber() == null ? -1 : memory.getChapterNumber()))
                .map(ChapterMemoryVO::getEndingHook)
                .orElse(null);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
