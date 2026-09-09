package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.infrastructure.dao.IChapterPlanDao;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IOutlineNodeDao;
import cn.ninth.novel.infrastructure.dao.po.NovelProjectPO;
import cn.ninth.novel.infrastructure.dao.po.OutlineNodePO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningRepositoryOrderingTest {

    @Test
    void shouldReorderSameParentAndNormalizeSequenceNumbers() {
        INovelProjectDao projectDao = mock(INovelProjectDao.class);
        IOutlineNodeDao outlineDao = mock(IOutlineNodeDao.class);
        IChapterPlanDao chapterPlanDao = mock(IChapterPlanDao.class);
        PlanningRepository repository = new PlanningRepository(
                projectDao, outlineDao, chapterPlanDao
        );

        NovelProjectPO project = project(1L, "novel-001");
        OutlineNodePO book = node(10L, 1L, null, "book", "BOOK", 1);
        OutlineNodePO first = node(11L, 1L, 10L, "volume-1", "VOLUME", 1);
        OutlineNodePO target = node(12L, 1L, 10L, "volume-2", "VOLUME", 2);
        OutlineNodePO third = node(13L, 1L, 10L, "volume-3", "VOLUME", 4);
        List<OutlineNodePO> siblings = new ArrayList<>(List.of(first, target, third));

        when(projectDao.queryByProjectCode("novel-001")).thenReturn(project);
        when(outlineDao.queryByCode(1L, "volume-2")).thenReturn(target);
        when(outlineDao.querySiblings(1L, 10L)).thenReturn(siblings);
        when(outlineDao.queryById(1L, 10L)).thenReturn(book);

        OutlineNodeVO updated = repository.updateOutline("novel-001", new OutlineNodeVO(
                "volume-2", "book", OutlineNodeKindEnum.VOLUME, 1,
                "第二卷", "修订后的第二卷", 1, 100, "READY"
        ));

        List<OutlineNodePO> ordered = siblings.stream()
                .sorted(Comparator.comparing(OutlineNodePO::getSequenceNo))
                .toList();
        assertThat(ordered).extracting(OutlineNodePO::getNodeCode)
                .containsExactly("volume-2", "volume-1", "volume-3");
        assertThat(ordered).extracting(OutlineNodePO::getSequenceNo)
                .containsExactly(1, 2, 3);
        assertThat(updated).extracting(OutlineNodeVO::nodeCode, OutlineNodeVO::sequenceNo)
                .containsExactly("volume-2", 1);
        assertThat(target.getTitle()).isEqualTo("第二卷");
        assertThat(target.getParentId()).isEqualTo(10L);
        verify(outlineDao, times(3)).update(any(OutlineNodePO.class));
        System.out.printf(
                "PlanningRepositoryOrderingTest parent=book order=%s sequence=%s%n",
                ordered.stream().map(OutlineNodePO::getNodeCode).toList(),
                ordered.stream().map(OutlineNodePO::getSequenceNo).toList()
        );
    }

    private NovelProjectPO project(Long id, String code) {
        NovelProjectPO project = new NovelProjectPO();
        project.setId(id);
        project.setProjectCode(code);
        project.setTitle("测试项目");
        project.setGenre("玄幻");
        project.setTargetChapterCount(100);
        project.setWordsPerChapter(2000);
        project.setCurrentChapterNumber(0);
        project.setStatus("DRAFT");
        return project;
    }

    private OutlineNodePO node(
            Long id,
            Long projectId,
            Long parentId,
            String code,
            String kind,
            int sequence
    ) {
        OutlineNodePO node = new OutlineNodePO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setParentId(parentId);
        node.setNodeCode(code);
        node.setNodeKind(kind);
        node.setSequenceNo(sequence);
        node.setStartChapter(1);
        node.setEndChapter(100);
        node.setTitle(code);
        node.setSummary(code + " summary");
        node.setStatus("PLANNED");
        return node;
    }
}
