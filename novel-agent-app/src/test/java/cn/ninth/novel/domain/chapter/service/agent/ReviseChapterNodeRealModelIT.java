package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVISER_SYSTEM_PROMPT;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ReviseChapterNodeRealModelIT {

    @Autowired
    private ReviseChapterNode reviseChapterNode;

    @Test
    void shouldPrintRevisePromptAndRealModelOutput() {
        ChapterContextAggregate context = buildContext();
        String draft = "客栈老板跨上最后一级台阶，伸手去夺林澈指间的染血船票。林澈运起凝气境真气，隔空将老板震下楼梯。佩剑撞上栏杆，剑鞘内侧露出一道与船票仓印相同的‘柒’字刻痕。老板伏在楼梯转角，惊惧地盯着那两个‘柒’字，终于承认船票属于七年前停航的渡船，登船处正是旧渡口柒号仓。林澈正要追问兄长的下落，紧闭的大门外忽然响起三短一长的敲门声——那是兄长惯用的节奏。";
        ReviewReportVO reviewReport = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.BLOCKER)
                        .category("世界规则")
                        .description("主角使用了当前境界无法掌握的能力。")
                        .evidence("林澈运起凝气境真气，隔空将老板震下楼梯。")
                        .build()))
                .build();
        String userPrompt = reviseChapterNode.buildRevisePrompt(context, draft, reviewReport);

        System.out.println("\n========== CHAPTER REVISE SYSTEM PROMPT ==========");
        System.out.println(REVISER_SYSTEM_PROMPT);
        System.out.println("========== CHAPTER REVISE USER PROMPT ==========");
        System.out.println(userPrompt);

        Map<String, Object> update = reviseChapterNode.apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.DRAFT, draft,
                ChapterGraphKeys.REVIEW_REPORT, reviewReport
        )));
        String revisedDraft = (String) update.get(ChapterGraphKeys.DRAFT);

        System.out.println("========== CHAPTER REVISE OUTPUT ==========");
        System.out.println(revisedDraft);
        System.out.println("========== END CHAPTER REVISE OUTPUT ==========\n");
    }

    private ChapterContextAggregate buildContext() {
        return ChapterContextAggregate.builder()
                .storyBible(StoryBibleEntity.builder()
                        .hardRulesJson("[\"死者不能复生\",\"主角当前只能使用锻体境能力\"]")
                        .styleGuide("第三人称限知，语言克制冷峻。")
                        .build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(2)
                        .title("旧渡口")
                        .summary("确认染血船票指向旧渡口。")
                        .status("READY")
                        .build())
                .characters(List.of(StoryCharacterEntity.builder()
                        .characterCode("CHAR_001")
                        .name("林澈")
                        .roleType("主角")
                        .gender("男")
                        .ageDescription("二十七岁")
                        .appearance("眉眼清冷，常穿旧青衫")
                        .personality("谨慎坚韧")
                        .backgroundStory("曾在雾港长大")
                        .note("保持其克制的说话方式")
                        .build()))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                1,
                                "林澈发现兄长留下的佩剑和染血船票。",
                                List.of("确认兄长近期到过雾港。", "暴露了自己的调查。"),
                                List.of("兄长为何使用林澈的旧名"),
                                "船票背面留有来历不明的‘柒’字仓印。")))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(1)
                                .title("雨夜遗剑")
                                .content("雨水敲打着客栈窗纸。林澈从兄长佩剑的剑鞘夹层抽出一张染血船票，票面写着他的旧名，背面还盖着一个模糊的‘柒’字仓印。楼下传来门闩落下的声音。客栈老板站在楼梯口，死死盯着船票，压低声音道：‘把剑和船票给我。’说完，他扶着栏杆，一步步向楼上逼近。")
                                .wordCount(120)
                                .status("FINALIZED")
                                .build())
                        .build())
                .build();
    }

}
