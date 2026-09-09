package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.IStoryBibleDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStoryCharacterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryBiblePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class NovelProjectRepository implements INovelProjectRepository {

    private final INovelProjectDao novelProjectDao;
    private final IChapterPlanDao chapterPlanDao;
    private final IStoryBibleDao storyBibleDao;
    private final IOutlineNodeDao outlineNodeDao;
    private final IStoryCharacterDao storyCharacterDao;
    private final IStoryChapterDao storyChapterDao;
    private final IStorySummaryDao storySummaryDao;

    public NovelProjectRepository(
            INovelProjectDao novelProjectDao,
            IChapterPlanDao chapterPlanDao,
            IStoryBibleDao storyBibleDao,
            IOutlineNodeDao outlineNodeDao,
            IStoryCharacterDao storyCharacterDao,
            IStoryChapterDao storyChapterDao,
            IStorySummaryDao storySummaryDao
    ) {
        this.novelProjectDao = novelProjectDao;
        this.chapterPlanDao = chapterPlanDao;
        this.storyBibleDao = storyBibleDao;
        this.outlineNodeDao = outlineNodeDao;
        this.storyCharacterDao = storyCharacterDao;
        this.storyChapterDao = storyChapterDao;
        this.storySummaryDao = storySummaryDao;
    }

    @Override
    public NovelProjectVO createProject(NovelProjectVO project) {
        if (novelProjectDao.queryByProjectCode(project.projectCode()) != null) {
            throw illegalParameter(
                    "项目已存在，projectCode=" + project.projectCode()
            );
        }
        NovelProjectPO projectPO = new NovelProjectPO();
        projectPO.setProjectCode(project.projectCode());
        projectPO.setTitle(project.title());
        projectPO.setGenre(project.genre());
        projectPO.setTargetChapterCount(project.targetChapterCount());
        projectPO.setWordsPerChapter(project.wordsPerChapter());
        projectPO.setCurrentChapterNumber(project.currentChapterNumber());
        projectPO.setStatus(project.status());
        novelProjectDao.insert(projectPO);
        return toProject(projectPO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelProjectVO updateTargetChapterCount(
            String projectCode,
            Integer targetChapterCount
    ) {
        if (targetChapterCount == null || targetChapterCount <= 0) {
            throw illegalParameter("targetChapterCount 必须大于 0");
        }

        NovelProjectPO project = requireProject(projectCode);
        List<OutlineNodePO> nodes = outlineNodeDao.queryByProject(project.getId());
        Integer maxActualChapter = storyChapterDao.queryMaxChapterNumber(project.getId());
        if (maxActualChapter != null && maxActualChapter > targetChapterCount) {
            throw illegalParameter("预计章节数不能小于已完成章节数");
        }
        int maxPlannedChapter = nodes.stream()
                .filter(node -> "ARC".equals(node.getNodeKind()))
                .flatMap(node -> java.util.stream.Stream.of(
                        node.getStartChapter(), node.getEndChapter()))
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        if (maxPlannedChapter > targetChapterCount) {
            throw illegalParameter("预计章节数不能小于已规划章节");
        }

        OutlineNodePO book = outlineNodeDao.queryRoot(project.getId());
        OutlineNodePO activeVolume = book == null ? null : nodes.stream()
                .filter(node -> "VOLUME".equals(node.getNodeKind()))
                .filter(node -> java.util.Objects.equals(
                        node.getParentId(), book.getId()))
                .max(Comparator.comparing(
                        OutlineNodePO::getSequenceNo,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (activeVolume != null
                && activeVolume.getStartChapter() != null
                && activeVolume.getStartChapter() > targetChapterCount) {
            throw illegalParameter("预计章节数不能小于活动卷起始章节");
        }

        project.setTargetChapterCount(targetChapterCount);
        novelProjectDao.update(project);

        if (book != null && "BOOK".equals(book.getNodeKind())) {
            book.setEndChapter(targetChapterCount);
            outlineNodeDao.update(book);

            if (activeVolume != null) {
                activeVolume.setEndChapter(targetChapterCount);
                outlineNodeDao.update(activeVolume);
            }
        }
        return toProject(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
        NovelProjectPO project = requireProject(projectCode);
        StoryBiblePO biblePO = toBible(project.getId(), bible);
        if (storyBibleDao.queryByProjectId(project.getId()) == null) {
            storyBibleDao.insert(biblePO);
        } else {
            storyBibleDao.updateByProjectId(biblePO);
        }
        return toBible(storyBibleDao.queryByProjectId(project.getId()));
    }

    @Override
    public StoryBibleVO findBible(String projectCode) {
        NovelProjectPO project = requireProject(projectCode);
        StoryBiblePO bible = storyBibleDao.queryByProjectId(project.getId());
        return bible == null ? null : toBible(bible);
    }

    @Override
    public List<StoryCharacterVO> findCharacters(String projectCode) {
        NovelProjectPO project = requireProject(projectCode);
        return storyCharacterDao.queryByProjectId(project.getId()).stream()
                .map(this::toCharacter)
                .toList();
    }

    @Override
    public boolean hasOutlines(String projectCode) {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(projectCode);
        return project != null
                && !outlineNodeDao.queryByProject(project.getId()).isEmpty();
    }

    @Override
    public StoryCharacterVO addCharacter(
            String projectCode,
            StoryCharacterVO character
    ) {
        NovelProjectPO project = requireProject(projectCode);
        StoryCharacterPO existing = storyCharacterDao.queryByCharacterCode(
                project.getId(),
                character.characterCode()
        );
        if (existing != null) {
            throw illegalParameter(
                    "人物已存在，characterCode=" + character.characterCode()
            );
        }
        StoryCharacterPO characterPO = toCharacter(
                project.getId(),
                character
        );
        storyCharacterDao.insert(characterPO);
        return toCharacter(characterPO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoryCharacterVO updateCharacter(
            String projectCode,
            String characterCode,
            StoryCharacterVO character
    ) {
        NovelProjectPO project = requireProject(projectCode);
        StoryCharacterPO existing = storyCharacterDao.queryByCharacterCode(
                project.getId(),
                characterCode
        );
        if (existing == null) {
            throw illegalParameter(
                    "人物不存在，characterCode=" + characterCode
            );
        }
        StoryCharacterPO characterPO = toCharacter(
                project.getId(),
                character
        );
        characterPO.setId(existing.getId());
        storyCharacterDao.update(characterPO);
        return toCharacter(storyCharacterDao.queryByCharacterCode(
                project.getId(),
                characterCode
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCharacter(String projectCode, String characterCode) {
        NovelProjectPO project = requireProject(projectCode);
        if (storyCharacterDao.queryByCharacterCodeForUpdate(project.getId(), characterCode) == null) {
            throw illegalParameter("人物不存在");
        }
        try {
            if (storyCharacterDao.deleteByProjectIdAndCharacterCode(project.getId(), characterCode) == 0) {
                throw illegalParameter("人物不存在");
            }
        } catch (DataIntegrityViolationException exception) {
            throw illegalParameter("角色被其他正式数据引用或受数据约束限制，无法删除；请先处理相关引用或约束");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeneratedChapterVO overwriteChapterContent(
            String projectCode,
            int chapterNumber,
            String title,
            String content,
            int wordCount
    ) {
        NovelProjectPO project = requireProject(projectCode);
        StoryChapterPO existing = storyChapterDao
                .queryByProjectIdAndChapterNumber(
                        project.getId(),
                        chapterNumber
                );
        if (existing == null) {
            throw illegalParameter(
                    "章节不存在，chapterNumber=" + chapterNumber
            );
        }
        existing.setTitle(title);
        existing.setContent(content);
        existing.setWordCount(wordCount);
        existing.setStatus("DIRTY");
        storyChapterDao.insertOrUpdate(existing);
        storySummaryDao.markStale(project.getId(), chapterNumber);
        return findChapter(projectCode, chapterNumber);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(String projectCode, int chapterNumber) {
        NovelProjectPO project = requireProject(projectCode);
        StoryChapterPO existing = storyChapterDao
                .queryByProjectIdAndChapterNumber(project.getId(), chapterNumber);
        if (existing == null)
            throw illegalParameter("章节不存在，chapterNumber=" + chapterNumber);
        if (storyChapterDao.countByProjectIdAndChapterNumberGreaterThan(project.getId(), chapterNumber) > 0) {
            throw illegalParameter("MVP 只允许删除最后一章，chapterNumber=" + chapterNumber);
        }
        storySummaryDao.deleteByProjectIdAndChapterNumber(project.getId(), chapterNumber);
        storyChapterDao.deleteByProjectIdAndChapterNumber(project.getId(), chapterNumber);
        int restoredPlans = chapterPlanDao.updateStatusIfCurrent(
                project.getId(), existing.getChapterPlanId(), "COMPLETED", "READY");
        if (restoredPlans != 1) {
            throw illegalParameter(
                    "删除章节时 ChapterPlan 未处于 COMPLETED 状态，chapterNumber=" + chapterNumber);
        }
        Integer currentChapterNumber = storyChapterDao.queryMaxChapterNumber(project.getId());
        novelProjectDao.updateCurrentChapterNumber(
                project.getId(), currentChapterNumber == null ? 0 : currentChapterNumber);
    }

    @Override
    public NovelProjectVO findProject(String projectCode) {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(projectCode);
        return project == null ? null : toProject(project);
    }

    @Override
    public Long findProjectId(String projectCode) {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(projectCode);
        return project == null ? null : project.getId();
    }

    @Override
    public GeneratedChapterVO findChapter(
            String projectCode,
            int chapterNumber
    ) {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(projectCode);
        if (project == null) {
            return null;
        }
        StoryChapterPO chapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(
                        project.getId(),
                        chapterNumber
                );
        return chapter == null ? null : new GeneratedChapterVO(
                chapter.getChapterNumber(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getWordCount(),
                chapter.getStatus()
        );
    }

    @Override
    public List<GeneratedChapterVO> findChapters(String projectCode) {
        NovelProjectPO project = requireProject(projectCode);
        return toGeneratedChapters(storyChapterDao.queryByProjectId(
                project.getId()
        ));
    }

    @Override
    public List<GeneratedChapterVO> searchChapters(String projectCode, String keyword, int limit) {
        NovelProjectPO project = requireProject(projectCode);
        return toGeneratedChapters(storyChapterDao.queryByProjectIdAndContentLike(
                project.getId(), keyword, limit));
    }

    @Override
    public List<NovelProjectVO> findProjects() {
        return novelProjectDao.queryAll().stream()
                .map(this::toProject)
                .toList();
    }

    @Override
    public List<VolumeChapterGroupVO> findChaptersByVolume(
            String projectCode
    ) {
        NovelProjectPO project = requireProject(projectCode);
        List<GeneratedChapterVO> chapters = toGeneratedChapters(
                storyChapterDao.queryByProjectId(project.getId())
        );
        List<OutlineNodePO> volumes = outlineNodeDao
                .queryByProject(project.getId()).stream()
                .filter(node -> "VOLUME".equals(node.getNodeKind()))
                .sorted(Comparator.comparing(
                                OutlineNodePO::getSequenceNo,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                OutlineNodePO::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .toList();

        Set<Integer> assignedChapterNumbers = new HashSet<>();
        List<VolumeChapterGroupVO> groups = new ArrayList<>(volumes.size() + 1);
        for (OutlineNodePO volume : volumes) {
            List<GeneratedChapterVO> volumeChapters = chapters.stream()
                    .filter(chapter -> !assignedChapterNumbers.contains(
                            chapter.chapterNumber()
                    ))
                    .filter(chapter -> containsChapter(
                            volume,
                            chapter.chapterNumber()
                    ))
                    .toList();
            volumeChapters.forEach(chapter -> assignedChapterNumbers.add(
                    chapter.chapterNumber()
            ));
            groups.add(new VolumeChapterGroupVO(
                    volume.getNodeCode(),
                    volume.getSequenceNo(),
                    volume.getTitle(),
                    volume.getStartChapter(),
                    volume.getEndChapter(),
                    volume.getStatus(),
                    volumeChapters
            ));
        }

        List<GeneratedChapterVO> unassigned = chapters.stream()
                .filter(chapter -> !assignedChapterNumbers.contains(
                        chapter.chapterNumber()
                ))
                .toList();
        if (!unassigned.isEmpty()) {
            groups.add(new VolumeChapterGroupVO(
                    "UNASSIGNED", null, "未分卷", null, null, null, unassigned
            ));
        }
        return List.copyOf(groups);
    }

    private boolean containsChapter(OutlineNodePO volume, int chapterNumber) {
        return volume.getStartChapter() != null
                && volume.getEndChapter() != null
                && chapterNumber >= volume.getStartChapter()
                && chapterNumber <= volume.getEndChapter();
    }

    private List<GeneratedChapterVO> toGeneratedChapters(
            List<StoryChapterPO> chapters
    ) {
        return chapters.stream()
                .map(chapter -> new GeneratedChapterVO(
                        chapter.getChapterNumber(),
                        chapter.getTitle(),
                        chapter.getContent(),
                        chapter.getWordCount(),
                        chapter.getStatus()
                ))
                .toList();
    }

    private NovelProjectPO requireProject(String projectCode) {
        NovelProjectPO project = novelProjectDao.queryByProjectCode(projectCode);
        if (project == null) {
            throw illegalParameter("项目不存在，projectCode=" + projectCode);
        }
        return project;
    }

    private NovelProjectVO toProject(NovelProjectPO project) {
        return new NovelProjectVO(
                project.getProjectCode(),
                project.getTitle(),
                project.getGenre(),
                project.getTargetChapterCount(),
                project.getWordsPerChapter(),
                project.getCurrentChapterNumber(),
                project.getStatus()
        );
    }

    private StoryBiblePO toBible(Long projectId, StoryBibleVO bible) {
        StoryBiblePO biblePO = new StoryBiblePO();
        biblePO.setProjectId(projectId);
        biblePO.setOneSentencePremise(bible.oneSentencePremise());
        biblePO.setCoreTheme(bible.coreTheme());
        biblePO.setMainConflict(bible.mainConflict());
        biblePO.setEndingDirection(bible.endingDirection());
        biblePO.setWorldBackground(bible.worldBackground());
        biblePO.setPowerSystemJson(bible.powerSystemJson());
        biblePO.setHardRulesJson(bible.hardRulesJson());
        biblePO.setStyleGuide(bible.styleGuide());
        biblePO.setStatus(bible.status());
        return biblePO;
    }

    private StoryBibleVO toBible(StoryBiblePO bible) {
        return new StoryBibleVO(
                bible.getOneSentencePremise(),
                bible.getCoreTheme(),
                bible.getMainConflict(),
                bible.getEndingDirection(),
                bible.getWorldBackground(),
                bible.getPowerSystemJson(),
                bible.getHardRulesJson(),
                bible.getStyleGuide(),
                bible.getStatus()
        );
    }

    private StoryCharacterPO toCharacter(
            Long projectId,
            StoryCharacterVO character
    ) {
        StoryCharacterPO characterPO = new StoryCharacterPO();
        characterPO.setProjectId(projectId);
        characterPO.setCharacterCode(character.characterCode());
        characterPO.setName(character.name());
        characterPO.setRoleType(character.roleType());
        characterPO.setGender(character.gender());
        characterPO.setAgeDescription(character.ageDescription());
        characterPO.setAppearance(character.appearance());
        characterPO.setPersonality(character.personality());
        characterPO.setBackgroundStory(character.backgroundStory());
        characterPO.setNote(character.note());
        characterPO.setCurrentStateJson(character.currentStateJson());
        characterPO.setLifeStatus(character.lifeStatus());
        characterPO.setStatus(character.status());
        return characterPO;
    }

    private StoryCharacterVO toCharacter(StoryCharacterPO character) {
        return new StoryCharacterVO(
                character.getCharacterCode(), character.getName(), character.getRoleType(),
                character.getGender(), character.getAgeDescription(), character.getAppearance(),
                character.getPersonality(), character.getBackgroundStory(), character.getNote(),
                character.getCurrentStateJson(),
                character.getLifeStatus(),
                character.getStatus()
        );
    }

    private AppException illegalParameter(String detail) {
        if (detail != null
                && (detail.contains("projectCode")
                || detail.contains("characterCode")
                || detail.contains("chapterNumber")
                || detail.contains("ChapterPlan")
                || detail.contains("parentNodeCode"))) {
            return AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
        }
        return AppException.user(ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
    }
}
