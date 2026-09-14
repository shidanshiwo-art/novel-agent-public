package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.adapter.repository.IContextRepository;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.infrastructure.dao.*;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryBiblePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MySQL 章节上下文仓储实现。
 * 通过项目业务编码解析数据库关联关系，并将各类持久化对象转换为领域模型。
 */
@Repository
public class ContextRepository implements IContextRepository {

    /** DRAFT 等正文节点最多加载的近期 ChapterMemory 章节数量；不用于完整正文。 */
    private static final int RECENT_MEMORY_CHAPTER_LIMIT = 8;
    /** 人工修改后的章节正文及其派生数据不可作为确认历史。 */
    private static final String DIRTY_CHAPTER_STATUS = "DIRTY";

    /** 小说项目 DAO，用于通过业务编码解析项目及其数据库主键。 */
    private final INovelProjectDao novelProjectDao;
    /** 故事圣经 DAO，用于读取项目级世界观和创作约束。 */
    private final IStoryBibleDao storyBibleDao;
    /** 章节计划 DAO，用于读取指定章节对应的当前章计划。 */
    private final IChapterPlanDao chapterPlanDao;
    /** 大纲节点 DAO，用于解析 ChapterPlan 所属 ARC 及其正式标题。 */
    private final IOutlineNodeDao outlineNodeDao;
    /** 章节正文 DAO，用于读取当前章节的直接上一章。 */
    private final IStoryChapterDao storyChapterDao;
    /** 人物 DAO，用于读取项目下的人物设定和当前状态。 */
    private final IStoryCharacterDao storyCharacterDao;
    /** 章节摘要 DAO，用于读取当前章节之前的近期记忆。 */
    private final IStorySummaryDao storySummaryDao;
    /** Canonical Memory 只读适配器，负责 accepted source 和生命周期过滤。 */
    private final CanonicalMemoryContextReader canonicalMemoryContextReader;

    /**
     * 创建结构化上下文仓储。
     *
     * @param novelProjectDao 小说项目数据访问对象
     * @param storyBibleDao 故事圣经数据访问对象
     * @param chapterPlanDao 章节计划数据访问对象
     * @param outlineNodeDao 大纲节点数据访问对象
     * @param storyChapterDao 章节正文数据访问对象
     * @param storyCharacterDao 人物数据访问对象
     * @param storySummaryDao 章节摘要数据访问对象
     */
    public ContextRepository(
            INovelProjectDao novelProjectDao,
            IStoryBibleDao storyBibleDao,
            IChapterPlanDao chapterPlanDao,
            IOutlineNodeDao outlineNodeDao,
            IStoryChapterDao storyChapterDao,
            IStoryCharacterDao storyCharacterDao,
            IStorySummaryDao storySummaryDao) {
        this(
                novelProjectDao,
                storyBibleDao,
                chapterPlanDao,
                outlineNodeDao,
                storyChapterDao,
                storyCharacterDao,
                storySummaryDao,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ContextRepository(
            INovelProjectDao novelProjectDao,
            IStoryBibleDao storyBibleDao,
            IChapterPlanDao chapterPlanDao,
            IOutlineNodeDao outlineNodeDao,
            IStoryChapterDao storyChapterDao,
            IStoryCharacterDao storyCharacterDao,
            IStorySummaryDao storySummaryDao,
            CanonicalMemoryContextReader canonicalMemoryContextReader) {
        this.novelProjectDao = novelProjectDao;
        this.storyBibleDao = storyBibleDao;
        this.chapterPlanDao = chapterPlanDao;
        this.outlineNodeDao = outlineNodeDao;
        this.storyChapterDao = storyChapterDao;
        this.storyCharacterDao = storyCharacterDao;
        this.storySummaryDao = storySummaryDao;
        this.canonicalMemoryContextReader = canonicalMemoryContextReader;
    }

    /**
     * 根据项目业务编码加载小说项目，并转换为领域实体。
     *
     * @param projectCode 项目业务编码
     * @return 小说项目实体，项目不存在时返回 {@code null}
     */
    @Override
    public NovelProjectEntity loadProject(String projectCode) {
        NovelProjectPO novelProjectPO = novelProjectDao.queryByProjectCode(projectCode);
        if (novelProjectPO == null) {
            return null;
        }
        return NovelProjectEntity.builder()
                .projectCode(novelProjectPO.getProjectCode())
                .title(novelProjectPO.getTitle())
                .genre(novelProjectPO.getGenre())
                .targetChapterCount(novelProjectPO.getTargetChapterCount())
                .wordsPerChapter(novelProjectPO.getWordsPerChapter())
                .currentChapterNumber(novelProjectPO.getCurrentChapterNumber())
                .status(novelProjectPO.getStatus())
                .build();
    }

    /**
     * 加载指定项目的故事圣经，并转换为领域实体。
     *
     * @param projectCode 项目业务编码
     * @return 故事圣经实体，项目或故事圣经不存在时返回 {@code null}
     */
    @Override
    public StoryBibleEntity loadStoryBible(String projectCode) {
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return null;
        }
        StoryBiblePO storyBible = storyBibleDao.queryByProjectId(project.getId());
        if (storyBible == null) {
            return null;
        }
        return StoryBibleEntity.builder()
                .projectId(storyBible.getProjectId())
                .oneSentencePremise(storyBible.getOneSentencePremise())
                .coreTheme(storyBible.getCoreTheme())
                .mainConflict(storyBible.getMainConflict())
                .endingDirection(storyBible.getEndingDirection())
                .worldBackground(storyBible.getWorldBackground())
                .powerSystemJson(storyBible.getPowerSystemJson())
                .hardRulesJson(storyBible.getHardRulesJson())
                .styleGuide(storyBible.getStyleGuide())
                .status(storyBible.getStatus())
                .build();
    }

    /**
     * 加载指定项目和章节号对应的章节计划。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 待生成章节号
     * @return 章节计划对应的上下文实体，项目或章节计划不存在时返回 {@code null}
     */
    @Override
    public ChapterPlanEntity loadChapterPlan(String projectCode, int chapterNumber) {
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return null;
        }
        ChapterPlanPO chapterPlan = chapterPlanDao.queryByChapterNumber(
                project.getId(), chapterNumber);
        if (chapterPlan == null) {
            return null;
        }
        OutlineNodePO outlineNode = chapterPlan.getOutlineNodeId() == null
                ? null
                : outlineNodeDao.queryById(project.getId(), chapterPlan.getOutlineNodeId());
        return ChapterPlanEntity.builder()
                .id(chapterPlan.getId())
                .projectId(chapterPlan.getProjectId())
                .outlineNodeId(chapterPlan.getOutlineNodeId())
                .outlineNodeCode(outlineNode == null ? null : outlineNode.getNodeCode())
                .chapterNumber(chapterPlan.getChapterNumber())
                .title(chapterPlan.getTitle())
                .summary(chapterPlan.getSummary())
                .status(chapterPlan.getStatus())
                .build();
    }

    @Override
    public OutlineNodeVO loadCurrentArc(String projectCode, int chapterNumber) {
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return null;
        }
        List<OutlineNodePO> nodes = outlineNodeDao.queryByProject(project.getId());
        Map<Long, OutlineNodePO> nodesById = nodes.stream()
                .filter(node -> node != null && node.getId() != null)
                .collect(Collectors.toMap(
                        OutlineNodePO::getId,
                        Function.identity(),
                        (left, right) -> left
                ));
        return nodes.stream()
                .filter(node -> "ARC".equalsIgnoreCase(node.getNodeKind()))
                .filter(node -> node.getStartChapter() != null
                        && node.getEndChapter() != null
                        && node.getStartChapter() <= chapterNumber
                        && chapterNumber <= node.getEndChapter())
                .filter(node -> node.getParentId() != null
                        && "VOLUME".equalsIgnoreCase(
                        value(nodesById.get(node.getParentId()), OutlineNodePO::getNodeKind)))
                .sorted(Comparator.comparing(
                        node -> node.getEndChapter() - node.getStartChapter()))
                .map(node -> toOutlineNode(node, nodesById))
                .findFirst()
                .orElse(null);
    }

    private OutlineNodeVO toOutlineNode(
            OutlineNodePO node,
            Map<Long, OutlineNodePO> nodesById
    ) {
        OutlineNodeKindEnum nodeKind = node.getNodeKind() == null
                ? null : OutlineNodeKindEnum.valueOf(node.getNodeKind().toUpperCase());
        OutlineNodePO parent = node.getParentId() == null
                ? null : nodesById.get(node.getParentId());
        return new OutlineNodeVO(
                node.getNodeCode(),
                parent == null ? null : parent.getNodeCode(),
                nodeKind,
                node.getSequenceNo(),
                node.getTitle(),
                node.getSummary(),
                node.getStartChapter(),
                node.getEndChapter(),
                node.getStatus()
        );
    }

    private <T> T value(OutlineNodePO node, Function<OutlineNodePO, T> mapper) {
        return node == null ? null : mapper.apply(node);
    }

    /**
     * 加载指定项目下的全部人物数据，并转换为领域实体列表。
     *
     * @param projectCode 项目业务编码
     * @return 人物实体列表，项目不存在或没有人物时返回空列表
     */
    @Override
    public List<StoryCharacterEntity> loadCharacters(String projectCode) {
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return List.of();
        }
        return storyCharacterDao.queryByProjectId(project.getId()).stream()
                .map(this::toCharacterEntity)
                .toList();
    }

    /**
     * 加载当前章节之前的近期压缩记忆和直接上一章正文。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 当前待生成章节号
     * @return 章节历史值对象；第一章的上一章字段为空
     */
    @Override
    public ChapterHistoryVO loadHistory(String projectCode, int chapterNumber) {
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return ChapterHistoryVO.builder()
                    .recentMemories(List.of())
                    .storyStateSnapshot(StoryStateSnapshot.empty())
                    .previousChapter(null)
                    .build();
        }

        List<StorySummaryPO> summaries = storySummaryDao
                .queryRecent(project.getId(), chapterNumber, RECENT_MEMORY_CHAPTER_LIMIT)
                .stream()
                .sorted(Comparator.comparing(StorySummaryPO::getChapterNumber))
                .toList();
        List<ChapterMemoryVO> recentMemories = summaries.stream()
                .map(ChapterMemoryMapper::toMemory)
                .toList();
        StoryStateSnapshot storyStateSnapshot = latestStoryStateSnapshot(summaries);
        StoryChapterPO previousChapter = chapterNumber == 1
                ? null
                : storyChapterDao.queryByProjectIdAndChapterNumber(
                        project.getId(), chapterNumber - 1);
        if (previousChapter != null
                && DIRTY_CHAPTER_STATUS.equalsIgnoreCase(previousChapter.getStatus())) {
            previousChapter = null;
        }

        return ChapterHistoryVO.builder()
                .recentMemories(recentMemories)
                .storyStateSnapshot(storyStateSnapshot)
                .previousChapter(toPreviousChapter(previousChapter))
                .build();
    }

    private StoryStateSnapshot latestStoryStateSnapshot(List<StorySummaryPO> summaries) {
        for (int index = summaries.size() - 1; index >= 0; index--) {
            StorySummaryPO summary = summaries.get(index);
            if (summary != null
                    && summary.getStoryStateSnapshotJson() != null
                    && !summary.getStoryStateSnapshotJson().isBlank()) {
                return ChapterMemoryMapper.toStoryStateSnapshot(summary);
            }
        }
        return StoryStateSnapshot.empty();
    }

    @Override
    public List<ChapterMemoryVO> findRecentChapterMemories(
            String projectCode,
            Integer chapterNumber,
            int limit
    ) {
        if (chapterNumber == null || chapterNumber <= 1 || limit <= 0) {
            return List.of();
        }
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return List.of();
        }
        return storySummaryDao.queryRecent(project.getId(), chapterNumber, limit).stream()
                .map(ChapterMemoryMapper::toMemory)
                .toList();
    }

    @Override
    public List<MemoryContextItem> findMemoryContextItems(
            String projectCode,
            Integer chapterNumber
    ) {
        if (chapterNumber == null || chapterNumber <= 1 || storySummaryDao == null) {
            return List.of();
        }
        NovelProjectPO project = findProject(projectCode);
        if (project == null) {
            return List.of();
        }
        return ChapterMemoryMapper.toMemoryContextItems(
                storySummaryDao.queryAllValidByProjectId(project.getId()),
                chapterNumber);
    }

    @Override
    public List<MemoryContextItem> findCanonicalMemoryContextItems(
            String projectCode,
            Integer chapterNumber
    ) {
        if (canonicalMemoryContextReader == null) {
            return List.of();
        }
        return canonicalMemoryContextReader.find(projectCode, chapterNumber);
    }

    /**
     * 通过业务编码查询项目持久化对象。
     *
     * @param projectCode 项目业务编码
     * @return 项目持久化对象，不存在时返回 {@code null}
     */
    private NovelProjectPO findProject(String projectCode) {
        return novelProjectDao.queryByProjectCode(projectCode);
    }

    /**
     * 将人物持久化对象转换为领域实体。
     *
     * @param character 人物持久化对象
     * @return 人物领域实体
     */
    private StoryCharacterEntity toCharacterEntity(StoryCharacterPO character) {
        return StoryCharacterEntity.builder()
                .projectId(character.getProjectId())
                .characterCode(character.getCharacterCode())
                .name(character.getName())
                .roleType(character.getRoleType())
                .gender(character.getGender())
                .ageDescription(character.getAgeDescription())
                .appearance(character.getAppearance())
                .personality(character.getPersonality())
                .backgroundStory(character.getBackgroundStory())
                .note(character.getNote())
                .status(character.getStatus())
                .build();
    }

    /**
     * 将章节正文持久化对象转换为上一章快照。
     *
     * @param chapter 章节正文持久化对象，允许为空
     * @return 上一章快照；输入为空时返回 {@code null}
     */
    private PreviousChapterVO toPreviousChapter(StoryChapterPO chapter) {
        if (chapter == null) {
            return null;
        }
        return PreviousChapterVO.builder()
                .chapterId(chapter.getId())
                .chapterNumber(chapter.getChapterNumber())
                .title(chapter.getTitle())
                .content(chapter.getContent())
                .wordCount(chapter.getWordCount())
                .status(chapter.getStatus())
                .build();
    }
}
