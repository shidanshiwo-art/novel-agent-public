package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.domain.memory.model.MemoryCandidate;
import cn.ninth.novel.domain.memory.model.MemoryCandidateType;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryContextPack;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.memory.model.MemoryProfile;
import cn.ninth.novel.domain.memory.model.MemorySourceVersion;
import cn.ninth.novel.memory.fixture.ContinuityFixture;
import cn.ninth.novel.memory.fixture.ContinuityFixtureFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewChapterNodeContinuityTest {

    @Test
    void deterministicChecksShouldReportTimeLocationLifeRuleAndKnowledgeIssues() {
        ChapterContextAggregate context = context(
                6,
                StoryBibleEntity.builder().hardRulesJson("[\"死者不能复生\"]").build(),
                List.of(StoryCharacterEntity.builder().name("张三").build(),
                        StoryCharacterEntity.builder().name("陆遥").build()),
                ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                5,
                                "第03日 18:00，张三已经死亡。黑色残晶位于旧钟楼地下室。",
                                List.of("陆遥依据古籍知道封印存在。"),
                                List.of(),
                                "旧钟楼地下室"
                        )))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(5)
                                .content("第03日 18:00，张三已经死亡。黑色残晶位于旧钟楼地下室。")
                                .build())
                        .storyStateSnapshot(new StoryStateSnapshot(
                                List.of(),
                                List.of(),
                                List.of("陆遥知道封印存在，来源是古籍《镇印残卷》，不是沈夜的转述。"),
                                List.of("黑色残晶位于旧钟楼地下室")
                        ))
                        .build());
        String draft = "第03日 06:00，张三还活着；黑色残晶仍在北上马车暗格。"
                + "陆遥从沈夜口中得知封印存在。死者复生。";

        ReviewReportVO report = review(context, draft, Map.of());

        System.out.printf(
                "REVIEW deterministic checks：issues=%s, sourceChapters=%s%n",
                report.getReviewIssueVOList().stream()
                        .map(issue -> issue.getCategory() + ":" + issue.getDescription()).toList(),
                report.getReviewIssueVOList().stream().map(ReviewIssueVO::getSourceChapter).toList()
        );
        assertThat(report.getReviewIssueVOList())
                .extracting(ReviewIssueVO::getCategory)
                .contains("时间线", "独占位置", "生存状态", "世界规则", "知识来源");
        assertThat(report.getReviewIssueVOList())
                .filteredOn(issue -> issue.getCategory().equals("时间线"))
                .allSatisfy(issue -> {
                    assertThat(issue.getSeverity()).isEqualTo(SeverityEnum.BLOCKER);
                    assertThat(issue.getSourceChapter()).isEqualTo(5);
                    assertThat(issue.getSourceEvidence()).contains("第03日 18:00");
                    assertThat(draft).contains(issue.getEvidence());
                });
    }

    @Test
    void sourceVersionMismatchAndStaleCandidateShouldBeReportedWithoutReuse() {
        String oldContent = "旧版本正文。";
        String finalContent = "修订版本正文。";
        MemorySourceVersion oldVersion = MemorySourceVersion.create("chapter-6-v1", oldContent);
        MemorySourceVersion finalVersion = MemorySourceVersion.create("chapter-6-v2", finalContent);
        MemoryCandidate oldCandidate = MemoryCandidate.provisional(
                oldVersion,
                oldContent,
                0,
                oldContent.length(),
                MemoryCandidateType.EVENT
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(ChapterGraphKeys.CONTEXT, context(6, StoryBibleEntity.builder().build(),
                List.of(), ChapterHistoryVO.builder().build()));
        data.put(ChapterGraphKeys.DRAFT, finalContent);
        data.put(ChapterGraphKeys.SOURCE_VERSION, finalVersion);
        data.put(ChapterGraphKeys.MEMORY_CANDIDATES, List.of(oldCandidate));

        ReviewReportVO report = review(new ChapterGraphState(data));

        System.out.printf(
                "REVIEW source/version check：categories=%s, candidateStatusAfterRefresh=%s%n",
                report.getReviewIssueVOList().stream().map(ReviewIssueVO::getCategory).toList(),
                new ChapterGraphState(data).memoryCandidates().get(0).candidateStatus()
        );
        assertThat(report.getReviewIssueVOList())
                .filteredOn(issue -> issue.getCategory().equals("来源/版本"))
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getSourceChapter()).isEqualTo(6);
                    assertThat(issue.getSourceVersion()).isEqualTo("chapter-6-v1");
                    assertThat(issue.getSourceEvidence()).contains("旧版本正文");
                });
        assertThat(new ChapterGraphState(data).memoryCandidates().get(0).candidateStatus())
                .isEqualTo(cn.ninth.novel.domain.memory.model.MemoryCandidateStatus.STALE);
    }

    @Test
    void reviewPromptShouldUseBoundedTopicEvidenceAndKeepSemanticReview() {
        ChapterContextAggregate context = context(
                6,
                StoryBibleEntity.builder().hardRulesJson("[\"死者不能复生\"]").build(),
                List.of(StoryCharacterEntity.builder().name("陆遥").build()),
                ChapterHistoryVO.builder()
                        .recentMemories(List.of(
                                new ChapterMemoryVO(4, "不应进入当前审核的旧历史", List.of(), List.of(), null),
                                new ChapterMemoryVO(5, "第03日 18:00，陆遥查阅古籍。",
                                        List.of("陆遥依据古籍确认封印存在。"),
                                        List.of("残晶来源未明"),
                                        "马车继续北上。")
                        ))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(5)
                                .content("上一章完整正文不进入 Prompt")
                                .build())
                        .build());

        String prompt = new ReviewChapterNode(null)
                .buildReviewPrompt(context, "陆遥确认封印存在。");

        System.out.printf(
                "REVIEW topic evidence：bounded=%s, semanticFocus=%s, oldHistoryExcluded=%s%n",
                prompt.contains("## 分主题审稿证据") && prompt.contains("[第5章]"),
                prompt.contains("猜测是否越级") && prompt.contains("因果是否跳跃"),
                !prompt.contains("不应进入当前审核的旧历史")
        );
        assertThat(prompt)
                .contains("## 分主题审稿证据", "### 时间线", "[第5章]",
                        "## 语义复核重点", "猜测是否越级", "人物是否知道其不应知道的信息")
                .doesNotContain("不应进入当前审核的旧历史", "上一章完整正文不进入 Prompt");
    }

    @Test
    void v1ReviewPromptUsesCanonicalPackInsteadOfLegacyHistory() {
        MemoryContextItem canonicalState = MemoryContextItem.canonical(
                "CANONICAL_FACT",
                "canonical-review-state",
                MemoryContextCategory.CURRENT_STATES,
                "Canonical 状态：第03日，陆遥位于北港",
                5,
                "chapter-v1",
                "FACT_ACTIVE",
                "第03日",
                1);
        ChapterContextAggregate context = context(
                6,
                StoryBibleEntity.builder().build(),
                List.of(StoryCharacterEntity.builder().name("陆遥").build()),
                ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                5, "Legacy story_summary 不应进入 V1 Review", List.of(), List.of(), null)))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(5)
                                .content("Legacy Snapshot 正文不应进入 V1 Review")
                                .build())
                         .build(),
                new MemoryContextPack(
                        MemoryProfile.REVIEW,
                        List.of(),
                        List.of(),
                        List.of(canonicalState),
                        List.of(),
                        List.of(),
                        canonicalState.estimatedTokenCount()));
        List<String> prompts = new ArrayList<>();
        IChapterModelPort model = new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return Flux.empty();
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                prompts.add(userPrompt);
                return responseType.cast(ReviewReportVO.builder()
                        .reviewIssueVOList(List.of()).build());
            }
        };

        new ReviewChapterNode(model).apply(new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.DRAFT, "陆遥位于北港。",
                ChapterGraphKeys.MEMORY_MODE, MemoryMode.V1
        )));

        System.out.printf(
                "REVIEW V1 memory source: canonical=%s, legacySummaryLeaked=%s%n",
                prompts.get(0).contains("Canonical 状态：第03日，陆遥位于北港"),
                prompts.get(0).contains("Legacy story_summary"));
        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0))
                .contains("Canonical 状态：第03日，陆遥位于北港")
                .doesNotContain("Legacy story_summary", "Legacy Snapshot 正文");
    }

    @Test
    void chaptersFiveToTenFixtureShouldRejectWrongChapterNineEvidenceAndAcceptRevision() {
        ContinuityFixture fixture = ContinuityFixtureFactory.create();
        String wrongContent = fixture.chapter(9).versions().get(0).content();
        String finalContent = fixture.chapter(9).acceptedVersion().content();
        ChapterContextAggregate context = context(
                9,
                StoryBibleEntity.builder().build(),
                List.of(
                        StoryCharacterEntity.builder().name("沈夜").build(),
                        StoryCharacterEntity.builder().name("陆遥").build()
                ),
                ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                8,
                                fixture.chapter(8).acceptedVersion().content(),
                                List.of(
                                        fixture.memoryItem("continuity-c8-same-origin-confirmed").content(),
                                        fixture.memoryItem("continuity-c8-activate-ability").content()
                                ),
                                List.of(),
                                "黑色残晶位于北上马车暗格"
                        )))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(8)
                                .content(fixture.chapter(8).acceptedVersion().content())
                                .build())
                        .storyStateSnapshot(new StoryStateSnapshot(
                                List.of("黑色残晶位于北上马车暗格"),
                                List.of("沈夜胸口伤势仍未愈合"),
                                List.of("陆遥知道封印存在，来源是古籍《镇印残卷》，不是沈夜的转述"),
                                List.of("黑色残晶当前位于北上马车暗格")
                        ))
                        .build());

        MemorySourceVersion wrongVersion = MemorySourceVersion.create(
                ContinuityFixtureFactory.WRONG_CHAPTER_9_VERSION, wrongContent);
        MemoryCandidate wrongCandidate = MemoryCandidate.provisional(
                        wrongVersion,
                        wrongContent,
                        0,
                        Math.min(24, wrongContent.length()),
                        MemoryCandidateType.FACT)
                .withStatus(cn.ninth.novel.domain.memory.model.MemoryCandidateStatus.STALE);
        Map<String, Object> wrongState = new LinkedHashMap<>();
        wrongState.put(ChapterGraphKeys.CONTEXT, context);
        wrongState.put(ChapterGraphKeys.DRAFT, wrongContent);
        wrongState.put(ChapterGraphKeys.SOURCE_VERSION, wrongVersion);
        wrongState.put(ChapterGraphKeys.MEMORY_CANDIDATES, List.of(wrongCandidate));

        ReviewReportVO wrongReport = review(new ChapterGraphState(wrongState));
        System.out.printf(
                "第5～10章 fixture 错误版本回归：chapters=%d, wrongIssues=%s%n",
                fixture.chapters().size(),
                wrongReport.getReviewIssueVOList().stream()
                        .map(ReviewIssueVO::getCategory).toList()
        );
        assertThat(fixture.chapters()).hasSize(6);
        assertThat(wrongReport.getReviewIssueVOList())
                .extracting(ReviewIssueVO::getCategory)
                .contains("独占位置", "知识来源", "来源/版本");

        MemorySourceVersion finalVersion = MemorySourceVersion.create(
                ContinuityFixtureFactory.FINAL_CHAPTER_9_VERSION, finalContent);
        MemoryCandidate finalCandidate = MemoryCandidate.provisional(
                        finalVersion,
                        finalContent,
                        0,
                        Math.min(24, finalContent.length()),
                        MemoryCandidateType.FACT)
                .withStatus(cn.ninth.novel.domain.memory.model.MemoryCandidateStatus.READY_FOR_GATE);
        Map<String, Object> finalState = new LinkedHashMap<>();
        finalState.put(ChapterGraphKeys.CONTEXT, context);
        finalState.put(ChapterGraphKeys.DRAFT, finalContent);
        finalState.put(ChapterGraphKeys.SOURCE_VERSION, finalVersion);
        finalState.put(ChapterGraphKeys.MEMORY_CANDIDATES, List.of(finalCandidate));

        ReviewReportVO finalReport = review(new ChapterGraphState(finalState));
        System.out.printf(
                "第5～10章 fixture 修订版本回归：finalIssues=%s, candidate=%s%n",
                finalReport.getReviewIssueVOList().stream()
                        .map(ReviewIssueVO::getCategory).toList(),
                finalCandidate.candidateStatus()
        );
        assertThat(finalReport.getReviewIssueVOList())
                .filteredOn(issue -> List.of("独占位置", "知识来源", "来源/版本")
                        .contains(issue.getCategory()))
                .isEmpty();
    }

    @Test
    void b14HaoLePresenceConflictShouldBeReportedWithBusinessEvidence() {
        ChapterContextAggregate context = context(
                14,
                StoryBibleEntity.builder().build(),
                List.of(StoryCharacterEntity.builder().name("郝乐").build()),
                ChapterHistoryVO.builder()
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(13)
                                .content("第13章：赵铁峰中午已带郝乐去了行政楼报到，今夜没有郝乐。")
                                .build())
                        .recentMemories(List.of(new ChapterMemoryVO(
                                13,
                                "赵铁峰中午已带郝乐去了行政楼报到，今夜没有郝乐。",
                                List.of(), List.of(), null)))
                        .build(),
                null);

        ReviewIssueVO issue = review(context,
                        "宿舍门推开时，郝乐从宿舍床上弹起来。", Map.of())
                .getReviewIssueVOList().stream()
                .filter(value -> "CHARACTER_PRESENCE".equals(value.getIssueType()))
                .findFirst()
                .orElseThrow();

        System.out.printf(
                "B14 郝乐在场冲突：type=%s, checker=%s, sourceChapter=%s, current=%s, historical=%s%n",
                issue.getIssueType(), issue.getCheckerType(), issue.getSourceChapter(),
                issue.getCurrentEvidence(), issue.getHistoricalEvidence());
        assertThat(issue.getCheckerType()).isEqualTo("DETERMINISTIC");
        assertThat(issue.getSourceChapter()).isEqualTo(13);
        assertThat(issue.getCurrentEvidence()).contains("郝乐");
        assertThat(issue.getHistoricalEvidence()).contains("行政楼");
    }

    @Test
    void legacyItemLocationAndPossessionConflictShouldBeReported() {
        ChapterContextAggregate context = context(
                12,
                StoryBibleEntity.builder().build(),
                List.of(StoryCharacterEntity.builder().name("沈夜").build(),
                        StoryCharacterEntity.builder().name("陆遥").build()),
                ChapterHistoryVO.builder()
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(11)
                                .content("旧锁位于旧钟楼地下室。沈夜持有黑色残晶。")
                                .build())
                        .recentMemories(List.of(new ChapterMemoryVO(
                                11,
                                "旧锁位于旧钟楼地下室。沈夜持有黑色残晶。",
                                List.of(), List.of(), null)))
                        .build(),
                null);

        ReviewReportVO report = review(context,
                "旧锁在行政楼储物间。陆遥持有黑色残晶。", Map.of());

        System.out.printf(
                "Legacy Ch12 旧锁/持有冲突：issues=%s%n",
                report.getReviewIssueVOList().stream()
                        .map(issue -> issue.getIssueType() + ":" + issue.getRelatedEntity()
                                + ":" + issue.getCheckerType()).toList());
        assertThat(report.getReviewIssueVOList())
                .filteredOn(issue -> "ITEM_LOCATION".equals(issue.getIssueType()))
                .extracting(ReviewIssueVO::getRelatedEntity)
                .contains("旧锁", "黑色残晶");
    }

    @Test
    void legacyAbilityRuleConflictShouldEnterSemanticReview() {
        MemoryContextItem markerRule = MemoryContextItem.canonical(
                "CANONICAL_FACT",
                "legacy-ch13-marker-rule",
                MemoryContextCategory.RULES,
                "旧锁标记必须在月圆时才触发。",
                13,
                "chapter-13-final",
                "FACT_ACTIVE",
                "第13日",
                1);
        ChapterContextAggregate context = context(
                14,
                StoryBibleEntity.builder().build(),
                List.of(),
                ChapterHistoryVO.builder().build(),
                new MemoryContextPack(
                        MemoryProfile.REVIEW,
                        List.of(markerRule),
                        List.of(), List.of(), List.of(), List.of(),
                        markerRule.estimatedTokenCount()));

        ReviewReportVO report = review(new ChapterGraphState(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.DRAFT, "旧锁标记突然触发，门上的灰线亮了起来。",
                ChapterGraphKeys.MEMORY_MODE, MemoryMode.V1)));

        System.out.printf(
                "Legacy Ch13 标记规则冲突：issues=%s%n",
                report.getReviewIssueVOList().stream()
                        .map(issue -> issue.getIssueType() + ":" + issue.getCheckerType()
                                + ":" + issue.getReason()).toList());
        assertThat(report.getReviewIssueVOList())
                .filteredOn(issue -> "ABILITY_RULE".equals(issue.getIssueType()))
                .isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.getCheckerType()).isEqualTo("SEMANTIC");
                    assertThat(issue.getCurrentEvidence()).contains("标记");
                    assertThat(issue.getHistoricalEvidence()).contains("月圆");
                });
    }

    @Test
    void legalMovementTransferAndRecoveryShouldNotBeReported() {
        ChapterContextAggregate movementContext = context(
                14,
                StoryBibleEntity.builder().build(),
                List.of(StoryCharacterEntity.builder().name("郝乐").build()),
                ChapterHistoryVO.builder()
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(13)
                                .content("郝乐位于行政楼。")
                                .build())
                        .build(),
                null);
        ReviewReportVO movementReport = review(movementContext,
                "郝乐从行政楼回到宿舍。", Map.of());

        ChapterContextAggregate transferContext = context(
                14,
                StoryBibleEntity.builder().build(),
                List.of(StoryCharacterEntity.builder().name("沈夜").build(),
                        StoryCharacterEntity.builder().name("陆遥").build()),
                ChapterHistoryVO.builder()
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(13)
                                .content("沈夜持有黑色残晶。")
                                .build())
                        .build(),
                null);
        ReviewReportVO transferReport = review(transferContext,
                "沈夜将黑色残晶交给陆遥，陆遥持有黑色残晶。", Map.of());

        ChapterContextAggregate recoveryContext = context(
                11,
                StoryBibleEntity.builder().build(),
                List.of(StoryCharacterEntity.builder().name("沈夜").build()),
                ChapterHistoryVO.builder()
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(10)
                                .content("沈夜胸口伤势仍未愈合。")
                                .build())
                        .build(),
                null);
        ReviewReportVO recoveryReport = review(recoveryContext,
                "经过医师治疗，沈夜胸口伤势已经痊愈。", Map.of());

        System.out.printf(
                "合法移动/转移/恢复：movementIssues=%s, transferIssues=%s, recoveryIssues=%s%n",
                movementReport.getReviewIssueVOList().stream()
                        .map(ReviewIssueVO::getIssueType).toList(),
                transferReport.getReviewIssueVOList().stream()
                        .map(ReviewIssueVO::getIssueType).toList(),
                recoveryReport.getReviewIssueVOList().stream()
                        .map(ReviewIssueVO::getIssueType).toList());
        assertThat(movementReport.getReviewIssueVOList())
                .noneMatch(issue -> "CHARACTER_PRESENCE".equals(issue.getIssueType()));
        assertThat(transferReport.getReviewIssueVOList())
                .noneMatch(issue -> "ITEM_LOCATION".equals(issue.getIssueType()));
        assertThat(recoveryReport.getReviewIssueVOList())
                .noneMatch(issue -> "INJURY_PHYSICAL_STATE".equals(issue.getIssueType()));
    }

    private ReviewReportVO review(
            ChapterContextAggregate context,
            String draft,
            Map<String, Object> additional
    ) {
        Map<String, Object> data = new LinkedHashMap<>(additional);
        data.put(ChapterGraphKeys.CONTEXT, context);
        data.put(ChapterGraphKeys.DRAFT, draft);
        return review(new ChapterGraphState(data));
    }

    private ReviewReportVO review(ChapterGraphState state) {
        Map<String, Object> update = new ReviewChapterNode(modelReturningEmptyReport()).apply(state);
        assertThat(update).doesNotContainKey(ChapterGraphKeys.WORKFLOW_STATUS);
        return (ReviewReportVO) update.get(ChapterGraphKeys.REVIEW_REPORT);
    }

    private IChapterModelPort modelReturningEmptyReport() {
        return new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return Flux.empty();
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                return responseType.cast(ReviewReportVO.builder().reviewIssueVOList(List.of()).build());
            }
        };
    }

    private ChapterContextAggregate context(
            int chapterNumber,
            StoryBibleEntity storyBible,
            List<StoryCharacterEntity> characters,
            ChapterHistoryVO history
    ) {
        return context(chapterNumber, storyBible, characters, history, null);
    }

    private ChapterContextAggregate context(
            int chapterNumber,
            StoryBibleEntity storyBible,
            List<StoryCharacterEntity> characters,
            ChapterHistoryVO history,
            MemoryContextPack memoryContextPack
    ) {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("review-test").build())
                .storyBible(storyBible)
                .chapterPlan(ChapterPlanEntity.builder()
                        .outlineNodeCode("ARC_001")
                        .chapterNumber(chapterNumber)
                        .title("审核章节")
                        .status("READY")
                        .build())
                .arc(new cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO(
                        "ARC_001", "VOL_001",
                        cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum.ARC,
                        1, "审核章节", "审核连续性", chapterNumber, chapterNumber, "READY"))
                .characters(characters)
                .history(history)
                .memoryContextPack(memoryContextPack)
                .build();
    }
}
