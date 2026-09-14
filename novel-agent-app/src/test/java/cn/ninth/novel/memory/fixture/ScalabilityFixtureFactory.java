package cn.ninth.novel.memory.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates deterministic 10/50/100/200/300 chapter corpora for retrieval and lifecycle tests.
 */
public final class ScalabilityFixtureFactory {

    public static final List<Integer> SUPPORTED_CHAPTER_COUNTS = List.of(10, 50, 100, 200, 300);

    private ScalabilityFixtureFactory() {
    }

    public static List<ScalabilityFixture> all() {
        return SUPPORTED_CHAPTER_COUNTS.stream()
                .map(ScalabilityFixtureFactory::forChapterCount)
                .toList();
    }

    public static ScalabilityFixture forChapterCount(int chapterCount) {
        if (!SUPPORTED_CHAPTER_COUNTS.contains(chapterCount)) {
            throw new IllegalArgumentException(
                    "supported scalability chapter counts: " + SUPPORTED_CHAPTER_COUNTS);
        }

        List<FixtureChapter> chapters = new ArrayList<>(chapterCount);
        for (int chapterNumber = 1; chapterNumber <= chapterCount; chapterNumber++) {
            chapters.add(chapter(chapterNumber));
        }
        return new ScalabilityFixture(chapterCount, new MemoryFixtureCorpus(chapters));
    }

    private static FixtureChapter chapter(int chapterNumber) {
        String versionId = "scale-chapter-%03d-final".formatted(chapterNumber);
        String storyTime = "第%03d日 08:00".formatted(chapterNumber);
        String content = "第%d章，沈夜沿官道赶路。路边有一名普通路人经过，" +
                "他吃了一碗热粥，相关细节只服务本章场景，不产生长期影响。";
        content = content.formatted(chapterNumber);

        List<FixtureMemoryItem> items = new ArrayList<>();
        items.add(item(
                "scale-ch%03d-ordinary-event".formatted(chapterNumber),
                FixtureMemoryKind.EVENT,
                FixtureLifecycle.RESOLVED,
                chapterNumber,
                versionId,
                storyTime,
                "普通事件",
                "第%d章的赶路和短暂问路已经结束，不影响后续剧情。".formatted(chapterNumber),
                false));
        items.add(item(
                "scale-ch%03d-passerby".formatted(chapterNumber),
                FixtureMemoryKind.EPISODIC_DETAIL,
                FixtureLifecycle.ARCHIVED,
                chapterNumber,
                versionId,
                storyTime,
                "普通路人",
                "第%d章出现的普通路人没有姓名、关系或后续剧情作用。".formatted(chapterNumber),
                false));
        items.add(item(
                "scale-ch%03d-food".formatted(chapterNumber),
                FixtureMemoryKind.EPISODIC_DETAIL,
                FixtureLifecycle.ARCHIVED,
                chapterNumber,
                versionId,
                storyTime,
                "普通饮食",
                "第%d章的一碗热粥是普通饮食，不改变资源、能力、关系或剧情目标。".formatted(chapterNumber),
                false));

        if (chapterNumber == 1) {
            items.add(item(
                    "scale-world-rule-nocturne-ability",
                    FixtureMemoryKind.WORLD_RULE,
                    FixtureLifecycle.ACTIVE,
                    chapterNumber,
                    versionId,
                    storyTime,
                    "夜行能力",
                    "夜行能力只能由持有月印的人主动激活，每次激活消耗一枚月息。",
                    true));
        }
        if (chapterNumber == 2) {
            items.add(item(
                    "scale-character-change-shenye",
                    FixtureMemoryKind.CHARACTER_CHANGE,
                    FixtureLifecycle.ACTIVE,
                    chapterNumber,
                    versionId,
                    storyTime,
                    "沈夜",
                    "沈夜在第2章后决定优先保护同伴，后续选择会受到这一长期变化影响。",
                    true));
        }
        if (chapterNumber == 3) {
            items.add(item(
                    "scale-open-loop-missing-seal",
                    FixtureMemoryKind.OPEN_LOOP,
                    FixtureLifecycle.ACTIVE,
                    chapterNumber,
                    versionId,
                    storyTime,
                    "失踪封印",
                    "失踪封印的去向尚未解决，后续章节仍可能推进这一线程。",
                    true));
        }
        if (chapterNumber == 8) {
            items.add(item(
                    "scale-open-loop-courier",
                    FixtureMemoryKind.OPEN_LOOP,
                    FixtureLifecycle.ACTIVE,
                    chapterNumber,
                    versionId,
                    storyTime,
                    "失联信使",
                    "失联信使尚未找到，当前仍是少量长期未解决 Open Loop 之一。",
                    true));
        }
        if (chapterNumber == 6) {
            items.add(item(
                    "scale-resolved-side-story-caravan",
                    FixtureMemoryKind.CONSOLIDATED,
                    FixtureLifecycle.RESOLVED,
                    chapterNumber,
                    versionId,
                    storyTime,
                    "商队支线",
                    "商队支线已经解决：失散商队回到主路，结果已压缩为可追溯摘要。",
                    true));
        }

        FixtureCandidate candidate = new FixtureCandidate(
                "scale-ch%03d-candidate".formatted(chapterNumber),
                chapterNumber,
                versionId,
                FixtureHashing.sha256(content),
                "正文第1句～第2句",
                FixtureCandidateType.STATE_CHANGE,
                FixtureCandidateStatus.PROVISIONAL,
                "第%d章普通场景的状态候选，仅用于候选扫描量统计。".formatted(chapterNumber),
                "第%d章，沈夜沿官道赶路。".formatted(chapterNumber),
                null);

        return new FixtureChapter(
                chapterNumber,
                "规模测试第%d章".formatted(chapterNumber),
                List.of(new FixtureChapterVersion(
                        versionId,
                        storyTime,
                        content,
                        FixtureHashing.sha256(content),
                        true,
                        items,
                        List.of(candidate))));
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
