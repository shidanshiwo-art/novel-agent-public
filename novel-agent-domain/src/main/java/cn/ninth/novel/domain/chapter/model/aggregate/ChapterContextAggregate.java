package cn.ninth.novel.domain.chapter.model.aggregate;

import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * 章节生成上下文聚合。
 * 统一承载项目、故事圣经、当前章节计划、人物和历史快照，供领域服务完成生成前校验和后续编排。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChapterContextAggregate {

    /** 小说项目，用于确定作品身份、题材、目标篇幅和当前进度。 */
    private NovelProjectEntity project;
    /** 故事圣经，用于提供世界设定、核心冲突、硬规则和文风约束。 */
    private StoryBibleEntity storyBible;
    /** 当前章节计划，用于确定本章摘要和生成状态。正式标题由 arc 提供。 */
    private ChapterPlanEntity chapterPlan;
    /** 当前章节对应的 ARC，作为章节标题、章节号和大纲关联的唯一来源。 */
    private OutlineNodeVO arc;
    /** 当前项目的有效人物，用于提供正式静态设定并约束角色身份、行为和对白一致性。 */
    private List<StoryCharacterEntity> characters;
    /** 当前章节之前的历史快照，用于衔接前文并避免剧情断裂。 */
    private ChapterHistoryVO history;
    /** DRAFT Profile 预算后的统一记忆上下文；为空时沿用旧历史结构。 */
    private MemoryContextPack memoryContextPack;
    /** REVIEW Profile 的 Canonical 历史证据；不混入 DRAFT 当前状态上下文。 */
    private MemoryContextPack reviewMemoryContextPack;

    /**
     * 校验当前上下文是否具备生成章节的前置条件。
     * 小说项目、故事圣经和当前章节计划必须存在；
     * 生成第二章及之后的章节时，还必须存在直接上一章的历史快照。
     *
     * @throws AppException 上下文缺少任一必需数据时抛出非法参数异常
     */
    public void validateReadyForGeneration() {
        requirePresent(project, "小说项目不存在");
        requirePresent(storyBible, "故事圣经不存在");
        requirePresent(chapterPlan, "章节章纲不存在");
        requirePresent(arc, "当前 ARC 不存在");

        validateChapterPlanConsistency();

        Integer chapterNumber = chapterPlan.getChapterNumber();
        if (!"READY".equals(chapterPlan.getStatus())) {
            throw illegalParameter("第 " + chapterNumber + " 章细纲尚未确认");
        }
        if (chapterNumber != null && chapterNumber > 1
                && (history == null || history.getPreviousChapter() == null)) {
            throw illegalParameter("第 " + chapterNumber + " 章缺少上一章历史");
        }
    }

    private void validateChapterPlanConsistency() {
        if (arc.nodeKind() != OutlineNodeKindEnum.ARC
                || arc.nodeCode() == null
                || arc.startChapter() == null
                || arc.endChapter() == null
                || arc.title() == null
                || arc.title().isBlank()
                || arc.startChapter() > arc.endChapter()) {
            throw illegalParameter("当前 ARC 数据无效");
        }
        if (!Objects.equals(chapterPlan.getOutlineNodeCode(), arc.nodeCode())) {
            throw illegalParameter("ChapterPlan.outlineNodeCode 与当前 ARC 不一致");
        }
        if (chapterPlan.getChapterNumber() == null
                || chapterPlan.getChapterNumber() < arc.startChapter()
                || chapterPlan.getChapterNumber() > arc.endChapter()) {
            throw illegalParameter("ChapterPlan.chapterNumber 与当前 ARC 不一致");
        }
        if (!Objects.equals(chapterPlan.getTitle(), arc.title())) {
            throw illegalParameter("ChapterPlan.title 与当前 ARC.title 不一致");
        }
    }

    /** 校验生成上下文中的必需对象。 */
    private void requirePresent(Object value, String message) {
        if (value == null) {
            throw illegalParameter(message);
        }
    }

    /** 创建统一的生成前置条件异常。 */
    private AppException illegalParameter(String message) {
        return AppException.user(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
