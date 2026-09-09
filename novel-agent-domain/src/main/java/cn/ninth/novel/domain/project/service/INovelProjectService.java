package cn.ninth.novel.domain.project.service;

import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterUpdateResultVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;

import java.util.List;

public interface INovelProjectService {

    NovelProjectVO createProject(NovelProjectVO project);

    default NovelProjectVO updateTargetChapterCount(
            String projectCode,
            Integer targetChapterCount
    ) {
        throw new UnsupportedOperationException("项目章节目标调整尚未迁移");
    }

    StoryBibleVO saveBible(String projectCode, StoryBibleVO bible);

    StoryBibleVO getBible(String projectCode);

    PlanningDraftVO generateStoryBible(String projectCode, String requirement);

    PlanningDraftVO generateCharacters(
            String projectCode,
            Integer preferredCount,
            String requirement
    );

    List<StoryCharacterVO> confirmCharacters(
            String projectCode,
            String draftId,
            List<CharacterDraftVO> characters
    );

    void discardCharacterDraft(String projectCode, String draftId);

    StoryBibleVO confirmStoryBible(
            String projectCode,
            String draftId,
            StoryBibleVO editedBible
    );

    List<StoryCharacterVO> listCharacters(String projectCode);

    StoryCharacterVO addCharacter(
            String projectCode,
            StoryCharacterVO character
    );

    StoryCharacterVO updateCharacter(
            String projectCode,
            String characterCode,
            StoryCharacterVO character
    );

    default CharacterUpdateResultVO updateCharacterWithWarning(
            String projectCode,
            String characterCode,
            StoryCharacterVO character
    ) {
        return new CharacterUpdateResultVO(
                updateCharacter(projectCode, characterCode, character),
                null
        );
    }

    void deleteCharacter(String projectCode, String characterCode);

    /**
     * 保存人工修改的正文，并在正文保存事务提交后尝试自动更新章节记忆。
     * 自动更新失败时正文仍已保存，返回的章节保持 DIRTY 状态。
     */
    GeneratedChapterVO overwriteChapterContent(
            String projectCode,
            int chapterNumber,
            String title,
            String content
    );

    /**
     * 重新同步人工修改章节的正文派生数据。
     */
    default GeneratedChapterVO resyncChapterDerivedData(
            String projectCode,
            int chapterNumber
    ) {
        throw new UnsupportedOperationException("当前项目服务未提供正文派生数据同步能力");
    }

    void deleteChapter(String projectCode, int chapterNumber);

    GeneratedChapterVO getChapter(String projectCode, int chapterNumber);

    List<GeneratedChapterVO> listChapters(String projectCode);

    default List<GeneratedChapterVO> searchChapters(String projectCode, String keyword) {
        return List.of();
    }

    List<NovelProjectVO> listProjects();

    List<VolumeChapterGroupVO> listChaptersByVolume(String projectCode);

    NovelProjectVO getProject(String projectCode);
}
