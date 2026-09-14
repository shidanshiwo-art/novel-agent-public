package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.service.MemoryCommitGate;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.IStoryChapterDao;
import cn.ninth.novel.infrastructure.dao.IStorySummaryDao;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.ChapterPlanPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import cn.ninth.novel.infrastructure.dao.po.StoryChapterPO;
import cn.ninth.novel.infrastructure.dao.po.StorySummaryPO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 基于 MVP 七表模型的章节生成结果仓储。
 */
@Repository
public class ChapterPersistRepository implements IChapterPersistRepository {

    private final INovelProjectDao novelProjectDao;
    private final IChapterPlanDao chapterPlanDao;
    private final IOutlineNodeDao outlineNodeDao;
    private final IStoryChapterDao storyChapterDao;
    private final IStorySummaryDao storySummaryDao;
    private final ObjectMapper objectMapper;
    private final MemoryCommitGate memoryCommitGate;

    public ChapterPersistRepository(
            INovelProjectDao novelProjectDao,
            IChapterPlanDao chapterPlanDao,
            IOutlineNodeDao outlineNodeDao,
            IStoryChapterDao storyChapterDao,
            IStorySummaryDao storySummaryDao,
            ObjectMapper objectMapper,
            MemoryCommitGate memoryCommitGate
    ) {
        this.novelProjectDao = novelProjectDao;
        this.chapterPlanDao = chapterPlanDao;
        this.outlineNodeDao = outlineNodeDao;
        this.storyChapterDao = storyChapterDao;
        this.storySummaryDao = storySummaryDao;
        this.objectMapper = objectMapper;
        this.memoryCommitGate = memoryCommitGate;
    }

    /**
     * 保存正文及其派生数据，并在同一个事务中推进章节计划状态。
     * 任一写入步骤或状态推进失败，正文、派生数据和状态变化一并回滚。
     */
    @Override
    public void persist(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory
    ) {
        persist(projectCode, chapterNumber, content, chapterMemory, StoryStateSnapshot.empty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persist(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot
    ) {
        persistChapterInTransaction(
                projectCode, chapterNumber, content, chapterMemory, storyStateSnapshot);
    }

    /**
     * 章节正文与 Canonical Gate 共用一个 MySQL 业务事务。
     * Gate 失败时不会留下正文、Canonical、Fact 状态或 outbox。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistWithMemoryCommit(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot,
            MemoryCommitRequest memoryCommitRequest
    ) {
        if (memoryCommitRequest == null) {
            throw illegalParameter("PERSIST 缺少 MemoryCommitRequest");
        }
        if (!projectCode.equals(memoryCommitRequest.projectCode())
                || chapterNumber != memoryCommitRequest.chapterNumber()
                || !content.equals(memoryCommitRequest.finalContent())) {
            throw illegalParameter("MemoryCommitRequest 与最终章节正文不一致");
        }
        memoryCommitGate.commit(memoryCommitRequest);
        persistChapterInTransaction(
                projectCode, chapterNumber, content, chapterMemory, storyStateSnapshot);
    }

    private void persistChapterInTransaction(
            String projectCode,
            int chapterNumber,
            String content,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot
    ) {
        NovelProjectPO project = requireProject(projectCode);
        ChapterPlanPO chapterPlan = requireReadyChapterPlan(project.getId(), chapterNumber);
        requireMemory(chapterMemory);
        String arcTitle = requireArcTitle(project.getId(), chapterPlan, chapterNumber);
        StoryChapterPO chapter = toChapter(
                project.getId(), chapterPlan, arcTitle, chapterNumber, content);
        storyChapterDao.insertOrUpdate(chapter);

        StorySummaryPO summaryPO = toSummary(
                project.getId(), chapter.getId(), chapterNumber,
                chapterMemory, storyStateSnapshot);
        storySummaryDao.insertOrUpdate(summaryPO);

        novelProjectDao.advanceProgress(project.getId(), chapterNumber);
        int updatedPlans = chapterPlanDao.updateStatusIfCurrent(
                project.getId(), chapterPlan.getId(), "READY", "COMPLETED");
        if (updatedPlans != 1) {
            throw illegalParameter(
                    "PERSIST 章节计划状态更新失败，chapterNumber=" + chapterNumber);
        }
    }

    /**
     * 重新压缩并替换人工修改章节的派生数据。
     * 正文和章节计划保持不变，所有派生数据写入成功后才恢复章节状态。
     */
    @Override
    public void persistDerivedData(
            String projectCode,
            int chapterNumber,
            ChapterMemoryVO chapterMemory
    ) {
        persistDerivedData(
                projectCode, chapterNumber, chapterMemory, StoryStateSnapshot.empty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistDerivedData(
            String projectCode,
            int chapterNumber,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot
    ) {
        NovelProjectPO project = requireProject(projectCode);
        StoryChapterPO chapter = requireDirtyChapter(project.getId(), chapterNumber);
        requireMemory(chapterMemory);

        storySummaryDao.insertOrUpdate(toSummary(
                project.getId(), chapter.getId(), chapterNumber,
                chapterMemory, storyStateSnapshot));
        int updatedChapters = storyChapterDao.updateStatusIfCurrent(
                project.getId(), chapterNumber, "DIRTY", "FINALIZED");
        if (updatedChapters != 1) {
            throw illegalParameter(
                    "派生数据同步时章节状态更新失败，chapterNumber=" + chapterNumber);
        }
    }

    private NovelProjectPO requireProject(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            throw illegalParameter("PERSIST 缺少 projectCode");
        }
        NovelProjectPO project = novelProjectDao.queryByProjectCode(projectCode);
        if (project == null) {
            throw illegalParameter("PERSIST 对应的小说项目不存在，projectCode=" + projectCode);
        }
        return project;
    }

    private ChapterPlanPO requireReadyChapterPlan(Long projectId, int chapterNumber) {
        ChapterPlanPO chapterPlan = chapterPlanDao.queryByChapterNumber(projectId, chapterNumber);
        if (chapterPlan == null) {
            throw illegalParameter("PERSIST 对应的章节计划不存在，chapterNumber=" + chapterNumber);
        }
        if (!"READY".equals(chapterPlan.getStatus())) {
            throw illegalParameter(
                    "PERSIST 章节计划未处于 READY 状态，chapterNumber=" + chapterNumber
                            + "，status=" + chapterPlan.getStatus());
        }
        return chapterPlan;
    }

    private StoryChapterPO requireDirtyChapter(Long projectId, int chapterNumber) {
        StoryChapterPO chapter = storyChapterDao
                .queryByProjectIdAndChapterNumber(projectId, chapterNumber);
        if (chapter == null) {
            throw illegalParameter("派生数据同步对应的章节不存在，chapterNumber=" + chapterNumber);
        }
        if (!"DIRTY".equalsIgnoreCase(chapter.getStatus())) {
            throw illegalParameter(
                    "派生数据同步仅允许 DIRTY 章节，chapterNumber=" + chapterNumber
                            + "，status=" + chapter.getStatus());
        }
        return chapter;
    }

    private ChapterMemoryVO requireMemory(ChapterMemoryVO chapterMemory) {
        if (chapterMemory == null) {
            throw illegalParameter("PERSIST 缺少章节记忆");
        }
        if (chapterMemory.getShortSummary() == null
                || chapterMemory.getShortSummary().isBlank()) {
            throw illegalParameter("PERSIST 章节摘要内容为空");
        }
        return chapterMemory;
    }

    private StoryChapterPO toChapter(
            Long projectId,
            ChapterPlanPO chapterPlan,
            String arcTitle,
            int chapterNumber,
            String content
    ) {
        if (content == null || content.isBlank()) {
            throw illegalParameter("PERSIST 章节正文为空");
        }
        StoryChapterPO chapter = new StoryChapterPO();
        chapter.setProjectId(projectId);
        chapter.setChapterPlanId(chapterPlan.getId());
        chapter.setChapterNumber(chapterNumber);
        chapter.setTitle(arcTitle);
        chapter.setContent(content);
        chapter.setWordCount(countNonWhitespaceCodePoints(content));
        chapter.setStatus("FINALIZED");
        return chapter;
    }

    private String requireArcTitle(
            Long projectId,
            ChapterPlanPO chapterPlan,
            int chapterNumber
    ) {
        if (chapterPlan.getOutlineNodeId() == null) {
            throw illegalParameter(
                    "PERSIST ChapterPlan.outlineNodeCode 缺失，chapterNumber=" + chapterNumber);
        }
        OutlineNodePO arc = outlineNodeDao.queryById(
                projectId, chapterPlan.getOutlineNodeId());
        if (arc == null || !"ARC".equalsIgnoreCase(arc.getNodeKind())) {
            throw illegalParameter(
                    "PERSIST 当前 ARC 不存在或类型无效，chapterNumber=" + chapterNumber);
        }
        if (arc.getStartChapter() == null
                || arc.getEndChapter() == null
                || chapterNumber < arc.getStartChapter()
                || chapterNumber > arc.getEndChapter()) {
            throw illegalParameter(
                    "PERSIST ChapterPlan.chapterNumber 与当前 ARC 不一致，chapterNumber="
                            + chapterNumber);
        }
        if (arc.getTitle() == null || arc.getTitle().isBlank()) {
            throw illegalParameter(
                    "PERSIST 当前 ARC.title 缺失，chapterNumber=" + chapterNumber);
        }
        if (!java.util.Objects.equals(chapterPlan.getTitle(), arc.getTitle())) {
            throw illegalParameter(
                    "PERSIST ChapterPlan.title 与当前 ARC.title 不一致，chapterNumber="
                            + chapterNumber);
        }
        return arc.getTitle();
    }

    private StorySummaryPO toSummary(
            Long projectId,
            Long chapterId,
            int chapterNumber,
            ChapterMemoryVO chapterMemory,
            StoryStateSnapshot storyStateSnapshot
    ) {
        ChapterMemoryVO memory = requireMemory(chapterMemory);
        StorySummaryPO summaryPO = new StorySummaryPO();
        summaryPO.setProjectId(projectId);
        summaryPO.setChapterId(chapterId);
        summaryPO.setChapterNumber(chapterNumber);
        summaryPO.setShortSummary(memory.getShortSummary());
        summaryPO.setKeyEventsJson(serializeStringList(memory.getKeyEvents()));
        summaryPO.setUnresolvedQuestionsJson(serializeStringList(memory.getUnresolved()));
        summaryPO.setEndingHook(memory.getEndingHook());
        summaryPO.setStoryStateSnapshotJson(serializeStoryStateSnapshot(storyStateSnapshot));
        summaryPO.setStatus("GENERATED");
        return summaryPO;
    }

    private String serializeStoryStateSnapshot(StoryStateSnapshot storyStateSnapshot) {
        return objectMapper.writeValueAsString(
                storyStateSnapshot == null ? StoryStateSnapshot.empty() : storyStateSnapshot);
    }

    private String serializeStringList(List<String> values) {
        return objectMapper.writeValueAsString(values == null ? List.of() : values);
    }

    private int countNonWhitespaceCodePoints(String content) {
        return Math.toIntExact(content.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count());
    }

    private AppException illegalParameter(String detail) {
        return AppException.internal(
                ResponseCode.ILLEGAL_PARAMETER.getCode(), detail);
    }
}
