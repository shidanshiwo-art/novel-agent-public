package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.infrastructure.dao.IStoryBibleDao;
import cn.ninth.novel.infrastructure.dao.IStoryCharacterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryBiblePO;
import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class PlanningRepository implements IPlanningRepository {

    private final INovelProjectDao projectDao;
    private final IOutlineNodeDao outlineDao;
    private final IChapterPlanDao chapterPlanDao;
    private final IStoryBibleDao storyBibleDao;
    private final IStoryCharacterDao storyCharacterDao;
    private final IStorySummaryDao storySummaryDao;

    /**
     * 保留给只关注大纲排序的旧测试夹具使用。
     */
    public PlanningRepository(
            INovelProjectDao projectDao,
            IOutlineNodeDao outlineDao,
            IChapterPlanDao chapterPlanDao
    ) {
        this(projectDao, outlineDao, chapterPlanDao,
                null, null, null);
    }

    @Autowired
    public PlanningRepository(
            INovelProjectDao projectDao,
            IOutlineNodeDao outlineDao,
            IChapterPlanDao chapterPlanDao,
            IStoryBibleDao storyBibleDao,
            IStoryCharacterDao storyCharacterDao,
            IStorySummaryDao storySummaryDao
    ) {
        this.projectDao = projectDao;
        this.outlineDao = outlineDao;
        this.chapterPlanDao = chapterPlanDao;
        this.storyBibleDao = storyBibleDao;
        this.storyCharacterDao = storyCharacterDao;
        this.storySummaryDao = storySummaryDao;
    }

    @Override
    public NovelProjectVO findProject(String projectCode) {
        NovelProjectPO project = projectDao.queryByProjectCode(projectCode);
        return project == null ? null : new NovelProjectVO(
                project.getProjectCode(), project.getTitle(), project.getGenre(),
                project.getTargetChapterCount(), project.getWordsPerChapter(),
                project.getCurrentChapterNumber(), project.getStatus()
        );
    }

    @Override
    public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return null;
        }
        OutlineNodePO node = outlineDao.queryByCode(project.getId(), nodeCode);
        return node == null || !isUnifiedOutlineNode(node)
                ? null : toOutline(node, parentNode(project.getId(), node.getParentId()));
    }

    @Override
    public OutlineNodeVO findRootOutline(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return null;
        }
        OutlineNodePO node = outlineDao.queryRoot(project.getId());
        return node == null || !isUnifiedOutlineNode(node) ? null : toOutline(node, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OutlineNodeVO> listOutlines(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return List.of();
        }
        migrateLegacyBookArcs(projectCode);
        List<OutlineNodePO> nodes = outlineDao.queryByProject(project.getId());
        Map<Long, OutlineNodePO> nodesById = nodes.stream()
                .collect(Collectors.toMap(OutlineNodePO::getId, Function.identity()));
        return nodes.stream()
                .filter(this::isUnifiedOutlineNode)
                .map(node -> toOutline(node, nodesById.get(node.getParentId())))
                .toList();
    }

    @Override
    public List<OutlineNodeVO> listChildren(String projectCode, String parentNodeCode) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO parent = requireNode(project.getId(), parentNodeCode);
        return outlineDao.queryChildren(project.getId(), parent.getId()).stream()
                .filter(this::isUnifiedOutlineNode)
                .map(node -> toOutline(node, parent))
                .toList();
    }

    @Override
    public boolean hasChildren(String projectCode, String nodeCode) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO node = requireNode(project.getId(), nodeCode);
        return !outlineDao.queryChildren(project.getId(), node.getId()).isEmpty();
    }

    @Override
    public boolean hasChapterPlans(String projectCode, String nodeCode) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO node = requireNode(project.getId(), nodeCode);
        return !chapterPlanDao.queryByOutlineNode(project.getId(), node.getId()).isEmpty();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutlineNodeVO saveOutline(String projectCode, OutlineNodeVO outline) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO node = toOutlinePO(project.getId(), outline, parentId(project.getId(), outline));
        outlineDao.insert(node);
        return toOutline(node, parentNode(project.getId(), node.getParentId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutlineNodeVO finalizeCurrentVolumeAndCreateNext(
            String projectCode,
            String currentVolumeNodeCode,
            OutlineNodeVO nextVolume
    ) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO current = outlineDao.queryByCodeForUpdate(
                project.getId(), currentVolumeNodeCode
        );
        if (current == null || !"VOLUME".equals(current.getNodeKind())) {
            throw illegalParameter("当前活动卷不存在，nodeCode=" + currentVolumeNodeCode);
        }
        if (current.getSummary() == null || current.getSummary().isBlank()
                || "UNPLANNED".equalsIgnoreCase(current.getStatus())) {
            throw illegalParameter("请先完成当前卷规划");
        }
        if (current.getParentId() == null) {
            throw illegalParameter("当前活动卷缺少 BOOK 父节点");
        }

        List<OutlineNodePO> volumeSiblings = outlineDao.queryChildrenForUpdate(
                project.getId(), current.getParentId()
        ).stream()
                .filter(node -> "VOLUME".equals(node.getNodeKind()))
                .toList();
        int lastVolumeSequence = volumeSiblings.stream()
                .map(OutlineNodePO::getSequenceNo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        if (!Objects.equals(current.getSequenceNo(), lastVolumeSequence)) {
            throw illegalParameter("只能从当前最后一卷创建下一卷");
        }

        OutlineNodePO book = outlineDao.queryById(project.getId(), current.getParentId());
        if (book == null || !"BOOK".equals(book.getNodeKind())) {
            throw illegalParameter("当前活动卷的父节点不是 BOOK");
        }
        List<OutlineNodePO> arcs = outlineDao.queryChildrenForUpdate(
                project.getId(), current.getId()
        );
        Integer actualEndChapter = arcs.stream()
                .filter(node -> "ARC".equals(node.getNodeKind()))
                .map(OutlineNodePO::getStartChapter)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        if (actualEndChapter == null) {
            throw illegalParameter("当前卷尚未规划章节，不能开始下一卷");
        }
        if (current.getStartChapter() == null
                || current.getEndChapter() == null
                || actualEndChapter < current.getStartChapter()
                || actualEndChapter > current.getEndChapter()) {
            throw illegalParameter("当前卷实际章节超出卷范围");
        }
        if (book.getEndChapter() == null || actualEndChapter >= book.getEndChapter()) {
            throw illegalParameter("当前卷没有可用的下一卷范围");
        }
        if (nextVolume == null
                || nextVolume.nodeKind() != OutlineNodeKindEnum.VOLUME
                || nextVolume.nodeCode() == null
                || nextVolume.nodeCode().isBlank()
                || !Objects.equals(nextVolume.parentNodeCode(), book.getNodeCode())
                || !Objects.equals(nextVolume.sequenceNo(), lastVolumeSequence + 1)) {
            throw illegalParameter("下一卷结构已变化，请重新生成");
        }
        if (outlineDao.queryByCode(project.getId(), nextVolume.nodeCode()) != null) {
            throw illegalParameter("下一卷节点已存在，请重新生成");
        }

        current.setEndChapter(actualEndChapter);
        outlineDao.update(current);

        OutlineNodePO next = toOutlinePO(
                project.getId(),
                new OutlineNodeVO(
                        nextVolume.nodeCode(), book.getNodeCode(), OutlineNodeKindEnum.VOLUME,
                        lastVolumeSequence + 1, nextVolume.title(), nextVolume.summary(),
                        actualEndChapter + 1, book.getEndChapter(), nextVolume.status()
                ),
                book.getId()
        );
        outlineDao.insert(next);
        return toOutline(next, book);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutlineNodeVO updateOutline(String projectCode, OutlineNodeVO outline) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO existing = requireNode(project.getId(), outline.nodeCode());
        validateArcChapterRange(outline);
        List<OutlineNodePO> siblings = new ArrayList<>(outlineDao.querySiblings(
                project.getId(), existing.getParentId()
        ));
        existing.setNodeKind(outline.nodeKind().name());
        existing.setStartChapter(outline.startChapter());
        existing.setEndChapter(outline.endChapter());
        existing.setTitle(outline.title());
        existing.setSummary(outline.summary());
        existing.setStatus(outline.status());
        normalizeSiblingSequences(siblings, existing, outline.sequenceNo());
        return toOutline(existing, parentNode(project.getId(), existing.getParentId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutlineNodeVO reorderOutline(
            String projectCode,
            String nodeCode,
            Integer targetSequence
    ) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO target = requireNode(project.getId(), nodeCode);
        List<OutlineNodePO> siblings = new ArrayList<>(outlineDao.querySiblings(
                project.getId(), target.getParentId()
        ));
        normalizeSiblingSequences(siblings, target, targetSequence);
        return toOutline(target, parentNode(project.getId(), target.getParentId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOutline(String projectCode, String nodeCode) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO node = requireNode(project.getId(), nodeCode);
        OutlineNodePO previousVolume = findPreviousVolumeAfterTailDeletion(
                project.getId(), node
        );
        outlineDao.deleteOne(project.getId(), node.getId());
        if (previousVolume != null) {
            Integer restoredEndChapter = bookEndChapter(
                    project.getId(), previousVolume.getParentId()
            );
            if (restoredEndChapter != null
                    && !Objects.equals(previousVolume.getEndChapter(), restoredEndChapter)) {
                previousVolume.setEndChapter(restoredEndChapter);
                outlineDao.update(previousVolume);
            }
        }
    }

    /**
     * 删除 BOOK 下的尾 Volume 后，恢复前一 Volume 的可规划范围。
     *
     * <p>创建下一卷时，当前尾 Volume 会被封口到最后一个 ARC；删除这一个空尾卷时，
     * 该封口必须反向撤销。非尾 Volume、ARC 以及没有前一 Volume 的情况不改变原有删除语义。</p>
     */
    private OutlineNodePO findPreviousVolumeAfterTailDeletion(
            Long projectId,
            OutlineNodePO node
    ) {
        if (node == null
                || !"VOLUME".equals(node.getNodeKind())
                || node.getParentId() == null) {
            return null;
        }
        OutlineNodePO book = outlineDao.queryById(projectId, node.getParentId());
        if (book == null || !"BOOK".equals(book.getNodeKind())) {
            return null;
        }
        List<OutlineNodePO> volumes = outlineDao.queryChildrenForUpdate(
                projectId, book.getId()
        ).stream()
                .filter(child -> "VOLUME".equals(child.getNodeKind()))
                .toList();
        OutlineNodePO tail = volumes.stream()
                .max(Comparator.comparing(
                        OutlineNodePO::getSequenceNo,
                        Comparator.nullsFirst(Integer::compareTo)
                ))
                .orElse(null);
        if (tail == null || !Objects.equals(tail.getId(), node.getId())) {
            return null;
        }
        return volumes.stream()
                .filter(volume -> !Objects.equals(volume.getId(), node.getId()))
                .max(Comparator.comparing(
                        OutlineNodePO::getSequenceNo,
                        Comparator.nullsFirst(Integer::compareTo)
                ))
                .orElse(null);
    }

    private Integer bookEndChapter(Long projectId, Long bookId) {
        OutlineNodePO book = outlineDao.queryById(projectId, bookId);
        return book == null ? null : book.getEndChapter();
    }

    @Override
    public int nextSequence(String projectCode, String parentNodeCode) {
        NovelProjectPO project = requireProject(projectCode);
        Long parentId = parentNodeCode == null
                ? null : requireNode(project.getId(), parentNodeCode).getId();
        Integer maxSequence = outlineDao.queryMaxSequenceNo(project.getId(), parentId);
        return (maxSequence == null ? 0 : maxSequence) + 1;
    }

    /**
     * 章节计划相关方法只负责项目与持久化记录之间的转换，不执行领域状态、范围或叶节点校验。
     */
    public ChapterPlanPO findChapterPlan(String projectCode, Integer chapterNumber) {
        NovelProjectPO project = findProjectPO(projectCode);
        return project == null
                ? null : chapterPlanDao.queryByChapterNumber(project.getId(), chapterNumber);
    }

    public List<ChapterPlanPO> listChapterPlans(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        return project == null ? List.of() : chapterPlanDao.queryByProject(project.getId());
    }

    @Override
    public List<ChapterOutlineVO> findChapterPlanOutlines(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return List.of();
        }
        return chapterPlanDao.queryByProject(project.getId()).stream()
                .map(plan -> toChapterOutline(project.getId(), plan))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChapterPlanPO updateChapterPlan(
            String projectCode,
            ChapterPlanPO chapterPlan
    ) {
        NovelProjectPO project = requireProject(projectCode);
        chapterPlan.setProjectId(project.getId());
        chapterPlanDao.update(chapterPlan);
        return chapterPlan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterOutlineVO updateChapterPlan(
            String projectCode,
            Integer chapterNumber,
            ChapterOutlineVO chapterPlan
    ) {
        NovelProjectPO project = requireProject(projectCode);
        ChapterPlanPO existing = chapterPlanDao.queryByChapterNumber(
                project.getId(), chapterNumber
        );
        if (existing == null) {
            throw illegalParameter("章节计划不存在，chapterNumber=" + chapterNumber);
        }
        OutlineNodePO arc = existing.getOutlineNodeId() == null
                ? null : outlineDao.queryById(project.getId(), existing.getOutlineNodeId());
        if (arc == null || !"ARC".equalsIgnoreCase(arc.getNodeKind())
                || !java.util.Objects.equals(arc.getNodeCode(), chapterPlan.outlineNodeCode())
                || arc.getStartChapter() == null
                || arc.getEndChapter() == null
                || arc.getStartChapter() > arc.getEndChapter()
                || chapterNumber < arc.getStartChapter()
                || chapterNumber > arc.getEndChapter()) {
            throw illegalParameter(
                    "ChapterPlan.outlineNodeCode 或 chapterNumber 与当前 ARC 不一致");
        }
        if (arc.getTitle() == null || arc.getTitle().isBlank()) {
            throw illegalParameter("当前 ARC.title 缺失");
        }
        if (!java.util.Objects.equals(arc.getTitle(), chapterPlan.title())) {
            throw illegalParameter("ChapterPlan.title 与当前 ARC.title 不一致");
        }
        existing.setTitle(chapterPlan.title());
        existing.setSummary(chapterPlan.summary());
        if (chapterPlan.status() != null) {
            existing.setStatus(chapterPlan.status());
        }
        chapterPlanDao.update(existing);
        return toChapterOutline(
                project.getId(),
                chapterPlanDao.queryByChapterNumber(project.getId(), chapterNumber)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterOutlineVO upsertChapterPlan(
            String projectCode,
            ChapterOutlineVO chapterPlan
    ) {
        NovelProjectPO project = requireProject(projectCode);
        OutlineNodePO node = requireNode(project.getId(), chapterPlan.outlineNodeCode());
        ChapterPlanPO existing = chapterPlanDao.queryByChapterNumber(
                project.getId(), chapterPlan.chapterNumber()
        );
        if (existing != null && "COMPLETED".equalsIgnoreCase(existing.getStatus())) {
            throw illegalParameter("已有正文的 ChapterPlan 不能被 AI 覆盖");
        }

        if (existing == null) {
            chapterPlanDao.insert(toChapterPlanPO(
                    project.getId(), node.getId(), chapterPlan
            ));
        } else {
            existing.setOutlineNodeId(node.getId());
            existing.setChapterNumber(chapterPlan.chapterNumber());
            existing.setTitle(chapterPlan.title());
            existing.setSummary(chapterPlan.summary());
            existing.setStatus(chapterPlan.status());
            chapterPlanDao.update(existing);
        }
        return toChapterOutline(
                project.getId(),
                chapterPlanDao.queryByChapterNumber(
                        project.getId(), chapterPlan.chapterNumber()
                )
        );
    }

    public int updateChapterPlanStatus(
            String projectCode,
            Integer chapterNumber,
            String status
    ) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return 0;
        }
        ChapterPlanPO chapterPlan = chapterPlanDao.queryByChapterNumber(
                project.getId(), chapterNumber);
        return chapterPlan == null ? 0 : chapterPlanDao.updateStatus(
                project.getId(), chapterPlan.getId(), status);
    }

    private NovelProjectPO findProjectPO(String projectCode) {
        return projectDao.queryByProjectCode(projectCode);
    }

    private NovelProjectPO requireProject(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            throw illegalParameter("项目不存在，projectCode=" + projectCode);
        }
        return project;
    }

    private OutlineNodePO requireNode(Long projectId, String nodeCode) {
        OutlineNodePO node = outlineDao.queryByCode(projectId, nodeCode);
        if (node == null) {
            throw illegalParameter("大纲节点不存在，nodeCode=" + nodeCode);
        }
        return node;
    }

    private Long parentId(Long projectId, OutlineNodeVO outline) {
        return outline.parentNodeCode() == null
                ? null : requireNode(projectId, outline.parentNodeCode()).getId();
    }

    private OutlineNodePO parentNode(Long projectId, Long parentId) {
        return parentId == null ? null : outlineDao.queryById(projectId, parentId);
    }

    private boolean isUnifiedOutlineNode(OutlineNodePO node) {
        String nodeKind = node.getNodeKind();
        return "BOOK".equals(nodeKind)
                || "VOLUME".equals(nodeKind)
                || "ARC".equals(nodeKind);
    }

    private OutlineNodePO toOutlinePO(Long projectId, OutlineNodeVO value, Long parentId) {
        validateArcChapterRange(value);
        OutlineNodePO po = new OutlineNodePO();
        po.setProjectId(projectId);
        po.setParentId(parentId);
        po.setNodeCode(value.nodeCode());
        po.setNodeKind(value.nodeKind().name());
        po.setSequenceNo(value.sequenceNo());
        po.setStartChapter(value.startChapter());
        po.setEndChapter(value.endChapter());
        po.setTitle(value.title());
        po.setSummary(value.summary());
        po.setStatus(value.status());
        return po;
    }

    private void validateArcChapterRange(OutlineNodeVO value) {
        if (value != null
                && value.nodeKind() == OutlineNodeKindEnum.ARC
                && (value.startChapter() == null
                || !Objects.equals(value.startChapter(), value.endChapter()))) {
            throw illegalParameter("ARC 必须是单章，startChapter 必须等于 endChapter");
        }
    }

    private ChapterPlanPO toChapterPlanPO(
            Long projectId,
            Long outlineNodeId,
            ChapterOutlineVO value
    ) {
        ChapterPlanPO po = new ChapterPlanPO();
        po.setProjectId(projectId);
        po.setOutlineNodeId(outlineNodeId);
        po.setChapterNumber(value.chapterNumber());
        po.setTitle(value.title());
        po.setSummary(value.summary());
        po.setStatus(value.status());
        return po;
    }

    private ChapterOutlineVO toChapterOutline(Long projectId, ChapterPlanPO value) {
        OutlineNodePO outlineNode = value.getOutlineNodeId() == null
                ? null : outlineDao.queryById(projectId, value.getOutlineNodeId());
        return new ChapterOutlineVO(
                value.getChapterNumber(),
                outlineNode == null ? null : outlineNode.getNodeCode(),
                value.getTitle(),
                value.getSummary(),
                value.getStatus()
        );
    }

    @Override
    public StoryBibleVO findBible(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null || storyBibleDao == null) {
            return null;
        }
        StoryBiblePO bible = storyBibleDao.queryByProjectId(project.getId());
        return bible == null ? null : new StoryBibleVO(
                bible.getOneSentencePremise(), bible.getCoreTheme(),
                bible.getMainConflict(), bible.getEndingDirection(),
                bible.getWorldBackground(), bible.getPowerSystemJson(),
                bible.getHardRulesJson(), bible.getStyleGuide(), bible.getStatus()
        );
    }

    private StoryBibleVO toBible(StoryBiblePO bible) {
        return new StoryBibleVO(
                bible.getOneSentencePremise(), bible.getCoreTheme(),
                bible.getMainConflict(), bible.getEndingDirection(),
                bible.getWorldBackground(), bible.getPowerSystemJson(),
                bible.getHardRulesJson(), bible.getStyleGuide(), bible.getStatus()
        );
    }

    @Override
    public List<StoryCharacterVO> findCharacters(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null || storyCharacterDao == null) {
            return List.of();
        }
        return storyCharacterDao.queryByProjectId(project.getId()).stream()
                .map(this::toCharacter)
                .toList();
    }

    @Override
    public String findPreviousChapterSummary(
            String projectCode,
        Integer chapterNumber
    ) {
        if (chapterNumber == null || chapterNumber <= 1
                || (storySummaryDao == null && chapterPlanDao == null)) {
            return null;
        }
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return null;
        }
        int previousChapterNumber = chapterNumber - 1;
        if (storySummaryDao != null) {
            StorySummaryPO summary = storySummaryDao.queryByProjectIdAndChapterNumber(
                    project.getId(), previousChapterNumber
            );
            if (hasUsableStorySummary(summary)) {
                return summary.getShortSummary();
            }
        }
        if (chapterPlanDao != null) {
            ChapterPlanPO chapterPlan = chapterPlanDao.queryByChapterNumber(
                    project.getId(), previousChapterNumber
            );
            if (chapterPlan != null && hasText(chapterPlan.getSummary())) {
                return chapterPlan.getSummary();
            }
        }
        return null;
    }

    private boolean hasUsableStorySummary(StorySummaryPO summary) {
        return summary != null
                && !"STALE".equalsIgnoreCase(summary.getStatus())
                && hasText(summary.getShortSummary());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public List<ChapterMemoryVO> findRecentChapterMemories(
            String projectCode,
            Integer chapterNumber,
            int limit
    ) {
        if (chapterNumber == null || chapterNumber <= 1
                || storySummaryDao == null || limit <= 0) {
            return List.of();
        }
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return List.of();
        }
        return storySummaryDao.queryRecent(project.getId(), chapterNumber, limit).stream()
                .map(ChapterMemoryMapper::toMemory)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateLegacyBookArcs(String projectCode) {
        NovelProjectPO project = findProjectPO(projectCode);
        if (project == null) {
            return 0;
        }
        List<OutlineNodePO> nodes = outlineDao.queryByProject(project.getId());
        OutlineNodePO book = nodes.stream()
                .filter(node -> "BOOK".equals(node.getNodeKind())
                        && node.getParentId() == null)
                .findFirst()
                .orElse(null);
        if (book == null) {
            return 0;
        }

        OutlineNodePO defaultVolume = findDefaultVolume(nodes, book.getId());
        if (defaultVolume != null) {
            markVolumeNeedsPlanning(defaultVolume);
        }

        List<OutlineNodePO> legacyArcs = nodes.stream()
                .filter(node -> "ARC".equals(node.getNodeKind())
                        && Objects.equals(node.getParentId(), book.getId()))
                .toList();
        if (legacyArcs.isEmpty()) {
            return 0;
        }

        if (defaultVolume == null) {
            defaultVolume = createDefaultVolume(project.getId(), book, nodes);
        }
        int migrated = 0;
        for (OutlineNodePO arc : legacyArcs) {
            arc.setParentId(defaultVolume.getId());
            outlineDao.update(arc);
            migrated++;
        }
        return migrated;
    }

    private void markVolumeNeedsPlanning(OutlineNodePO volume) {
        if (volume.getSummary() == null || volume.getSummary().isBlank()) {
            if (!"UNPLANNED".equalsIgnoreCase(volume.getStatus())) {
                volume.setStatus("UNPLANNED");
                outlineDao.update(volume);
            }
        }
    }

    private OutlineNodePO findDefaultVolume(
            List<OutlineNodePO> nodes,
            Long bookId
    ) {
        List<OutlineNodePO> volumes = nodes.stream()
                .filter(node -> "VOLUME".equals(node.getNodeKind())
                        && Objects.equals(node.getParentId(), bookId))
                .toList();
        OutlineNodePO namedDefault = volumes.stream()
                .filter(node -> Objects.equals(node.getSequenceNo(), 1)
                        || "VOL_001".equals(node.getNodeCode())
                        || "卷一".equals(node.getTitle())
                        || "第一卷".equals(node.getTitle()))
                .findFirst()
                .orElse(null);
        return namedDefault == null && !volumes.isEmpty()
                ? volumes.get(0) : namedDefault;
    }

    private OutlineNodePO createDefaultVolume(
            Long projectId,
            OutlineNodePO book,
            List<OutlineNodePO> nodes
    ) {
        OutlineNodePO volume = new OutlineNodePO();
        volume.setProjectId(projectId);
        volume.setParentId(book.getId());
        volume.setNodeCode(nextDefaultVolumeCode(nodes));
        volume.setNodeKind("VOLUME");
        volume.setSequenceNo(1);
        volume.setStartChapter(book.getStartChapter());
        volume.setEndChapter(book.getEndChapter());
        volume.setTitle("卷一");
        volume.setSummary("");
        volume.setStatus("UNPLANNED");
        outlineDao.insert(volume);
        return volume;
    }

    private String nextDefaultVolumeCode(List<OutlineNodePO> nodes) {
        int suffix = 1;
        String candidate;
        boolean used;
        do {
            candidate = String.format("VOL_%03d", suffix++);
            String code = candidate;
            used = nodes.stream().anyMatch(node -> code.equals(node.getNodeCode()));
        } while (used);
        return candidate;
    }

    private StoryCharacterVO toCharacter(StoryCharacterPO character) {
        return new StoryCharacterVO(
                character.getCharacterCode(), character.getName(),
                character.getRoleType(), character.getGender(),
                character.getAgeDescription(), character.getAppearance(),
                character.getPersonality(), character.getBackgroundStory(),
                character.getNote(), character.getCurrentStateJson(),
                character.getLifeStatus(), character.getStatus()
        );
    }

    private OutlineNodeVO toOutline(OutlineNodePO node, OutlineNodePO parent) {
        return new OutlineNodeVO(
                node.getNodeCode(),
                parent == null ? null : parent.getNodeCode(),
                toNodeKind(node.getNodeKind()),
                node.getSequenceNo(),
                node.getTitle(),
                node.getSummary(),
                node.getStartChapter(),
                node.getEndChapter(),
                node.getStatus()
        );
    }

    private void normalizeSiblingSequences(
            List<OutlineNodePO> siblings,
            OutlineNodePO target,
            Integer requestedSequence
    ) {
        siblings.removeIf(node -> Objects.equals(node.getId(), target.getId()));
        int targetIndex = (requestedSequence == null ? siblings.size() : requestedSequence - 1);
        targetIndex = Math.max(0, Math.min(targetIndex, siblings.size()));
        siblings.add(targetIndex, target);
        for (int index = 0; index < siblings.size(); index++) {
            OutlineNodePO sibling = siblings.get(index);
            sibling.setSequenceNo(index + 1);
            outlineDao.update(sibling);
        }
    }

    private OutlineNodeKindEnum toNodeKind(String nodeKind) {
        return OutlineNodeKindEnum.valueOf(nodeKind);
    }

    private AppException illegalParameter(String message) {
        if (message != null
                && (message.contains("nodeCode")
                || message.contains("parentNodeCode")
                || message.contains("projectCode")
                || message.contains("chapterNumber")
                || message.contains("ChapterPlan")
                || message.contains("startChapter")
                || message.contains("endChapter")
                || message.contains("parent")
                || message.contains("child")
                || message.contains("structured response")
                || message.contains("结构化响应"))) {
            return AppException.internal(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
        }
        return AppException.user(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
