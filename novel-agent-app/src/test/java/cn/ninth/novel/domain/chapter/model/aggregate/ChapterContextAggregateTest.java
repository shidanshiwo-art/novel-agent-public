package cn.ninth.novel.domain.chapter.model.aggregate;

import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChapterContextAggregateTest {

    @Test
    void shouldRejectMissingProject() {
        ChapterContextAggregate context = readyContext(1);
        context.setProject(null);

        AppException exception = assertThrows(AppException.class, context::validateReadyForGeneration);

        assertIllegalParameter(exception, "小说项目不存在");
    }

    @Test
    void shouldRejectMissingStoryBible() {
        ChapterContextAggregate context = readyContext(1);
        context.setStoryBible(null);

        AppException exception = assertThrows(AppException.class, context::validateReadyForGeneration);

        assertIllegalParameter(exception, "故事圣经不存在");
    }

    @Test
    void shouldRejectMissingChapterOutline() {
        ChapterContextAggregate context = readyContext(1);
        context.setChapterPlan(null);

        AppException exception = assertThrows(AppException.class, context::validateReadyForGeneration);

        assertIllegalParameter(exception, "章节章纲不存在");
    }

    @Test
    void shouldRejectChapterPlanWhenItDoesNotMatchCurrentArc() {
        ChapterContextAggregate missingArc = readyContext(1);
        missingArc.setArc(null);
        AppException missingArcException = assertThrows(
                AppException.class, missingArc::validateReadyForGeneration
        );
        assertIllegalParameter(missingArcException, "当前 ARC 不存在");

        ChapterContextAggregate mismatchedCode = readyContext(1);
        mismatchedCode.getChapterPlan().setOutlineNodeCode("ARC_OTHER");
        AppException codeException = assertThrows(
                AppException.class, mismatchedCode::validateReadyForGeneration
        );
        assertIllegalParameter(codeException, "ChapterPlan.outlineNodeCode 与当前 ARC 不一致");

        ChapterContextAggregate mismatchedTitle = readyContext(1);
        mismatchedTitle.getChapterPlan().setTitle("不应使用的标题");
        AppException titleException = assertThrows(
                AppException.class, mismatchedTitle::validateReadyForGeneration
        );
        assertIllegalParameter(titleException, "ChapterPlan.title 与当前 ARC.title 不一致");

        System.out.println("ChapterPlan 与 ARC 一致性校验通过：缺失 ARC、编码不一致和标题不一致均直接拒绝生成");
    }

    @Test
    void shouldAllowOnlyReadyChapterPlanAmongAllLifecycleStates() {
        assertChapterPlanStatus(null, false, "章节章纲不存在");
        assertChapterPlanStatus("PLANNED", false, "第 1 章细纲尚未确认");
        assertChapterPlanStatus("READY", true, null);
        assertChapterPlanStatus("COMPLETED", false, "第 1 章细纲尚未确认");
    }

    @Test
    void shouldAllowFirstChapterWithoutPreviousChapter() {
        ChapterContextAggregate context = readyContext(1);
        context.setHistory(null);

        assertDoesNotThrow(context::validateReadyForGeneration);
    }

    @Test
    void shouldRejectLaterChapterWithoutHistory() {
        ChapterContextAggregate context = readyContext(2);
        context.setHistory(null);

        AppException exception = assertThrows(AppException.class, context::validateReadyForGeneration);

        assertIllegalParameter(exception, "第 2 章缺少上一章历史");
    }

    @Test
    void shouldRejectLaterChapterWithoutPreviousChapter() {
        ChapterContextAggregate context = readyContext(2);
        context.setHistory(ChapterHistoryVO.builder().build());

        AppException exception = assertThrows(AppException.class, context::validateReadyForGeneration);

        assertIllegalParameter(exception, "第 2 章缺少上一章历史");
    }

    @Test
    void shouldAllowLaterChapterWithPreviousChapter() {
        ChapterContextAggregate context = readyContext(2);

        assertDoesNotThrow(context::validateReadyForGeneration);
    }

    @Test
    void shouldAllowChapterPlanInsideMultiChapterArcRange() {
        ChapterContextAggregate context = readyContext(2);
        context.setArc(new OutlineNodeVO(
                "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                "第2章", "多章 ARC", 1, 3, "READY"
        ));

        assertDoesNotThrow(context::validateReadyForGeneration);
        System.out.println("多章 ARC 范围校验通过：ChapterPlan.chapterNumber 落在 ARC 范围内即可生成");
    }

    private ChapterContextAggregate readyContext(int chapterNumber) {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("project-001").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .outlineNodeCode("ARC_001")
                        .chapterNumber(chapterNumber)
                        .title("第" + chapterNumber + "章")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                        "第" + chapterNumber + "章", "本章概要",
                        chapterNumber, chapterNumber, "READY"
                ))
                .history(ChapterHistoryVO.builder()
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(chapterNumber - 1)
                                .build())
                        .build())
                .build();
    }

    private void assertIllegalParameter(AppException exception, String message) {
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), exception.getCode());
        assertEquals(message, exception.getInfo());
    }

    private void assertChapterPlanStatus(
            String status,
            boolean allowed,
            String expectedMessage
    ) {
        ChapterContextAggregate context = readyContext(1);
        if (status == null) {
            context.setChapterPlan(null);
        } else {
            context.getChapterPlan().setStatus(status);
        }

        if (allowed) {
            assertDoesNotThrow(context::validateReadyForGeneration);
        } else {
            AppException exception = assertThrows(
                    AppException.class,
                    context::validateReadyForGeneration
            );
            assertIllegalParameter(exception, expectedMessage);
        }
        System.out.printf(
                "章节计划生成准入：status=%s, allowed=%s%n",
                status == null ? "无计划" : status,
                allowed
        );
    }
}
