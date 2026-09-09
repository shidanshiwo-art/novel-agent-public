package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.repository.IChapterPersistRepository;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * PERSIST 章节生成结果持久化节点。
 */
@Component
public class PersistChapterNode implements NodeAction<ChapterGraphState> {

    private static final String NODE_NAME = "PERSIST";

    private final IChapterPersistRepository chapterPersistRepository;

    public PersistChapterNode(IChapterPersistRepository chapterPersistRepository) {
        this.chapterPersistRepository = chapterPersistRepository;
    }

    @Override
    public Map<String, Object> apply(ChapterGraphState state) {
        String projectCode = state.projectCode()
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 projectCode"));
        int chapterNumber = state.chapterNumber()
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 chapterNumber"));
        String content = state.draft()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 draft"));
        ChapterMemoryVO chapterMemory = state.chapterMemory()
                .orElseThrow(() -> illegalParameter("PERSIST 节点缺少 chapterMemory"));
        StoryStateSnapshot storyStateSnapshot = state.storyStateSnapshot()
                .orElseGet(StoryStateSnapshot::empty);

        chapterPersistRepository.persist(
                projectCode, chapterNumber, content, chapterMemory, storyStateSnapshot);

        return Map.of(
                ChapterGraphKeys.CURRENT_NODE, NODE_NAME,
                ChapterGraphKeys.COMPLETED_STAGES, List.of(NODE_NAME)
        );
    }

    private AppException illegalParameter(String detail) {
        return AppException.internal(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                detail
        );
    }
}
