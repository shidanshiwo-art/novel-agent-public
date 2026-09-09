package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import cn.ninth.novel.domain.chapter.model.aggregate.ChapterContextAggregate;
import cn.ninth.novel.domain.chapter.model.entity.ChapterPlanEntity;
import cn.ninth.novel.domain.chapter.model.entity.NovelProjectEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryBibleEntity;
import cn.ninth.novel.domain.chapter.model.entity.StoryCharacterEntity;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterHistoryVO;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponseException;
import cn.ninth.novel.domain.chapter.model.valobj.PreviousChapterVO;
import cn.ninth.novel.domain.chapter.model.valobj.PromptTraceRecord;
import cn.ninth.novel.domain.chapter.model.valobj.StoryStateSnapshot;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewIssueVO;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewReportVO;
import cn.ninth.novel.domain.chapter.model.valobj.enums.ChapterWorkflowStatusEnum;
import cn.ninth.novel.domain.chapter.model.valobj.enums.SeverityEnum;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphKeys;
import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.bsc.langgraph4j.action.NodeAction;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static cn.ninth.novel.domain.chapter.service.agent.SystemPrompt.REVIEW_SYSTEM_PROMPT;

class ChapterNodeTest {

    @Test
    void draftNodeShouldTrustContextValidatedByLoadNode() {
        ChapterContextAggregate context = contextValidatedByLoadNode();

        Map<String, Object> update = new DraftChapterNode(modelReturning("draft")).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, context
        )));

        assertThat(update).containsEntry(ChapterGraphKeys.DRAFT, "draft");
    }

    @Test
    void draftNodeShouldReadTitleFromArcAndSummaryFromChapterPlan() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setTitle("不应使用的计划标题");
        context.getChapterPlan().setSummary("林澈追查染血船票指向的旧渡口。");
        AtomicReference<String> userPrompt = new AtomicReference<>();

        Map<String, Object> update = new DraftChapterNode(modelCapturingPrompt(userPrompt, "draft"))
                .apply(state(Map.of(ChapterGraphKeys.CONTEXT, context)));

        System.out.printf(
                "DRAFT 标题来源校验：arcTitle=%s, planTitleIgnored=%s, summary=%s%n",
                userPrompt.get().contains("章节标题：旧渡口"),
                !userPrompt.get().contains("章节标题：不应使用的计划标题"),
                userPrompt.get().contains("章节摘要：林澈追查染血船票指向的旧渡口。")
        );
        assertThat(update).containsEntry(ChapterGraphKeys.DRAFT, "draft");
        assertThat(userPrompt.get())
                .contains("章节标题：旧渡口", "章节摘要：林澈追查染血船票指向的旧渡口。")
                .doesNotContain("章节标题：不应使用的计划标题");
    }

    @Test
    void contentNodesShouldUseConfirmedChapterOutlineWithoutPlanState() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setTitle("旧渡口");
        context.getChapterPlan().setSummary("林澈追查染血船票指向的旧渡口。");
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of())
                .build();

        String draftPrompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        String reviewPrompt = new ReviewChapterNode(null).buildReviewPrompt(context, "正文");
        String revisePrompt = new ReviseChapterNode(null)
                .buildRevisePrompt(context, "正文", report);

        System.out.printf(
                "正文节点章纲输入检查：draft=%s, review=%s, revise=%s%n",
                draftPrompt.contains("旧渡口"),
                reviewPrompt.contains("旧渡口"),
                revisePrompt.contains("旧渡口")
        );
        assertThat(draftPrompt)
                .contains("## 本章计划", "章节标题：旧渡口", "章节摘要：林澈追查染血船票指向的旧渡口。")
                .doesNotContain("当前章节计划");
        assertThat(reviewPrompt)
                .contains("## 当前章节章纲", "章节标题：旧渡口", "章节摘要：林澈追查染血船票指向的旧渡口。")
                .doesNotContain("当前章节计划");
        assertThat(revisePrompt)
                .contains("## 当前章节章纲", "章节标题：旧渡口", "章节摘要：林澈追查染血船票指向的旧渡口。")
                .doesNotContain("当前章节计划");
    }

    @Test
    void chapterDraftPromptShouldUseDeathEventInsteadOfDatabaseLifeStatus() {
        ChapterContextAggregate context = ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("test-project").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .outlineNodeCode("ARC_003")
                        .chapterNumber(3)
                        .title("第三章")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_003", "VOL_001", OutlineNodeKindEnum.ARC, 3,
                        "第三章", "本章概要", 3, 3, "READY"
                ))
                .characters(List.of(StoryCharacterEntity.builder()
                        .name("张三")
                        .build()))
                .history(ChapterHistoryVO.builder()
                        .recentMemories(List.of(new ChapterMemoryVO(
                                1,
                                "张三在钟楼中死亡",
                                List.of("林渊杀死张三，张三死亡"),
                                List.of(),
                                "无")))
                        .previousChapter(PreviousChapterVO.builder()
                                .chapterNumber(2)
                                .content("林渊在钟楼中杀死张三。")
                                .build())
                        .build())
                .build();

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        boolean eventRendered = prompt.contains("林渊杀死张三，张三死亡");
        boolean databaseLifeStatusLeaked = prompt.contains("张三 生存状态：存活")
                || prompt.contains("生存状态：存活");

        System.out.printf(
                "第3章 DRAFT 动态状态边界：keyEventRendered=%s, databaseLifeStatusLeaked=%s%n",
                eventRendered,
                databaseLifeStatusLeaked
        );
        assertThat(prompt)
                .contains("摘要：张三在钟楼中死亡", "- 林渊杀死张三，张三死亡")
                .doesNotContain("张三 生存状态：存活", "生存状态：存活", "生存状态：", "ALIVE");
    }

    @Test
    void contentNodesShouldUseIndependentCurrentStoryStateSnapshot() {
        ChapterContextAggregate context = readyContext();
        StoryStateSnapshot snapshot = new StoryStateSnapshot(
                List.of("议会徽记"),
                List.of("左臂受伤，暂时不能持弓"),
                List.of("林澈知道密室入口，守卫尚不知道"),
                List.of("当前在旧渡口，同行者已离场")
        );
        context.setHistory(ChapterHistoryVO.builder()
                .recentMemories(List.of(new ChapterMemoryVO(
                        1, "历史剧情", List.of("拿到徽记"), List.of(), "无")))
                .storyStateSnapshot(snapshot)
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(1)
                        .content("上一章正文")
                        .build())
                .build());
        ReviewReportVO report = new ReviewReportVO(List.of());

        String draftPrompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        String reviewPrompt = new ReviewChapterNode(null).buildReviewPrompt(context, "正文");
        String revisePrompt = new ReviseChapterNode(null)
                .buildRevisePrompt(context, "正文", report);

        System.out.printf(
                "StoryStateSnapshot Prompt 传递：draft=%s, review=%s, revise=%s%n",
                draftPrompt.contains("议会徽记"),
                reviewPrompt.contains("议会徽记"),
                revisePrompt.contains("议会徽记")
        );
        assertThat(draftPrompt).contains("## 当前有效状态", "重要资源/物品：", "议会徽记");
        assertThat(reviewPrompt).contains("## 当前有效状态", "左臂受伤，暂时不能持弓");
        assertThat(revisePrompt).contains("## 当前有效状态", "同行者已离场");
        assertThat(draftPrompt + reviewPrompt + revisePrompt)
                .doesNotContain("currentStateJson", "characterStates", "StoryStateSnapshot");
    }

    @Test
    void draftPromptShouldDeduplicatePreviousChapterMemory() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setChapterNumber(10);
        context.getChapterPlan().setTitle("第十章标题");
        context.setArc(new OutlineNodeVO(
                "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                "第十章标题", "本章概要", 10, 10, "READY"
        ));
        context.setHistory(ChapterHistoryVO.builder()
                .recentMemories(List.of(
                        new ChapterMemoryVO(
                                8,
                                "第8章压缩记忆",
                                List.of("第8章关键事件"),
                                List.of("第8章未解决问题"),
                                "第8章结尾钩子"
                        ),
                        new ChapterMemoryVO(
                                6,
                                "第6章压缩记忆",
                                List.of("第6章关键事件"),
                                List.of("第6章未解决问题"),
                                "第6章结尾钩子"
                        ),
                        new ChapterMemoryVO(
                                9,
                                "第9章压缩记忆",
                                List.of("第9章关键事件"),
                                List.of("第9章未解决问题"),
                                "第9章结尾钩子"
                        )
                ))
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(9)
                        .content("第9章完整正文")
                        .build())
                .build());

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        System.out.printf(
                "第10章 DRAFT 历史边界：olderMemory=%s, previousBody=%s%n",
                prompt.contains("第6章压缩记忆")
                        && prompt.contains("第8章压缩记忆")
                        && !prompt.contains("第9章压缩记忆"),
                prompt.contains("上一章正文：第9章完整正文")
        );
        assertThat(prompt)
                .contains("## 本章计划", "## 上一章正文", "## 此前章节记忆",
                        "## 人物资料", "## 故事设定",
                        "摘要：第6章压缩记忆",
                        "关键事件：\n- 第6章关键事件",
                        "未解决：\n- 第6章未解决问题",
                        "结尾钩子：第6章结尾钩子",
                        "摘要：第8章压缩记忆",
                        "关键事件：\n- 第8章关键事件",
                        "未解决：\n- 第8章未解决问题",
                        "结尾钩子：第8章结尾钩子",
                "章节号：9", "上一章正文：第9章完整正文");
        assertThat(prompt)
                .doesNotContain("摘要：第9章压缩记忆", "第9章关键事件",
                        "第9章未解决问题", "第9章结尾钩子");
        System.out.printf(
                "第10章 DRAFT ChapterPlan 去重：chapterNumberCount=%d, chapterTitleCount=%d%n",
                prompt.split("章节号：10", -1).length - 1,
                prompt.split("章节标题：第十章标题", -1).length - 1
        );
        assertThat(prompt.split("章节号：10", -1)).hasSize(2);
        assertThat(prompt.split("章节标题：第十章标题", -1)).hasSize(2);

        assertThat(prompt.indexOf("## 本章计划"))
                .isLessThan(prompt.indexOf("## 上一章正文"));
        assertThat(prompt.indexOf("## 上一章正文"))
                .isLessThan(prompt.indexOf("## 此前章节记忆"));
        assertThat(prompt.indexOf("## 此前章节记忆"))
                .isLessThan(prompt.indexOf("## 人物资料"));
        assertThat(prompt.indexOf("## 人物资料"))
                .isLessThan(prompt.indexOf("## 故事设定"));
        assertThat(prompt.indexOf("### 第 6 章"))
                .isLessThan(prompt.indexOf("### 第 8 章"));

        context.getHistory().setPreviousChapter(PreviousChapterVO.builder()
                .chapterNumber(8)
                .content("第8章完整正文不应传入")
                .build());
        String mismatchedPrompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        System.out.printf(
                "第10章 DRAFT 错位正文过滤：containsChapter8Body=%s%n",
                mismatchedPrompt.contains("第8章完整正文不应传入")
        );
        assertThat(mismatchedPrompt)
                .doesNotContain("第8章完整正文不应传入")
                .contains("无，未找到当前章节的直接上一章。");
    }

    @Test
    void draftPromptShouldOmitEmptyMemorySections() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setChapterNumber(5);
        context.setHistory(ChapterHistoryVO.builder()
                .recentMemories(List.of(new ChapterMemoryVO(
                        3,
                        "第3章摘要",
                        List.of(),
                        List.of(),
                        null)))
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(4)
                        .content("第4章完整正文")
                        .build())
                .build());

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        System.out.printf(
                "第5章 DRAFT 空 Memory 字段：summary=%s, emptySectionsOmitted=%s%n",
                prompt.contains("摘要：第3章摘要"),
                !prompt.contains("关键事件：")
                        && !prompt.contains("未解决：")
                        && !prompt.contains("结尾钩子：")
        );
        assertThat(prompt)
                .contains("摘要：第3章摘要")
                .doesNotContain("关键事件：", "未解决：", "结尾钩子：",
                        "shortSummary=", "keyEvents=", "unresolved=", "endingHook=");
    }

    @Test
    void draftUserPromptShouldContainContextWithoutGenerationInstructions() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setTitle("旧渡口");
        context.setStoryBible(StoryBibleEntity.builder()
                .worldBackground("雾港由七家商会共治。")
                .styleGuide("第三人称限知。")
                .build());

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        System.out.printf(
                "DRAFT User Prompt 上下文边界：styleSection=%s, generationInstructionLeaked=%s%n",
                prompt.contains("## 写作风格") && prompt.contains("文风指南：第三人称限知。"),
                prompt.contains("你必须")
                        || prompt.contains("你禁止")
                        || prompt.contains("务必")
                        || prompt.contains("请注意")
                        || prompt.contains("只输出当前章节正文")
                        || prompt.contains("不要提前写完未来章节")
        );
        assertThat(prompt)
                .contains("## 本章计划", "## 上一章正文", "## 此前章节记忆",
                        "## 人物资料", "## 故事设定", "## 写作风格",
                        "世界背景：雾港由七家商会共治。", "文风指南：第三人称限知。")
                .doesNotContain("你必须", "你禁止", "务必", "请注意",
                        "只输出当前章节正文", "不要提前写完未来章节");
        assertThat(prompt.indexOf("## 故事设定"))
                .isLessThan(prompt.indexOf("## 写作风格"));
    }

    @Test
    void draftPromptShouldRenderOnlyChapterRelevantStoryBibleContext() {
        ChapterContextAggregate context = readyContext();
        context.setStoryBible(StoryBibleEntity.builder()
                .oneSentencePremise("全书梗概不应进入 DRAFT")
                .coreTheme("全书主题不应进入 DRAFT")
                .mainConflict("全书主线矛盾不应进入 DRAFT")
                .endingDirection("全书结局方向不应进入 DRAFT")
                .worldBackground("当前章节需要的雾港背景")
                .hardRulesJson("[\"当前章节需要的硬规则\"]")
                .powerSystemJson("{\"name\":\"当前章节需要的力量体系\"}")
                .styleGuide("当前章节需要的文风")
                .build());

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        boolean chapterRelevantContextRendered = prompt.contains("当前章节需要的雾港背景")
                && prompt.contains("当前章节需要的硬规则")
                && prompt.contains("当前章节需要的力量体系")
                && prompt.contains("当前章节需要的文风");
        boolean longRangeBibleRepeated = prompt.contains("全书梗概不应进入 DRAFT")
                || prompt.contains("全书主题不应进入 DRAFT")
                || prompt.contains("全书主线矛盾不应进入 DRAFT")
                || prompt.contains("全书结局方向不应进入 DRAFT");

        System.out.printf(
                "DRAFT Story Bible 字段边界：chapterRelevant=%s, longRangeRepeated=%s%n",
                chapterRelevantContextRendered,
                longRangeBibleRepeated
        );
        assertThat(prompt)
                .contains("世界背景：当前章节需要的雾港背景",
                        "不可违反的硬规则：\n- 当前章节需要的硬规则",
                        "力量体系：\n- 名称：当前章节需要的力量体系",
                        "文风指南：当前章节需要的文风")
                .doesNotContain("全书梗概不应进入 DRAFT", "全书主题不应进入 DRAFT",
                        "全书主线矛盾不应进入 DRAFT", "全书结局方向不应进入 DRAFT");
    }

    @Test
    void firstChapterDraftPromptShouldNotIncludePreviousBody() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setChapterNumber(1);
        context.setHistory(ChapterHistoryVO.builder()
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(0)
                        .content("不存在的上一章正文")
                        .build())
                .build());

        String prompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        System.out.printf(
                "第1章 DRAFT 历史边界：containsPreviousBody=%s%n",
                prompt.contains("不存在的上一章正文")
        );
        assertThat(prompt)
                .doesNotContain("不存在的上一章正文")
                .contains("无，未找到当前章节的直接上一章。");
    }

    @Test
    void nodesShouldUseAppExceptionWhenRequiredStateIsMissing() {
        ChapterContextAggregate context = readyContext();
        ReviewReportVO report = new ReviewReportVO();

        assertMissingState(
                () -> new DraftChapterNode(modelReturning(null)).apply(state(Map.of())),
                "DRAFT 节点缺少 context"
        );
        assertMissingState(
                () -> new ReviewChapterNode(modelReturning(null)).apply(state(Map.of())),
                "REVIEW 节点缺少 context"
        );
        assertMissingState(
                () -> new ReviewChapterNode(modelReturning(null)).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, context
                ))),
                "REVIEW 节点缺少 draft"
        );
        assertMissingState(
                () -> new ReviseChapterNode(modelReturning(null)).apply(state(Map.of())),
                "REVISE 节点缺少 context"
        );
        assertMissingState(
                () -> new ReviseChapterNode(modelReturning(null)).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, context
                ))),
                "REVISE 节点缺少 draft"
        );
        assertMissingState(
                () -> new ReviseChapterNode(modelReturning(null)).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, context,
                        ChapterGraphKeys.DRAFT, "draft"
                ))),
                "REVISE 节点缺少 reviewReport"
        );
        assertMissingState(
                () -> new CompressChapterNode(modelReturning(null)).apply(state(Map.of())),
                "COMPRESSION 节点缺少 context"
        );
        assertMissingState(
                () -> new CompressChapterNode(modelReturning(null)).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, context,
                        ChapterGraphKeys.CHAPTER_NUMBER, 1
                ))),
                "COMPRESSION 节点缺少 draft"
        );
    }

    @Test
    void draftNodeShouldWriteOnlyDraftNodeUpdate() throws Exception {
        ChapterContextAggregate context = readyContext();
        DraftChapterNode node = new DraftChapterNode(modelReturning(" draft "));

        Map<String, Object> update = asNode(node).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, context
        )));

        assertNodeUpdate(update, ChapterGraphKeys.DRAFT, "draft", "DRAFT");
        assertPrivateMethod(DraftChapterNode.class, "draft", ChapterContextAggregate.class);
    }

    @Test
    void draftNodeShouldAccumulateStreamChunksAndNotifyEachChunk() {
        List<String> receivedChunks = new java.util.ArrayList<>();
        IChapterModelPort model = new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return Flux.just("第一段", "第二段");
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }
        };

        Map<String, Object> update = new DraftChapterNode(model).applyStreaming(
                state(Map.of(ChapterGraphKeys.CONTEXT, readyContext())),
                receivedChunks::add
        );

        assertThat(update).containsEntry(ChapterGraphKeys.DRAFT, "第一段第二段");
        assertThat(receivedChunks).containsExactly("第一段", "第二段");
        System.out.printf(
                "DRAFT 流式正文检查：chunkCount=%d, content=%s%n",
                receivedChunks.size(),
                update.get(ChapterGraphKeys.DRAFT)
        );
    }

    @Test
    void draftNodeShouldStopBeforeReturningPartialDraftWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        List<String> receivedChunks = new java.util.ArrayList<>();
        IChapterModelPort model = new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return Flux.just("已收到的预览", "不应继续接收");
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }
        };

        CancellationException exception = catchThrowableOfType(
                () -> new DraftChapterNode(model).applyStreaming(
                        state(Map.of(ChapterGraphKeys.CONTEXT, readyContext())),
                        chunk -> {
                            receivedChunks.add(chunk);
                            cancelled.set(true);
                        },
                        cancelled::get,
                        subscription -> { }
                ),
                CancellationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(receivedChunks).containsExactly("已收到的预览");
        System.out.println(
                "DRAFT STOP 取消检查通过：已收到 chunk 仅保留为预览，未返回完整正文 State 更新"
        );
    }

    @Test
    void draftNodeShouldRecordRetriesAfterTransientModelFailure() {
        AtomicInteger calls = new AtomicInteger();
        IChapterModelPort model = modelReturningAfterTransientFailure("draft", calls);
        DraftChapterNode node = new DraftChapterNode(model, new ChapterModelRetryExecutor());

        Map<String, Object> update = node.apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, readyContext()
        )));

        assertThat(update)
                .containsEntry(ChapterGraphKeys.DRAFT, "draft")
                .containsEntry(ChapterGraphKeys.RETRY_COUNT, 1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void reviewNodeShouldWriteOnlyReviewNodeUpdate() throws Exception {
        ChapterContextAggregate context = readyContext();
        ReviewReportVO expected = ReviewReportVO.builder()
                .reviewIssueVOList(List.of())
                .build();
        ReviewChapterNode node = new ReviewChapterNode(modelReturning(expected));

        Map<String, Object> update = asNode(node).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.DRAFT, "draft"
        )));

        assertThat(update)
                .containsOnlyKeys(
                        ChapterGraphKeys.REVIEW_REPORT,
                        ChapterGraphKeys.CURRENT_NODE,
                        ChapterGraphKeys.COMPLETED_STAGES,
                        ChapterGraphKeys.RETRY_COUNT,
                        ChapterGraphKeys.FAILURE_MESSAGE,
                        ChapterGraphKeys.GENERATION_METRICS_DELTA
                )
                .containsEntry(ChapterGraphKeys.REVIEW_REPORT, expected)
                .containsEntry(ChapterGraphKeys.CURRENT_NODE, "REVIEW")
                .containsEntry(ChapterGraphKeys.COMPLETED_STAGES, List.of("REVIEW"))
                .containsEntry(ChapterGraphKeys.RETRY_COUNT, 0)
                .containsEntry(ChapterGraphKeys.FAILURE_MESSAGE, "");
        assertPrivateMethod(
                ReviewChapterNode.class,
                "review",
                ChapterContextAggregate.class,
                String.class
        );
    }

    @Test
    void reviewNodeShouldEnterRecoverableStateWhenReportHasNoIssueList() {
        Map<String, Object> update = new ReviewChapterNode(modelReturning(new ReviewReportVO()))
                .apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, readyContext(),
                        ChapterGraphKeys.DRAFT, "林澈推开木门。"
                )));

        assertThat(update)
                .containsEntry(
                        ChapterGraphKeys.WORKFLOW_STATUS,
                        ChapterWorkflowStatusEnum.REVIEW_FAILED.name()
                )
                .containsEntry(
                        ChapterGraphKeys.RETRY_COUNT,
                        ChapterModelRetryExecutor.MAX_RETRIES
                )
                .doesNotContainKey(ChapterGraphKeys.DRAFT);
        System.out.println("非法 REVIEW 报告重试耗尽后进入 REVIEW_FAILED，正文 State 未被覆盖");
    }

    @Test
    void reviewNodeShouldEnterRecoverableStateWhenIssueMissesRequiredField() {
        String draft = "林澈推开木门。";

        assertInvalidReviewIssue(ReviewIssueVO.builder()
                .category("剧情推进")
                .description("问题说明")
                .evidence(draft)
                .build(), draft, "severity");
        assertInvalidReviewIssue(ReviewIssueVO.builder()
                .severity(SeverityEnum.MAJOR)
                .category(" ")
                .description("问题说明")
                .evidence(draft)
                .build(), draft, "category");
        assertInvalidReviewIssue(ReviewIssueVO.builder()
                .severity(SeverityEnum.MAJOR)
                .category("剧情推进")
                .description(" ")
                .evidence(draft)
                .build(), draft, "description");
        assertInvalidReviewIssue(ReviewIssueVO.builder()
                .severity(SeverityEnum.MAJOR)
                .category("剧情推进")
                .description("问题说明")
                .evidence(" ")
                .build(), draft, "evidence");
    }

    @Test
    void reviewNodeShouldEnterRecoverableStateWhenEvidenceIsNotInDraft() {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.MAJOR)
                        .category("剧情推进")
                        .description("主角没有取得钥匙")
                        .evidence("正文中不存在的证据")
                        .build()))
                .build();

        Map<String, Object> update = new ReviewChapterNode(modelReturning(report))
                .apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, readyContext(),
                        ChapterGraphKeys.DRAFT, "林澈推开木门。"
                )));

        assertThat(update)
                .containsEntry(
                        ChapterGraphKeys.WORKFLOW_STATUS,
                        ChapterWorkflowStatusEnum.REVIEW_FAILED.name()
                )
                .containsEntry(
                        ChapterGraphKeys.RETRY_COUNT,
                        ChapterModelRetryExecutor.MAX_RETRIES
                )
                .doesNotContainKey(ChapterGraphKeys.DRAFT);
        System.out.println("非法 evidence 经 REVIEW 重试后进入可恢复状态，当前正文保持不变");
    }

    @Test
    void reviewNodeShouldRetryValidationAndUseTheNextValidReport() {
        String draft = "林澈推开木门。";
        ReviewReportVO invalid = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.MAJOR)
                        .category("剧情推进")
                        .description("问题说明")
                        .evidence("正文中不存在")
                        .build()))
                .build();
        ReviewReportVO valid = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.MAJOR)
                        .category("剧情推进")
                        .description("问题说明")
                        .evidence(draft)
                        .build()))
                .build();
        AtomicInteger calls = new AtomicInteger();
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
                return responseType.cast(calls.incrementAndGet() == 1 ? invalid : valid);
            }
        };

        Map<String, Object> update = new ReviewChapterNode(
                model,
                new ChapterModelRetryExecutor()
        ).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, readyContext(),
                ChapterGraphKeys.DRAFT, draft
        )));

        assertThat(update)
                .containsEntry(ChapterGraphKeys.REVIEW_REPORT, valid)
                .containsEntry(ChapterGraphKeys.RETRY_COUNT, 1)
                .doesNotContainKey(ChapterGraphKeys.WORKFLOW_STATUS);
        assertThat(calls).hasValue(2);
        System.out.printf("REVIEW evidence 校验重试检查：calls=%d, retryCount=%s%n",
                calls.get(), update.get(ChapterGraphKeys.RETRY_COUNT));
    }

    @Test
    void reviewNodeShouldPersistTraceForEveryRetryAttempt() {
        String draft = "林澈推开木门。";
        ReviewReportVO invalid = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.MAJOR)
                        .category("剧情推进")
                        .description("问题说明")
                        .evidence("正文中不存在")
                        .build()))
                .build();
        ReviewReportVO valid = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.MAJOR)
                        .category("剧情推进")
                        .description("问题说明")
                        .evidence(draft)
                        .build()))
                .build();
        AtomicInteger calls = new AtomicInteger();
        List<PromptTraceRecord> traces = new java.util.ArrayList<>();
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
                return responseType.cast(calls.incrementAndGet() == 1 ? invalid : valid);
            }
        };

        Map<String, Object> update = new ReviewChapterNode(
                model,
                new ChapterModelRetryExecutor(),
                new PromptTraceRecorder(traces::add)
        ).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, readyContext(),
                ChapterGraphKeys.DRAFT, draft,
                ChapterGraphKeys.WORKFLOW_ID, "workflow-review-trace"
        )));

        assertThat(update).containsEntry(ChapterGraphKeys.RETRY_COUNT, 1);
        assertThat(traces).extracting(PromptTraceRecord::attempt)
                .containsExactly(0, 1);
        assertThat(traces).extracting(PromptTraceRecord::success)
                .containsExactly(false, true);
        assertThat(traces.get(0).errorCode()).isEqualTo(ResponseCode.E0004.getCode());
        assertThat(traces).allSatisfy(trace -> {
            assertThat(trace.workflowId()).isEqualTo("workflow-review-trace");
            assertThat(trace.systemPrompt()).isNotBlank();
            assertThat(trace.userPrompt()).isNotBlank();
            assertThat(trace.responseText()).isNotBlank();
        });
        System.out.printf(
                "REVIEW retry Trace 独立保存检查：attempts=%s, success=%s, firstError=%s%n",
                traces.stream().map(PromptTraceRecord::attempt).toList(),
                traces.stream().map(PromptTraceRecord::success).toList(),
                traces.get(0).errorCode()
        );
    }

    @Test
    void reviewNodeShouldKeepRawResponseWhenStructuredParsingFails() {
        String rawResponse = "{\"reviewIssueVOList\":[{\"evidence\":\"正文中不存在\"}";
        List<PromptTraceRecord> traces = new java.util.ArrayList<>();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> ChapterModelResponse<T> callWithRawResponse(
                    String systemPrompt,
                    String userPrompt,
                    Class<T> responseType
            ) {
                throw new ChapterModelResponseException(
                        ResponseCode.E0007.getCode(),
                        "无法解析模型返回的结构化内容",
                        null,
                        rawResponse
                );
            }
        };

        AppException exception = catchThrowableOfType(
                () -> new ReviewChapterNode(
                        model,
                        new ChapterModelRetryExecutor(),
                        new PromptTraceRecorder(traces::add)
                ).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, readyContext(),
                        ChapterGraphKeys.DRAFT, "林澈推开木门。",
                        ChapterGraphKeys.WORKFLOW_ID, "workflow-raw-response"
                ))),
                AppException.class
        );

        assertThat(exception.getCode()).isEqualTo(ResponseCode.E0007.getCode());
        assertThat(traces).hasSize(2);
        assertThat(traces).extracting(PromptTraceRecord::attempt)
                .containsExactly(0, 1);
        assertThat(traces).allSatisfy(trace -> {
            assertThat(trace.responseText()).isEqualTo(rawResponse);
            assertThat(trace.success()).isFalse();
            assertThat(trace.errorCode()).isEqualTo(ResponseCode.E0007.getCode());
        });
        System.out.printf(
                "结构化解析失败 Trace 保留 raw response：attempts=%s, length=%d%n",
                traces.stream().map(PromptTraceRecord::attempt).toList(),
                rawResponse.length()
        );
    }

    @Test
    void reviewNodeShouldNormalizeEvidenceWhitespaceBeforeCheckingDraft() {
        String draft = "林澈推开木门。\r\n他屏住呼吸，听见门外的脚步声。";
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(ReviewIssueVO.builder()
                        .severity(SeverityEnum.MAJOR)
                        .category("剧情推进")
                        .description("主角发现门外有人")
                        .evidence("  林澈推开木门。\n\n 他屏住呼吸，听见门外的脚步声。  ")
                        .build()))
                .build();

        Map<String, Object> update = new ReviewChapterNode(modelReturning(report))
                .apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, readyContext(),
                        ChapterGraphKeys.DRAFT, draft
                )));

        assertThat(update)
                .containsEntry(ChapterGraphKeys.REVIEW_REPORT, report)
                .containsEntry(ChapterGraphKeys.RETRY_COUNT, 0)
                .doesNotContainKey(ChapterGraphKeys.WORKFLOW_STATUS);
        System.out.println("REVIEW evidence 格式归一化检查通过：CRLF/LF、首尾空白和连续空白不再触发 E0004");
    }

    @Test
    void reviewPromptShouldIncludeAllReviewableStoryConstraintsAndTargetWords() {
        ChapterContextAggregate context = readyContext();
        context.setProject(NovelProjectEntity.builder()
                .projectCode("test-project")
                .wordsPerChapter(2500)
                .build());
        context.setStoryBible(StoryBibleEntity.builder()
                .hardRulesJson("[\"死者不能复生\"]")
                .powerSystemJson("{\"name\":\"灵脉\"}")
                .worldBackground("雾港由七家商会共治。")
                .styleGuide("第三人称限知。")
                .build());

        String prompt = new ReviewChapterNode(modelReturning(null))
                .buildReviewPrompt(context, "待审正文");

        assertThat(prompt)
                .contains("不可违反的硬规则：\n- 死者不能复生")
                .contains("力量体系：\n- 名称：灵脉")
                .contains("世界背景：雾港由七家商会共治。")
                .contains("目标字数：2500")
                .doesNotContain("[\"死者不能复生\"]", "{\"name\":\"灵脉\"}");
    }

    @Test
    void bodyPromptsShouldRenderStoredContextAsBusinessText() {
        ChapterContextAggregate context = readyContext();
        context.setStoryBible(StoryBibleEntity.builder()
                .hardRulesJson("[\"死者不能复生\",\"月蚀时不能施法\"]")
                .powerSystemJson("{\"name\":\"灵脉\",\"description\":\"以潮汐引动灵力\","
                        + "\"levels\":\"锻体、凝气\",\"supplement\":\"消耗体温\"}")
                .build());
        context.setCharacters(List.of(StoryCharacterEntity.builder()
                .characterCode("char-lin-che")
                .name("林澈")
                .roleType("主角")
                .gender("男")
                .ageDescription("二十七岁")
                .appearance("眉眼清冷，常穿旧青衫")
                .personality("谨慎坚韧")
                .backgroundStory("曾在雾港长大")
                .note("优先保持其克制的说话方式")
                .build()));
        context.getChapterPlan().setChapterNumber(3);
        context.setHistory(ChapterHistoryVO.builder()
                .recentMemories(List.of(
                        new ChapterMemoryVO(
                                1,
                                "更早章节记忆",
                                List.of("更早关键事件"),
                                List.of("更早未解决问题"),
                                "更早结尾钩子"
                        ),
                        new ChapterMemoryVO(
                                2,
                                "林澈抵达雾港。",
                                List.of("林澈前往旧渡口", "林澈决定追查残页来源"),
                                List.of("船票来自谁"),
                                "染血船票指向旧渡口。"
                        )
                ))
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(2)
                        .content("第2章完整正文")
                        .build())
                .build());
        ReviewReportVO report = ReviewReportVO.builder().reviewIssueVOList(List.of()).build();

        String draftPrompt = new DraftChapterNode(null).buildDraftUserPrompt(context);
        String reviewPrompt = new ReviewChapterNode(null).buildReviewPrompt(context, "正文");
        String revisePrompt = new ReviseChapterNode(null).buildRevisePrompt(context, "正文", report);

        System.out.printf(
                "正文 Prompt 上下文来源：draftStatic=%s, reviewStatic=%s, reviseStatic=%s, dynamicCharacterStateExcluded=%s%n",
                hasStaticCharacterContext(draftPrompt),
                hasStaticCharacterContext(reviewPrompt),
                hasStaticCharacterContext(revisePrompt),
                !draftPrompt.contains("仅来自旧状态")
                        && !reviewPrompt.contains("仅来自旧状态")
                        && !revisePrompt.contains("仅来自旧状态")
        );
        assertThat(draftPrompt).satisfies(this::assertStaticCharacterContextRendered);
        assertThat(draftPrompt)
                .contains("## 人物资料")
                .doesNotContain("人物当前状态", "人物运行时状态", "角色状态快照");
        assertThat(draftPrompt)
                .doesNotContain("projectCode", "chapterPlanId", "sequenceNo", "workflowId",
                        "nodeCode", "数据库", "持久化", "DTO", "VO", "Graph",
                        "节点类型", "后端校验");
        assertThat(draftPrompt)
                .contains("## 此前章节记忆", "摘要：更早章节记忆", "关键事件：",
                        "- 更早关键事件", "未解决：", "- 更早未解决问题",
                        "结尾钩子：更早结尾钩子")
                .doesNotContain("摘要：林澈抵达雾港。", "- 林澈前往旧渡口",
                        "- 林澈决定追查残页来源", "船票来自谁",
                        "染血船票指向旧渡口。")
                .doesNotContain("主角目标", "实际结果", "付出代价", "人物变化");
        assertThat(reviewPrompt)
                .contains("## 相关人物静态设定")
                .contains("林澈", "角色定位：主角", "性别：男", "年龄：二十七岁",
                        "外貌：眉眼清冷，常穿旧青衫", "性格：谨慎坚韧", "背景故事：曾在雾港长大",
                        "作者备注：优先保持其克制的说话方式", "- 林澈前往旧渡口",
                        "- 林澈决定追查残页来源")
                .contains("不可违反的硬规则：\n- 死者不能复生")
                .doesNotContain("仅来自旧状态", "人物变化", "生存状态：", "当前状态：");
        assertThat(revisePrompt).satisfies(this::assertStaticCharacterContextRendered);
        assertThat(revisePrompt)
                .contains("## 相关人物静态设定")
                .contains("## 近期剧情记忆", "摘要：林澈抵达雾港。", "关键事件：",
                        "- 林澈前往旧渡口", "- 林澈决定追查残页来源",
                        "未解决问题：", "结尾钩子：染血船票指向旧渡口。")
                .doesNotContain("主角目标", "实际结果", "付出代价", "人物变化");
        assertThat(reviewPrompt).doesNotContain("人物当前状态", "人物运行时状态", "角色状态快照");
        assertThat(revisePrompt).doesNotContain("人物当前状态", "人物运行时状态", "角色状态快照");
        assertThat(draftPrompt).satisfies(this::assertPowerSystemRendered);
        assertThat(reviewPrompt).satisfies(this::assertPowerSystemRendered);
        System.out.println("正文 Prompt 已统一使用业务文本、中文状态和列表格式");
    }

    @Test
    void reviewPromptShouldUseOnlyRelevantBoundedContext() {
        ChapterContextAggregate context = readyContext();
        context.getChapterPlan().setChapterNumber(3);
        context.setArc(new OutlineNodeVO(
                "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                "旧渡口追查", "本章概要", 3, 3, "READY"
        ));
        context.getChapterPlan().setTitle("不应使用的计划标题");
        context.getChapterPlan().setSummary("林澈查找染血船票的来历。");
        context.setStoryBible(StoryBibleEntity.builder()
                .hardRulesJson("[\"死者不能复生\"]")
                .worldBackground("雾港由七家商会共治。")
                .styleGuide("第三人称限知。")
                .build());
        context.setCharacters(List.of(
                StoryCharacterEntity.builder().name("林澈").build(),
                StoryCharacterEntity.builder().name("顾言").build(),
                StoryCharacterEntity.builder().name("黑市商人").build(),
                StoryCharacterEntity.builder().name("旧守卫").build(),
                StoryCharacterEntity.builder().name("无关人物").build()
        ));
        context.setHistory(ChapterHistoryVO.builder()
                .recentMemories(List.of(
                        new ChapterMemoryVO(1, "历史-1", List.of(), List.of(), null),
                        new ChapterMemoryVO(2, "上一章摘要：林澈取得染血船票。",
                                List.of("林澈取得染血船票。"),
                                List.of("船票真正来源未明"),
                                "船票指向旧渡口。"),
                        new ChapterMemoryVO(3, "历史-3", List.of(), List.of(), null),
                        new ChapterMemoryVO(4, "历史-4", List.of(), List.of(), null),
                        new ChapterMemoryVO(5, "历史-5", List.of(), List.of(), null),
                        new ChapterMemoryVO(6, "历史-6", List.of(), List.of(), null),
                        new ChapterMemoryVO(7, "历史-7", List.of(), List.of(), null),
                        new ChapterMemoryVO(8, "历史-8", List.of(), List.of(), null)
                ))
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(2)
                        .content("上一章完整正文绝对不应进入 REVIEW。")
                        .build())
                .build());

        String prompt = new ReviewChapterNode(null)
                .buildReviewPrompt(context, "林澈走进旧渡口，检查染血船票。");

        System.out.printf(
                "REVIEW Context 收口：relatedCharacter=%s, previousFullText=%s, unrelatedHistory=%s%n",
                prompt.contains("林澈") && !prompt.contains("顾言"),
                prompt.contains("上一章完整正文绝对不应进入 REVIEW。"),
                prompt.contains("历史-1") || prompt.contains("历史-3")
        );
        assertThat(prompt)
                .contains("旧渡口追查", "林澈查找染血船票的来历。", "林澈走进旧渡口，检查染血船票。")
                .contains("不可违反的硬规则：\n- 死者不能复生")
                .contains("世界背景：雾港由七家商会共治。", "文风指南：第三人称限知。")
                .contains("摘要：上一章摘要：林澈取得染血船票。")
                .contains("关键事件：\n- 林澈取得染血船票。",
                        "未解决问题：\n- 船票真正来源未明",
                        "结尾钩子：船票指向旧渡口。")
                .doesNotContain(
                        "上一章完整正文绝对不应进入 REVIEW。", "上一章正文", "直接上一章正文",
                        "历史-1", "历史-3", "历史-4", "历史-5", "历史-6", "历史-7", "历史-8",
                        "顾言", "黑市商人", "旧守卫", "无关人物", "远方",
                        "事实类型", "主体", "谓词", "客体", "相关确认 Facts"
                );
        System.out.println("REVIEW 只保留当前章、上一章压缩记忆及相关人物");
    }

    private boolean hasStaticCharacterContext(String prompt) {
        return prompt.contains("角色定位：主角")
                && prompt.contains("性别：男")
                && prompt.contains("背景故事：曾在雾港长大")
                && !prompt.contains("生存状态：")
                && !prompt.contains("当前状态：")
                && !prompt.contains("仅来自旧状态");
    }

    private void assertStaticCharacterContextRendered(String prompt) {
        assertThat(prompt)
                .contains("林澈", "角色定位：主角", "性别：男", "年龄：二十七岁",
                        "外貌：眉眼清冷，常穿旧青衫", "性格：谨慎坚韧",
                        "背景故事：曾在雾港长大", "作者备注：优先保持其克制的说话方式")
                .doesNotContain(
                        "char-lin-che", "currentStateJson", "[\"位置\"", "{\"位置\"",
                        "[\"林澈前往旧渡口\"", "[\"死者不能复生\"]", "{\"name\":\"灵脉\"}",
                        "生存状态：", "当前状态：", "ALIVE", "DEAD", "MISSING", "UNKNOWN"
                );
    }

    private void assertPowerSystemRendered(String prompt) {
        assertThat(prompt)
                .contains("力量体系：\n- 名称：灵脉", "- 说明：以潮汐引动灵力",
                        "- 等级/境界：锻体、凝气", "- 补充设定：消耗体温")
                .doesNotContain("{\"name\":\"灵脉\"}");
    }

    @Test
    void reviewSystemPromptShouldRequireExactDraftEvidence() {
        assertThat(REVIEW_SYSTEM_PROMPT)
                .contains("最短连续正文原文")
                .contains("不做改写、概括或位置说明");
    }

    @Test
    void revisePromptShouldUseBusinessSeverityLabels() {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(
                        ReviewIssueVO.builder().severity(SeverityEnum.BLOCKER).build(),
                        ReviewIssueVO.builder().severity(SeverityEnum.MAJOR).build(),
                        ReviewIssueVO.builder().severity(SeverityEnum.MINOR).build()))
                .build();
        String prompt = new ReviseChapterNode(null).buildRevisePrompt(
                readyContext(), "待修正文", report);
        System.out.println("返修 Prompt 中文等级检查：\n" + prompt);
        assertThat(prompt).contains("严重程度：严重", "严重程度：一般", "严重程度：轻微")
                .doesNotContain("BLOCKER", "MAJOR", "MINOR");
        System.out.println("返修等级全部使用业务名称");
    }

    @Test
    void reviseNodeShouldWriteOnlyRevisedDraftNodeUpdate() throws Exception {
        ChapterContextAggregate context = readyContext();
        ReviewReportVO report = new ReviewReportVO();
        ReviseChapterNode node = new ReviseChapterNode(modelReturning(" revised draft "));

        Map<String, Object> update = asNode(node).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.DRAFT, "draft",
                ChapterGraphKeys.REVIEW_REPORT, report
        )));

        assertNodeUpdate(update, ChapterGraphKeys.DRAFT, "revised draft", "REVISE");
        assertPrivateMethod(
                ReviseChapterNode.class,
                "revise",
                ChapterContextAggregate.class,
                String.class,
                ReviewReportVO.class
        );
    }

    @Test
    void reviseNodeShouldIncludeHumanRevisionInstructionInPrompt() {
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<String> userPrompt = new AtomicReference<>();
        IChapterModelPort model = new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String prompt) {
                return Flux.empty();
            }

            @Override
            public String call(String modelSystemPrompt, String prompt) {
                systemPrompt.set(modelSystemPrompt);
                userPrompt.set(prompt);
                return "revised draft";
            }

            @Override
            public <T> T call(
                    String systemPrompt,
                    String prompt,
                    Class<T> responseType
            ) {
                throw new UnsupportedOperationException();
            }
        };

        Map<String, Object> update = new ReviseChapterNode(model).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, readyContext(),
                ChapterGraphKeys.DRAFT, "draft",
                ChapterGraphKeys.REVIEW_REPORT, new ReviewReportVO(),
                "revisionInstruction", "加强环境压迫感"
        )));

        assertThat(userPrompt.get())
                .contains("## 人工修改指令")
                .contains("加强环境压迫感");
        assertThat(systemPrompt.get())
                .contains("人工修改指令")
                .contains("无需审稿证据")
                .contains("不得违反故事设定");
        assertThat(update)
                .containsEntry(ChapterGraphKeys.REVISION_INSTRUCTION, "");
    }

    @Test
    void compressionNodeShouldWriteOnlyCompressionNodeUpdate() throws Exception {
        ChapterContextAggregate context = readyContext();
        context.setCharacters(List.of(StoryCharacterEntity.builder()
                .characterCode("char-main")
                .name("主角")
                .build()));
        CompressChapterNode node = new CompressChapterNode(modelReturning(new ChapterMemoryResponse(
                "摘要", List.of("结果"),
                List.of("问题"), "钩子",
                new StoryStateSnapshot(
                        List.of("染血船票"),
                        List.of("左臂受伤"),
                        List.of("知道徽记来自议会"),
                        List.of("仍在旧渡口")))));

        Map<String, Object> update = asNode(node).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.CHAPTER_NUMBER, 3,
                ChapterGraphKeys.DRAFT, "final draft"
        )));

        ChapterMemoryVO actual = (ChapterMemoryVO) update.get(ChapterGraphKeys.MEMORY);
        assertNodeUpdate(update, ChapterGraphKeys.MEMORY, actual, "COMPRESSION");
        assertThat(actual.getChapterNumber()).isEqualTo(3);
        assertThat(actual.getKeyEvents()).containsExactly("结果");
        assertThat(actual.getUnresolved()).containsExactly("问题");
        assertThat(update.get(ChapterGraphKeys.STORY_STATE_SNAPSHOT))
                .isEqualTo(new StoryStateSnapshot(
                        List.of("染血船票"),
                        List.of("左臂受伤"),
                        List.of("知道徽记来自议会"),
                        List.of("仍在旧渡口")));
        assertPrivateMethod(
                CompressChapterNode.class,
                "compress",
                ChapterContextAggregate.class,
                int.class,
                String.class
        );
    }

    @Test
    void compressionPromptShouldContainOnlyCurrentFinalChapter() {
        ChapterContextAggregate context = readyContext();
        context.setHistory(ChapterHistoryVO.builder()
                .recentMemories(List.of(new ChapterMemoryVO(
                        1,
                        "历史记忆不应进入压缩",
                        List.of("历史事件不应进入压缩"),
                        List.of("历史问题不应进入压缩"),
                        "历史钩子不应进入压缩")))
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(2)
                        .content("上一章完整正文不应进入压缩")
                        .build())
                .build());

        String prompt = new CompressChapterNode(null)
                .buildCompressionPrompt(context, 3, "当前最终正文");
        System.out.printf(
                "COMPRESSION 历史边界：currentContent=%s, historicalContentLeaked=%s%n",
                prompt.contains("当前最终正文"),
                prompt.contains("上一章完整正文不应进入压缩")
                        || prompt.contains("历史记忆不应进入压缩")
        );
        assertThat(prompt)
                .contains("章节号：3", "当前最终正文")
                .doesNotContain("上一章完整正文不应进入压缩", "历史记忆不应进入压缩",
                        "历史事件不应进入压缩", "历史问题不应进入压缩", "历史钩子不应进入压缩");
    }

    @Test
    void compressionPromptShouldCarryPreviousStateWithoutCarryingPreviousStory() {
        ChapterContextAggregate context = readyContext();
        context.setHistory(ChapterHistoryVO.builder()
                .storyStateSnapshot(new StoryStateSnapshot(
                        List.of("上一章留下的钥匙"), List.of(), List.of(), List.of("旧渡口")))
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(2)
                        .content("上一章正文不应进入压缩")
                        .build())
                .build());

        String prompt = new CompressChapterNode(null)
                .buildCompressionPrompt(context, 3, "当前最终正文");

        System.out.println("COMPRESSION 状态输入边界：继承快照，不带入上一章正文");
        assertThat(prompt)
                .contains("## 本章开始前的当前有效状态", "上一章留下的钥匙", "旧渡口")
                .contains("## 当前章节完整正文", "当前最终正文")
                .doesNotContain("上一章正文不应进入压缩");
    }

    @Test
    void compressionStateShouldCarryCurrentArticleFactsIntoNextDraft() {
        ChapterContextAggregate context = readyContext();
        StoryStateSnapshot expected = new StoryStateSnapshot(
                List.of("沈夜：四粒尸核已全部兑换，当前没有尸核"),
                List.of("沈夜：首次接触并使用灵息，但尚未正式掌握修炼体系"),
                List.of("沈夜：尚不知道“炼气初阶”这一术语"),
                List.of("苏晚晴：尚未正式与沈夜见面")
        );
        ChapterMemoryResponse response = new ChapterMemoryResponse(
                "本章完成尸核兑换并首次接触灵息。",
                List.of("沈夜兑换四粒尸核并首次使用灵息。"),
                List.of(),
                "灵息的来源仍待确认",
                expected
        );

        Map<String, Object> compressionUpdate = new CompressChapterNode(modelReturning(response)).apply(state(Map.of(
                ChapterGraphKeys.CONTEXT, context,
                ChapterGraphKeys.CHAPTER_NUMBER, 1,
                ChapterGraphKeys.DRAFT, "当前章节最终正文：沈夜兑换四粒尸核并首次接触灵息。"
        )));
        StoryStateSnapshot actual = (StoryStateSnapshot) compressionUpdate.get(
                ChapterGraphKeys.STORY_STATE_SNAPSHOT);
        context.setHistory(ChapterHistoryVO.builder()
                .storyStateSnapshot(actual)
                .previousChapter(PreviousChapterVO.builder()
                        .chapterNumber(1)
                        .content("当前章节最终正文：沈夜兑换四粒尸核并首次接触灵息。")
                        .build())
                .build());
        context.getChapterPlan().setChapterNumber(2);

        String nextDraftPrompt = new DraftChapterNode(null).buildDraftUserPrompt(context);

        System.out.printf(
                "章节状态回归：compressionState=%s, nextDraftHasAllState=%s, forbiddenHintsAbsent=%s%n",
                actual,
                nextDraftPrompt.contains(expected.resources().get(0))
                        && nextDraftPrompt.contains(expected.abilities().get(0))
                        && nextDraftPrompt.contains(expected.knowledge().get(0))
                        && nextDraftPrompt.contains(expected.presence().get(0)),
                !nextDraftPrompt.contains("无来源掏出尸核")
                        && !nextDraftPrompt.contains("无来源自称炼气初阶")
                        && !nextDraftPrompt.contains("体内晶核")
                        && !nextDraftPrompt.contains("苏晚晴毫无前置地已在队伍中")
        );
        assertThat(actual).isEqualTo(expected);
        assertThat(nextDraftPrompt)
                .contains("## 当前有效状态")
                .contains(expected.resources().get(0), expected.abilities().get(0),
                        expected.knowledge().get(0), expected.presence().get(0))
                .doesNotContain("无来源掏出尸核", "无来源自称炼气初阶", "体内晶核",
                        "苏晚晴毫无前置地已在队伍中");
    }

    @Test
    void compressionNodeShouldKeepBusinessValidationAsE0006() {
        ChapterMemoryResponse response = new ChapterMemoryResponse(
                "", List.of(), List.of(), "钩子", null);

        AppException exception = catchThrowableOfType(
                () -> new CompressChapterNode(modelReturning(response)).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, readyContext(),
                        ChapterGraphKeys.CHAPTER_NUMBER, 1,
                        ChapterGraphKeys.DRAFT, "正文"
                ))),
                AppException.class
        );

        assertThat(exception.getCode()).isEqualTo(ResponseCode.E0006.getCode());
        assertThat(exception.getInternalDetail()).contains("SCHEMA_VALIDATION_FAILED", "shortSummary 不能为空");
        System.out.println("COMPRESSION 业务校验错误保留为：" + exception.getCode());
    }

    @Test
    void compressionNodeShouldOnlyReturnChapterCompression() {
        ChapterContextAggregate context = readyContext();
        context.setCharacters(List.of(StoryCharacterEntity.builder()
                .characterCode("lin-che")
                .name("林澈")
                .build()));
        ChapterMemoryResponse response = new ChapterMemoryResponse(
                "摘要", List.of("结果"), List.of(), "钩子", null);
        Map<String, Object> update = new CompressChapterNode(modelReturning(response)).apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, context,
                        ChapterGraphKeys.CHAPTER_NUMBER, 1,
                        ChapterGraphKeys.DRAFT, "正文"
                )));
        ChapterMemoryVO actual = (ChapterMemoryVO) update.get(ChapterGraphKeys.MEMORY);
        System.out.printf("章节压缩结果不含原子事实：%s%n", actual);
        assertThat(actual.getKeyEvents()).containsExactly("结果");
        assertThat(actual.getShortSummary()).isEqualTo("摘要");
    }

    private ChapterContextAggregate readyContext() {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("test-project").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .outlineNodeCode("ARC_001")
                        .chapterNumber(1)
                        .title("旧渡口")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_001", "VOL_001", OutlineNodeKindEnum.ARC, 1,
                        "旧渡口", "本章概要", 1, 1, "READY"
                ))
                .characters(List.of())
                .build();
    }

    private ChapterContextAggregate contextValidatedByLoadNode() {
        return ChapterContextAggregate.builder()
                .project(NovelProjectEntity.builder().projectCode("test-project").build())
                .storyBible(StoryBibleEntity.builder().build())
                .chapterPlan(ChapterPlanEntity.builder()
                        .outlineNodeCode("ARC_002")
                        .chapterNumber(2)
                        .title("第二章")
                        .status("READY")
                        .build())
                .arc(new OutlineNodeVO(
                        "ARC_002", "VOL_001", OutlineNodeKindEnum.ARC, 2,
                        "第二章", "本章概要", 2, 2, "READY"
                ))
                .characters(List.of())
                .build();
    }

    private IChapterModelPort modelReturning(Object result) {
        return new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return result instanceof String value
                        ? Flux.just(value)
                        : Flux.empty();
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                return (String) result;
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                return responseType.cast(result);
            }
        };
    }

    private IChapterModelPort modelCapturingPrompt(
            AtomicReference<String> userPrompt,
            String result
    ) {
        return new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String prompt) {
                userPrompt.set(prompt);
                return Flux.just(result);
            }

            @Override
            public String call(String systemPrompt, String prompt) {
                userPrompt.set(prompt);
                return result;
            }

            @Override
            public <T> T call(String systemPrompt, String prompt, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private IChapterModelPort modelReturningAfterTransientFailure(
            String result,
            AtomicInteger calls
    ) {
        return new IChapterModelPort() {
            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                if (calls.incrementAndGet() == 1) {
                    return Flux.error(AppException.internal(ResponseCode.E0001.getCode(), "temporary"));
                }
                return Flux.just(result);
            }

            @Override
            public String call(String systemPrompt, String userPrompt) {
                if (calls.incrementAndGet() == 1) {
                    throw AppException.internal(ResponseCode.E0001.getCode(), "temporary");
                }
                return result;
            }

            @Override
            public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void assertPrivateMethod(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        assertThat(Modifier.isPrivate(method.getModifiers())).isTrue();
    }

    private NodeAction<ChapterGraphState> asNode(NodeAction<ChapterGraphState> node) {
        return node;
    }

    private ChapterGraphState state(Map<String, Object> data) {
        return new ChapterGraphState(data);
    }

    private void assertNodeUpdate(
            Map<String, Object> update,
            String resultKey,
            Object expectedResult,
            String nodeName
    ) {
        assertThat(update)
                .containsKeys(
                        resultKey,
                        ChapterGraphKeys.CURRENT_NODE,
                        ChapterGraphKeys.COMPLETED_STAGES,
                        ChapterGraphKeys.RETRY_COUNT
                )
                .containsEntry(resultKey, expectedResult)
                .containsEntry(ChapterGraphKeys.CURRENT_NODE, nodeName)
                .containsEntry(ChapterGraphKeys.COMPLETED_STAGES, List.of(nodeName))
                .containsEntry(ChapterGraphKeys.RETRY_COUNT, 0);
        if (ChapterGraphKeys.MEMORY.equals(resultKey)) {
            assertThat(update).containsKey(ChapterGraphKeys.STORY_STATE_SNAPSHOT);
        } else {
            assertThat(update).doesNotContainKey(ChapterGraphKeys.STORY_STATE_SNAPSHOT);
        }
    }

    private void assertMissingState(ThrowingCallable invocation, String expectedMessage) {
        AppException exception = catchThrowableOfType(invocation, AppException.class);

        assertThat(exception.getCode()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
        assertThat(exception.getInternalDetail()).isEqualTo(expectedMessage);
    }

    private void assertInvalidReviewIssue(
            ReviewIssueVO issue,
            String draft,
            String expectedField
    ) {
        ReviewReportVO report = ReviewReportVO.builder()
                .reviewIssueVOList(List.of(issue))
                .build();
        Map<String, Object> update = new ReviewChapterNode(modelReturning(report))
                .apply(state(Map.of(
                        ChapterGraphKeys.CONTEXT, readyContext(),
                        ChapterGraphKeys.DRAFT, draft
                )));

        assertThat(update)
                .containsEntry(
                        ChapterGraphKeys.WORKFLOW_STATUS,
                        ChapterWorkflowStatusEnum.REVIEW_FAILED.name()
                )
                .containsEntry(
                        ChapterGraphKeys.RETRY_COUNT,
                        ChapterModelRetryExecutor.MAX_RETRIES
                );
        String failureMessage = (String) update.get(ChapterGraphKeys.FAILURE_MESSAGE);
        assertThat(failureMessage)
                .isEqualTo(ResponseCode.E0004.getMessage())
                .doesNotContain("evidence", "JSON", "reviewIssueVOList");
        System.out.printf(
                "非法 REVIEW 字段进入可恢复状态：field=%s, status=%s%n",
                expectedField,
                update.get(ChapterGraphKeys.WORKFLOW_STATUS)
        );
    }
}
