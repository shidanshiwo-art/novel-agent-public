package cn.ninth.novel.domain.chapter.model.valobj;

import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 质量审核使用的固定上下文投影。
 *
 * <p>该对象不携带历史记忆列表；上一章只保留可供承接的 ending bridge，
 * 避免质量审核阶段把连续性历史重新带入上下文。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QualityContext {

    /** 当前章纲。 */
    private ChapterPlanEntity chapterPlan;

    /** 正文或章纲中实际涉及的角色静态设定。 */
    @Builder.Default
    private List<StoryCharacterEntity> relevantCharacters = List.of();

    /** 当前 ARC 节点；其 summary 作为 ARC 目标。 */
    private OutlineNodeVO currentArc;

    /** 当前 ARC 目标的自然语言摘要。 */
    private String arcGoal;

    /** 当前卷目标；当前 Context Aggregate 未提供卷级节点时保持为空。 */
    private String volumeGoal;

    /** 上一章已确认的 ending bridge。 */
    private String previousChapterEndingBridge;

    /** 当前待审核正文。 */
    private String currentDraft;
}
