package cn.ninth.novel.domain.chapter.service.data;


import cn.ninth.novel.domain.chapter.adapter.repository.IContextRepository;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import org.springframework.stereotype.Service;

@Service
public class ChapterContextLoader implements IDataService{

    private final IContextRepository contextRepository;

    public ChapterContextLoader(IContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @Override
    public ChapterContextAggregate loadContext(String projectCode, int chapterNumber) {

        return ChapterContextAggregate.builder()
                .project(contextRepository.loadProject(projectCode))
                .storyBible(contextRepository.loadStoryBible(projectCode))
                .chapterPlan(contextRepository.loadChapterPlan(projectCode, chapterNumber))
                .arc(contextRepository.loadCurrentArc(projectCode, chapterNumber))
                .characters(contextRepository.loadCharacters(projectCode))
                .history(contextRepository.loadHistory(projectCode,chapterNumber))
                .build();
    }
}
