package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.model.valobj.*;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.memory.model.MemoryMode;

public interface IPlanningService {

    java.util.List<OutlineNodeVO> listOutlineTree(String projectCode);

    OutlineNodeVO createOutlineNode(String projectCode, OutlineNodeVO outline);

    OutlineNodeVO updateOutlineNode(String projectCode, OutlineNodeVO outline);

    OutlineNodeVO reorderOutlineNode(
            String projectCode,
            String nodeCode,
            Integer targetSequence
    );

    void deleteOutlineNode(String projectCode, String nodeCode);

    java.util.List<ChapterOutlineVO> listChapterPlans(String projectCode);

    ChapterOutlineVO updateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            ChapterOutlineVO chapterPlan
    );

    PlanningDraftVO generateRootOutline(String projectCode, String requirement);

    void confirmRootOutline(
            String projectCode,
            String draftId,
            String summary
    );

    OutlineNodeVO createNextVolume(
            String projectCode,
            String bookNodeCode
    );

    PlanningDraftVO generateNextOutline(
            String projectCode,
            String parentNodeCode,
            String requirement
    );

    void confirmNextOutline(
            String projectCode,
            String parentNodeCode,
            String draftId,
            String title,
            String summary
    );

    PlanningDraftVO generateVolumeOutline(
            String projectCode,
            String volumeNodeCode,
            String requirement
    );

    void confirmVolumeOutline(
            String projectCode,
            String volumeNodeCode,
            String draftId,
            String title,
            String summary
    );

    PlanningDraftVO generateArcRegeneration(
            String projectCode,
            String arcNodeCode,
            String requirement
    );

    void confirmArcRegeneration(
            String projectCode,
            String arcNodeCode,
            String draftId,
            String title,
            String summary
    );

    PlanningDraftVO generateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            String requirement
    );

    /** 使用与章节 DRAFT/REVIEW 相同的 Memory 路由模式生成章节计划。 */
    default PlanningDraftVO generateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            String requirement,
            MemoryMode memoryMode
    ) {
        return generateChapterPlan(projectCode, chapterNumber, requirement);
    }

    void confirmChapterPlan(
            String projectCode,
            Integer chapterNumber,
            String draftId,
            String title,
            String summary
    );

}
