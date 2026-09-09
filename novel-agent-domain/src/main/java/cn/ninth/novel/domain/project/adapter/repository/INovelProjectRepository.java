package cn.ninth.novel.domain.project.adapter.repository;

import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;

import java.util.List;

public interface INovelProjectRepository {

    NovelProjectVO createProject(NovelProjectVO project);

    default NovelProjectVO updateTargetChapterCount(
            String projectCode,
            Integer targetChapterCount
    ) {
        throw new UnsupportedOperationException("项目章节目标调整尚未迁移");
    }

    StoryBibleVO saveBible(String projectCode, StoryBibleVO bible);

    StoryBibleVO findBible(String projectCode);

    List<StoryCharacterVO> findCharacters(String projectCode);

    StoryCharacterVO addCharacter(
            String projectCode,
            StoryCharacterVO character
    );

    StoryCharacterVO updateCharacter(
            String projectCode,
            String characterCode,
            StoryCharacterVO character
    );

    default boolean hasOutlines(String projectCode) {
        return false;
    }

    void deleteCharacter(String projectCode, String characterCode);

    GeneratedChapterVO overwriteChapterContent(
            String projectCode,
            int chapterNumber,
            String title,
            String content,
            int wordCount
    );
    void deleteChapter(String projectCode, int chapterNumber);

    NovelProjectVO findProject(String projectCode);

    /** 查询项目主键，供跨聚合的章节指标查询使用。 */
    default Long findProjectId(String projectCode) {
        return null;
    }

    GeneratedChapterVO findChapter(String projectCode, int chapterNumber);

    List<GeneratedChapterVO> findChapters(String projectCode);

    default List<GeneratedChapterVO> searchChapters(String projectCode, String keyword, int limit) {
        return List.of();
    }

    List<NovelProjectVO> findProjects();

    List<VolumeChapterGroupVO> findChaptersByVolume(String projectCode);
}
