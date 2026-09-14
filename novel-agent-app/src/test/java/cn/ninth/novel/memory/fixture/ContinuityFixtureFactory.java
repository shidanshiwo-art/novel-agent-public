package cn.ninth.novel.memory.fixture;

import java.util.List;

/**
 * Creates the fixed chapters 5-10 continuity regression corpus.
 */
public final class ContinuityFixtureFactory {

    public static final int FIRST_CHAPTER = 5;
    public static final int LAST_CHAPTER = 10;
    public static final String WRONG_CHAPTER_9_VERSION = "chapter-09-v1-draft";
    public static final String FINAL_CHAPTER_9_VERSION = "chapter-09-v2-revised";
    public static final String WRONG_CHAPTER_9_CANDIDATE = "continuity-candidate-ch9-v1-wrong";
    public static final String FINAL_CHAPTER_9_CANDIDATE = "continuity-candidate-ch9-v2-final";

    private ContinuityFixtureFactory() {
    }

    public static ContinuityFixture create() {
        String chapterFiveContent = "第5章清晨，沈夜在旧钟楼地下室找到黑色残晶，并将它留在地下室的铁匣中。";
        String chapterSixContent = "第6章午后，陆遥翻阅古籍《镇印残卷》，第一次从书中确认封印确实存在。";
        String chapterSevenContent = "第7章傍晚，沈夜把黑色残晶从地下室铁匣转移到北上的马车暗格；他胸口的伤口仍未愈合。";
        String chapterEightContent = "第8章深夜，沈夜主动激活回声感知，消耗一枚灵息；陆遥再次从古籍记载确认残晶与封印同源。";
        String wrongChapterNineContent = "第9章夜里，错误草稿写成黑色残晶仍在旧钟楼地下室，并声称陆遥从沈夜口中得知封印。";
        String finalChapterNineContent = "第9章夜里，修订正文确认黑色残晶仍在北上马车暗格，陆遥的依据仍是《镇印残卷》；两人第三次确认残晶与封印同源。";
        String chapterTenContent = "第10章次日拂晓，马车继续北上；沈夜胸口伤势未愈，灵息尚未恢复，陆遥第四次确认残晶与封印同源。";

        String wrongChapterNineHash = FixtureHashing.sha256(wrongChapterNineContent);
        String finalChapterNineHash = FixtureHashing.sha256(finalChapterNineContent);

        FixtureCandidate wrongCandidate = new FixtureCandidate(
                WRONG_CHAPTER_9_CANDIDATE,
                9,
                WRONG_CHAPTER_9_VERSION,
                wrongChapterNineHash,
                "正文第1句～第2句",
                FixtureCandidateType.STATE_CHANGE,
                FixtureCandidateStatus.STALE,
                "黑色残晶位于旧钟楼地下室，陆遥从沈夜处得知封印存在",
                "黑色残晶仍在旧钟楼地下室，并声称陆遥从沈夜口中得知封印",
                null);
        FixtureCandidate finalCandidate = new FixtureCandidate(
                FINAL_CHAPTER_9_CANDIDATE,
                9,
                FINAL_CHAPTER_9_VERSION,
                finalChapterNineHash,
                "正文第1句～第2句",
                FixtureCandidateType.STATE_CHANGE,
                FixtureCandidateStatus.READY_FOR_GATE,
                "黑色残晶位于北上马车暗格，陆遥从古籍《镇印残卷》得知封印存在",
                "黑色残晶仍在北上马车暗格，陆遥的依据仍是《镇印残卷》",
                WRONG_CHAPTER_9_CANDIDATE);

        FixtureChapter chapterFive = chapter(
                5,
                "地下室残晶",
                "第03日 06:00",
                "chapter-05-final",
                chapterFiveContent,
                List.of(
                        item("continuity-c5-find-crystal", FixtureMemoryKind.EVENT, FixtureLifecycle.ACTIVE,
                                5, "chapter-05-final", "第03日 06:00", "沈夜",
                                "沈夜在旧钟楼地下室发现黑色残晶。", true),
                        item("continuity-c5-crystal-basement", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                5, "chapter-05-final", "第03日 06:00", "黑色残晶",
                                "黑色残晶位于旧钟楼地下室的铁匣中。", true),
                        item("continuity-loop-crystal-origin", FixtureMemoryKind.OPEN_LOOP, FixtureLifecycle.ACTIVE,
                                5, "chapter-05-final", "第03日 06:00", "黑色残晶",
                                "黑色残晶的来源与封印关系尚未揭示。", true)),
                List.of());

        FixtureChapter chapterSix = chapter(
                6,
                "古籍中的封印",
                "第03日 12:00",
                "chapter-06-final",
                chapterSixContent,
                List.of(
                        item("continuity-c6-book-source", FixtureMemoryKind.EVENT, FixtureLifecycle.ACTIVE,
                                6, "chapter-06-final", "第03日 12:00", "陆遥",
                                "陆遥查阅古籍《镇印残卷》。", true),
                        item("continuity-c6-luyao-knowledge", FixtureMemoryKind.CHARACTER_KNOWLEDGE, FixtureLifecycle.ACTIVE,
                                6, "chapter-06-final", "第03日 12:00", "陆遥",
                                "陆遥知道封印确实存在，知识来源是古籍《镇印残卷》，不是沈夜的转述。", true)),
                List.of());

        FixtureChapter chapterSeven = chapter(
                7,
                "转移到马车",
                "第04日 18:00",
                "chapter-07-final",
                chapterSevenContent,
                List.of(
                        item("continuity-c7-move-crystal", FixtureMemoryKind.EVENT, FixtureLifecycle.ACTIVE,
                                7, "chapter-07-final", "第04日 18:00", "黑色残晶",
                                "黑色残晶从旧钟楼地下室铁匣转移到北上马车暗格。", true),
                        item("continuity-c7-crystal-carriage", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                7, "chapter-07-final", "第04日 18:00", "黑色残晶",
                                "黑色残晶当前位于北上马车暗格；地下室位置已被新位置取代。", true),
                        item("continuity-c7-injury", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                7, "chapter-07-final", "第04日 18:00", "沈夜",
                                "沈夜胸口伤势未愈，行动受限。", true),
                        item("continuity-c7-ability-rule", FixtureMemoryKind.WORLD_RULE, FixtureLifecycle.ACTIVE,
                                7, "chapter-07-final", "第04日 18:00", "回声感知",
                                "回声感知必须主动激活；每次激活消耗一枚灵息，灵息未恢复时不能再次激活。", true)),
                List.of());

        FixtureChapter chapterEight = chapter(
                8,
                "回声感知",
                "第05日 22:00",
                "chapter-08-final",
                chapterEightContent,
                List.of(
                        item("continuity-c8-activate-ability", FixtureMemoryKind.EVENT, FixtureLifecycle.ACTIVE,
                                8, "chapter-08-final", "第05日 22:00", "沈夜",
                                "沈夜主动激活回声感知，实际消耗一枚灵息。", true),
                        item("continuity-c8-ability-consumed", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                8, "chapter-08-final", "第05日 22:00", "回声感知",
                                "回声感知本次已主动使用并消耗一枚灵息；没有凭空恢复。", true),
                        item("continuity-c8-injury-continues", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                8, "chapter-08-final", "第05日 22:00", "沈夜",
                                "沈夜胸口伤势继续存在，未因章节切换自动痊愈。", true),
                        item("continuity-c8-same-origin-confirmed", FixtureMemoryKind.FACT, FixtureLifecycle.ACTIVE,
                                8, "chapter-08-final", "第05日 22:00", "黑色残晶与封印",
                                "陆遥依据古籍第一次确认黑色残晶与封印同源。", true)),
                List.of());

        FixtureChapter chapterNine = new FixtureChapter(
                9,
                "修订后的马车夜谈",
                List.of(
                        version(
                                WRONG_CHAPTER_9_VERSION,
                                "第06日 23:00",
                                wrongChapterNineContent,
                                false,
                                List.of(item("continuity-c9-wrong-crystal-location", FixtureMemoryKind.STATE,
                                        FixtureLifecycle.INVALIDATED, 9, WRONG_CHAPTER_9_VERSION, "第06日 23:00",
                                        "黑色残晶", "错误正文产生了错误记忆：黑色残晶仍在旧钟楼地下室。", false),
                                        item("continuity-c9-wrong-knowledge-source", FixtureMemoryKind.CHARACTER_KNOWLEDGE,
                                                FixtureLifecycle.INVALIDATED, 9, WRONG_CHAPTER_9_VERSION, "第06日 23:00",
                                                "陆遥", "错误正文产生了错误记忆：陆遥从沈夜处得知封印。", false)),
                                List.of(wrongCandidate)),
                        version(
                                FINAL_CHAPTER_9_VERSION,
                                "第06日 23:00",
                                finalChapterNineContent,
                                true,
                                List.of(
                                        item("continuity-c9-crystal-carriage", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                                9, FINAL_CHAPTER_9_VERSION, "第06日 23:00", "黑色残晶",
                                                "修订后的正文确认黑色残晶仍在北上马车暗格。", true),
                                        item("continuity-c9-luyao-book-source", FixtureMemoryKind.CHARACTER_KNOWLEDGE,
                                                FixtureLifecycle.ACTIVE, 9, FINAL_CHAPTER_9_VERSION, "第06日 23:00", "陆遥",
                                                "修订后的正文保留陆遥从古籍《镇印残卷》获得封印知识。", true),
                                        item("continuity-c9-same-origin-confirmed", FixtureMemoryKind.FACT, FixtureLifecycle.ACTIVE,
                                                9, FINAL_CHAPTER_9_VERSION, "第06日 23:00", "黑色残晶与封印",
                                                "修订后的正文再次确认黑色残晶与封印同源。", true),
                                        item("continuity-c9-injury-continues", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                                9, FINAL_CHAPTER_9_VERSION, "第06日 23:00", "沈夜",
                                                "修订后的正文确认沈夜胸口伤势仍未愈合。", true)),
                                List.of(finalCandidate))));

        FixtureChapter chapterTen = chapter(
                10,
                "拂晓北上",
                "第07日 06:00",
                "chapter-10-final",
                chapterTenContent,
                List.of(
                        item("continuity-c10-time-after-ch9", FixtureMemoryKind.EVENT, FixtureLifecycle.ACTIVE,
                                10, "chapter-10-final", "第07日 06:00", "时间顺序",
                                "第10章发生在第9章夜晚之后的次日拂晓，马车继续北上。", true),
                        item("continuity-c10-crystal-carriage", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                10, "chapter-10-final", "第07日 06:00", "黑色残晶",
                                "黑色残晶仍位于北上马车暗格，没有从已知位置凭空消失。", true),
                        item("continuity-c10-injury-continues", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                10, "chapter-10-final", "第07日 06:00", "沈夜",
                                "沈夜胸口伤势仍未愈合。", true),
                        item("continuity-c10-ability-not-restored", FixtureMemoryKind.STATE, FixtureLifecycle.ACTIVE,
                                10, "chapter-10-final", "第07日 06:00", "回声感知",
                                "灵息尚未恢复，回声感知不能跳过主动激活和消耗规则再次使用。", true),
                        item("continuity-c10-same-origin-confirmed", FixtureMemoryKind.FACT, FixtureLifecycle.ACTIVE,
                                10, "chapter-10-final", "第07日 06:00", "黑色残晶与封印",
                                "陆遥再次依据古籍确认黑色残晶与封印同源。", true)),
                List.of());

        return new ContinuityFixture(new MemoryFixtureCorpus(
                List.of(chapterFive, chapterSix, chapterSeven, chapterEight, chapterNine, chapterTen)));
    }

    private static FixtureChapter chapter(
            int chapterNumber,
            String title,
            String storyTime,
            String versionId,
            String content,
            List<FixtureMemoryItem> memoryItems,
            List<FixtureCandidate> candidates) {
        return new FixtureChapter(
                chapterNumber,
                title,
                List.of(version(versionId, storyTime, content, true, memoryItems, candidates)));
    }

    private static FixtureChapterVersion version(
            String versionId,
            String storyTime,
            String content,
            boolean accepted,
            List<FixtureMemoryItem> memoryItems,
            List<FixtureCandidate> candidates) {
        return new FixtureChapterVersion(
                versionId,
                storyTime,
                content,
                FixtureHashing.sha256(content),
                accepted,
                memoryItems,
                candidates);
    }

    private static FixtureMemoryItem item(
            String id,
            FixtureMemoryKind kind,
            FixtureLifecycle lifecycle,
            int sourceChapter,
            String sourceVersion,
            String storyTime,
            String subject,
            String content,
            boolean longTerm) {
        return new FixtureMemoryItem(
                id, kind, lifecycle, sourceChapter, sourceVersion, storyTime, subject, content, longTerm);
    }
}
