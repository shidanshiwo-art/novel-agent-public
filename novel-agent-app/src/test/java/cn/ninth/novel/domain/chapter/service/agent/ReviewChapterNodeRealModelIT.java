package cn.ninth.novel.domain.chapter.service.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ninth.novel.Application;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVIEW_SYSTEM_PROMPT;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class ReviewChapterNodeRealModelIT {

    @Autowired
    private ReviewChapterNode reviewChapterNode;

    @Test
    void shouldPrintReviewPromptAndRealModelOutput() {
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder()
                        .projectCode("review-real-model")
                        .wordsPerChapter(2500)
                        .build())
                .storyBible(StoryBibleEntity.builder()
                        .hardRulesJson("[\"死者不能复生\",\"主角当前只能使用锻体境能力\"]")
                        .powerSystemJson("{\"锻体境\":\"只能强化肉身\",\"凝气境\":\"可外放真气\"}")
                        .worldBackground("雾港的旧渡船已停航七年。")
                        .styleGuide("第三人称限知，语言克制冷峻。")
                        .build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .outlineNodeCode("ARC_001")
                        .chapterNumber(2)
                        .title("旧渡口")
                        .summary("确认染血船票指向旧渡口。")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                        "旧渡口", "确认染血船票指向旧渡口。", 1, 60, "READY"
                ))
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
                        .build()))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                1,
                                "林澈发现兄长留下的佩剑和染血船票。",
                                List.of("确认兄长近期到过雾港。", "暴露了自己的调查。"),
                                List.of("兄长为何使用林澈的旧名"),
                                "染血船票指向旧渡口。")))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(1)
                                .title("雨夜遗剑")
                                .content("林澈从剑鞘夹层抽出一张染血船票。")
                                .build())
                        .build())
                .build();
        String draft = """
                雾港入夜后，潮声会沿着废弃的石阶爬进城里。林澈在旧渡口外停下脚步，手里的染血船票已经被雨水泡得发软，票角却始终指向同一个名字：归潮号。那艘船七年前失踪，连同船上三十七名乘客一起，从所有人的记忆里被抹去了。

                他没有立刻走下堤岸。旧渡口的木桩上挂着一盏没有火焰的铜灯，灯罩里积着黑色的潮泥。根据兄长留下的字条，午夜前铜灯会亮一次，亮起时只能有一个人站在渡船上。林澈摸了摸袖中的短剑，确认剑鞘夹层里仍藏着那枚旧城区的铜钱。

                风从河面横扫过来，雨点打在石板上，像无数细小的脚步。林澈听见身后有人踩过积水，回头时只看见一柄黑伞停在巷口。伞下的人没有追来，也没有离开，只把一张新的船票压在墙角的石缝里。那张票的日期是七年前，收票人一栏写着他的旧名。

                林澈走过去时，黑伞已经消失。船票背面留着一行字：不要相信第一个替你点灯的人。他把字迹和兄长的笔迹反复比对，发现最后一笔的收锋完全相同。可兄长失踪之后，所有相关的纸张都被监察使府收走，连这张船票也不该出现在这里。

                铜灯忽然亮了。火焰呈现出浑浊的青色，照见河心一段本不该存在的木桥。桥面尽头停着归潮号，船身没有船名，湿漉漉的缆绳却像刚刚被人解开。林澈踏上第一块木板，脚下传来沉闷的回声，仿佛桥底有人用指节敲击。

                他想起故事圣经里那条不能违反的规则：死者不能复生。河面上的人影却抬起了头，露出兄长七年前离开时的侧脸。林澈没有喊出声，只把短剑横在胸前。他知道自己看见的未必是活人，更可能是钟楼回声借来的形状。

                木桥在第二步之后开始结霜。林澈本来只能使用锻体境的力量，不能像传闻中的高阶修行者那样外放真气；可当河水撞上桥墩时，一股陌生的热流仍从他的掌心涌出，把面前的霜层震出裂纹。他立即收回手，掌心留下了一道细小的黑线，像有人在皮下写了一个字。

                船舱里传来铃声。林澈推门进去，迎面看见三十七盏纸灯，每盏灯下都压着一张写有人名的船票。最后一盏灯没有名字，只有一枚铜钱。那枚铜钱和他袖中的铜钱严丝合缝，拼起来后，背面显出旧城区钟楼的刻痕。

                船尾传来脚步声。一个穿灰色长衫的少年从雨幕里走来，自称是替监察使送信的人。他说兄长没有失踪，只是被请去回答几个问题；他说归潮号今晚只载活人；他说如果林澈愿意把铜钱交出来，便能换回一段被夺走的记忆。每句话听起来都像真话，偏偏彼此之间没有一处能够同时成立。

                林澈没有把铜钱交给他。他问少年，为什么船票上的收票人写的是自己的旧名。少年笑了一下，说旧名不是名字，而是一把钥匙。下一刻，船舱里三十七盏纸灯同时熄灭，黑暗中有人贴着他的耳边说，兄长正在河底等他。

                林澈拔剑斩向声音传来的方向，剑锋只切开一片潮湿的黑布。黑布落地后，露出一枚监察使府的银印。河面上的归潮号开始后退，木桥一节一节沉入水中。林澈抓住船舷，听见船底传来许多人的低语，他们重复着同一句话：钟楼今晚已经响过一次。

                铜灯的青火在最后一刻照亮了船舱。墙上浮出一行用水写成的字：下一个被抹去的人，是林澈。随后归潮号沉入雾里，旧渡口只剩一张湿透的船票。林澈回到岸上时，黑伞人站在巷口，手里握着那枚与铜钱相同的旧城区钥匙。他没有追问船上的所见，只说监察使府已经知道他来过这里。

                林澈把船票收进衣襟，转身走向雾港城。身后的河面恢复平静，只有一声迟到的钟响穿过夜雨。那声音之后，街上所有写着他名字的招牌同时变成了空白。
                """;

        String userPrompt = reviewChapterNode.buildReviewPrompt(context, draft);

        System.out.println("\n========== CHAPTER REVIEW SYSTEM PROMPT ==========");
        System.out.println(REVIEW_SYSTEM_PROMPT);
        System.out.println("========== CHAPTER REVIEW USER PROMPT ==========");
        System.out.println(userPrompt);

        Logger logger = (Logger) LoggerFactory.getLogger(
                cn.ninth.novel.infrastructure.adapter.port.ChapterModelPort.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ReviewReportVO reviewReport;
        try {
            Map<String, Object> update = reviewChapterNode.apply(new ChapterGraphState(Map.of(
                    ChapterGraphKeys.CONTEXT, context,
                    ChapterGraphKeys.DRAFT, draft,
                    ChapterGraphKeys.WORKFLOW_ID, "review-real-model",
                    ChapterGraphKeys.PROJECT_CODE, "review-real-model",
                    ChapterGraphKeys.CHAPTER_NUMBER, 2
            )));
            reviewReport = (ReviewReportVO) update.get(ChapterGraphKeys.REVIEW_REPORT);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        System.out.println("========== CHAPTER REVIEW OUTPUT ==========");
        System.out.println(reviewReport);
        appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("status=success mode=structured stage=REVIEW"))
                .reduce((first, second) -> second)
                .ifPresent(successLog -> {
                    System.out.printf(
                            "REVIEW metrics: firstResponseMs=%s firstContentMs=%s generationMs=%s totalCostMs=%s completionTokens=%s reasoning=%s%n",
                            metric(successLog, "firstResponseMs"),
                            metric(successLog, "firstContentMs"),
                            metric(successLog, "generationMs"),
                            metric(successLog, "totalCostMs"),
                            metric(successLog, "completionTokens"),
                            metricText(successLog, "reasoning")
                    );
                    System.out.println(successLog);
                });
        System.out.println("========== END CHAPTER REVIEW OUTPUT ==========\n");
    }

    private static String metric(String log, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=(\\d+)(?=\\s|$)")
                .matcher(log);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String metricText(String log, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "=([^\\s]+)")
                .matcher(log);
        return matcher.find() ? matcher.group(1) : null;
    }
}
