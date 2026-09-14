package cn.ninth.novel.domain.chapter.service.data;

import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.memory.model.MemoryMode;

/**
 * 章节结构化数据服务接口。
 * 负责按照项目和章节维度加载后续 Agent 所需的数据，并统一返回章节上下文聚合。
 */
public interface IDataService {

    /**
     * 加载指定项目和章节的结构化上下文。
     *
     * @param projectCode 项目业务编码
     * @param chapterNumber 待生成章节号
     * @return 包含项目、故事圣经、章节计划、人物和历史数据的章节上下文聚合
     */
    ChapterContextAggregate loadContext(String projectCode, int chapterNumber);

    /**
     * 按本次生成的 Memory mode 加载上下文；旧实现默认沿用兼容入口。
     */
    default ChapterContextAggregate loadContext(
            String projectCode,
            int chapterNumber,
            MemoryMode memoryMode
    ) {
        return loadContext(projectCode, chapterNumber);
    }

    /** 按本次生成的 Memory mode 和 generation/run 关联加载上下文。 */
    default ChapterContextAggregate loadContext(
            String projectCode,
            int chapterNumber,
            MemoryMode memoryMode,
            String generationId
    ) {
        return loadContext(projectCode, chapterNumber, memoryMode);
    }
}
