package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.DRAFT_SYSTEM_PROMPT;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class DraftChapterNodeRealModelIT {

    @Autowired
    private DraftChapterNode draftChapterNode;

    @Test
    void shouldPrintDraftPromptAndRealModelOutput() {
        ChapterContextAggregate context = buildContext();
        String userPrompt = draftChapterNode.buildDraftUserPrompt(context);

        System.out.println("\n========== CHAPTER DRAFT SYSTEM PROMPT ==========");
        System.out.println(DRAFT_SYSTEM_PROMPT);
        System.out.println("========== CHAPTER DRAFT USER PROMPT ==========");
        System.out.println(userPrompt);

        Map<String, Object> update = draftChapterNode.apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTEXT, context
        )));
        String draft = (String) update.get(ChapterGraphKeys.DRAFT);

        System.out.println("========== CHAPTER DRAFT OUTPUT ==========");
        System.out.println(draft);
        System.out.println("========== END CHAPTER DRAFT OUTPUT ==========\n");
    }

    private ChapterContextAggregate buildContext() {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder()
                        .projectCode("drafter-it-001")
                        .title("雾港遗剑")
                        .genre("东方悬疑")
                        .targetChapterCount(80)
                        .wordsPerChapter(800)
                        .currentChapterNumber(2)
                        .status("ACTIVE")
                        .build())
                .storyBible(StoryBibleEntity.builder()
                        .oneSentencePremise("失忆剑客在封闭港城追查兄长失踪真相。")
                        .coreTheme("记忆与身份是否决定一个人")
                        .mainConflict("主角追查真相的行动与港城守密势力发生冲突。")
                        .endingDirection("主角找回记忆，并选择公开足以改变港城秩序的真相。")
                        .worldBackground("港城终年被浓雾笼罩，入夜后所有渡口关闭。")
                        .powerSystemJson("{\"境界\":[\"锻体\",\"凝气\",\"御剑\"]}")
                        .hardRulesJson("[\"死者不能复生\",\"主角当前只能使用锻体境能力\"]")
                        .styleGuide("第三人称限知，语言克制冷峻；以行动和细节推动悬疑，不直接解释谜底。")
                        .status("ACTIVE")
                        .build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .chapterNumber(2)
                        .title("旧渡口")
                        .summary("林澈根据染血船票追查已经废弃的旧渡口。")
                        .status("READY")
                        .build())
                .characters(List.of(StoryCharacterEntity.builder()
                        .characterCode("CHAR_001")
                        .name("林澈")
                        .roleType("MALE_LEAD")
                        .gender("男")
                        .ageDescription("二十七岁")
                        .appearance("眉眼清冷，常穿旧青衫")
                        .personality("克制、警惕，对亲人极为执着")
                        .backgroundStory("曾在雾港长大，后来成为失忆剑客")
                        .note("说话简短，很少解释")
                        .status("ACTIVE")
                        .build()))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                1,
                                "林澈在雨夜客栈发现失踪兄长的佩剑。",
                                List.of("确认兄长曾在近期到过雾港客栈。",
                                        "暴露了自己正在追查兄长。"),
                                List.of("兄长为何使用林澈的旧名购买船票"),
                                "佩剑夹层中掉出一张写有林澈旧名的染血船票。")))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(1)
                                .title("雨夜遗剑")
                                .content("雨水敲打着客栈窗纸。林澈从剑鞘夹层抽出染血船票，票面上的旧名让他短暂失神。楼下忽然传来门闩落下的声音，客栈老板站在楼梯口，盯着他手中的佩剑。")
                                .wordCount(75)
                                .status("FINALIZED")
                                .build())
                        .build())
                .build();
    }

}
