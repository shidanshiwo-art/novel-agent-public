package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterMemoryVO;
import cn.ninth.novel.domain.memory.model.MemoryContextCategory;
import cn.ninth.novel.domain.memory.model.MemoryContextItem;
import cn.ninth.novel.domain.memory.model.MemoryMode;
import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.ChapterPlanDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.ChapterOutlineVO;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterPlanSingleGenerationTest {

    @Test
    void shouldGenerateOneChapterPlanFromCompressedContextAndAssignMetadata() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = new PlanningService(model, drafts, repository);

        PlanningDraftVO result = service.generateChapterPlan(
                "novel-001", 17, "本章强化宗门大比冲突"
        );

        ChapterOutlineVO payload = (ChapterOutlineVO) result.payload();
        String prompt = model.userPrompts.get(0);
        System.out.printf(
                "single chapter plan draft: type=%s, chapter=%d, node=%s%n",
                result.draftType(), payload.chapterNumber(), payload.outlineNodeCode()
        );
        System.out.printf("single chapter plan prompt sections: %s%n",
                prompt.lines().filter(line -> line.startsWith("【")).toList());

        assertThat(model.responseTypes).containsExactly(ChapterPlanDraftVO.class);
        assertThat(result.draftType()).isEqualTo("CHAPTER_PLAN");
        assertThat(payload).extracting(
                ChapterOutlineVO::chapterNumber,
                ChapterOutlineVO::outlineNodeCode,
                ChapterOutlineVO::title,
                ChapterOutlineVO::summary,
                ChapterOutlineVO::status
        ).containsExactly(
                17, "ARC_002", "宗门大比", "林渊在上一章结果基础上迎战赵无极并取得阶段性突破", "PLANNED"
        );
        assertThat(prompt)
                .contains(
                        "【当前章节】", "第 17 章",
                        "【Story Bible】", "失忆剑客寻找真相", "自我与城市的选择",
                        "林渊与守密势力的冲突", "公开失忆真相", "雾城",
                        "特殊体系：灵力来自记忆", "写作风格：克制",
                        "【当前剧情段】", "宗门大比",
                        "【当前章节标题】", "当前章节标题已经确定：宗门大比",
                        "不得修改该标题；本次只生成本章写作执行计划。",
                        "【上层方向】", "上级大纲：", "全书方向：", "当前卷方向", "全书寻找失忆真相",
                        "【当前位置】", "当前剧情段第 7/10 章",
                         "赵无极上一章负伤后退场",
                        "【相关人物】", "林渊", "赵无极", "角色：男主角",
                        "【近期剧情记忆】", "第 16 章",
                        "摘要：赵无极上一章负伤后退场",
                        "关键事件：", "赵无极在擂台负伤退场",
                        "未解决问题：", "宗门大比是否继续",
                        "结尾钩子：影步暴露",
                        "【硬规则】", "影步不能在强光下使用",
                        "【补充要求】", "本章强化宗门大比冲突"
                )
                .doesNotContain(
                        "【上一章】", "当前状态：",
                        "outlineNodeCode", "chapterNumber", "status",
                        "startChapter", "endChapter", "sequenceNo",
                        "完整人物背景", "完整人物外貌", "完整人物备注",
                        "BOOK_001", "VOL_001", "ARC_002", "本卷：", "全书："
                )
                .doesNotContain("MALE_LEAD", "FEMALE_LEAD", "VILLAIN", "SUPPORTING",
                        "事实类型", "主体", "谓词", "客体",
                        "factType", "subject", "predicate", "object");
        assertThat(drafts.savedTypes).containsExactly("CHAPTER_PLAN");
        assertThat(repository.recentMemoryLimits).containsExactly(5);
        assertThat(ChapterPlanDraftVO.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("title", "summary");
    }

    @Test
    void shouldNaturallyContinueWhenRequirementIsAbsent() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = new PlanningService(model, drafts, repository);

        service.generateChapterPlan("novel-001", 17, null);
        String prompt = model.userPrompts.get(0);

        System.out.printf("chapter plan without requirement prompt tail: %s%n",
                prompt.substring(prompt.indexOf("【补充要求】")));
        assertThat(prompt)
                .contains("【Story Bible】", "【当前剧情段】",
                        "【近期剧情记忆】", "【相关人物】",
                        "【补充要求】",
                        "无（按当前 Story Bible、当前大纲和前文自然生成下一章计划）")
                .doesNotContain("本章额外创作偏好：");
        assertThat(model.calls).isEqualTo(1);
        assertThat(drafts.savedTypes).containsExactly("CHAPTER_PLAN");
    }

    @Test
    void shouldRenderPreviousSummaryOnlyWhenPreviousChapterMemoryIsUnavailable() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.recentMemories = List.of();
        PlanningService service = new PlanningService(model, drafts, repository);

        service.generateChapterPlan("novel-001", 17, "需求");
        String prompt = model.userPrompts.get(0);

        System.out.printf("ChapterPlan previous summary fallback rendered=%s%n",
                prompt.contains("【上一章】\n赵无极上一章负伤后退场"));
        assertThat(prompt)
                .contains("【上一章】\n赵无极上一章负伤后退场")
                .contains("【近期剧情记忆】\n无");
    }

    @Test
    void shouldPreferPlanMemoryContextProviderForLongTermCandidates() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.memoryContextItems = List.of(
                MemoryContextItem.of(
                        "open-loop-chapter-7",
                        MemoryContextCategory.OPEN_LOOPS,
                        "残晶来源仍未解决",
                        7),
                MemoryContextItem.of(
                        "consolidated-chapter-7",
                        MemoryContextCategory.CONSOLIDATED,
                        "第7章确认残晶与北港旧案有关",
                        7)
        );
        PlanningService service = new PlanningService(model, drafts, repository);

        service.generateChapterPlan("novel-001", 17, "保留远期伏笔");
        String prompt = model.userPrompts.get(0);

        System.out.printf("PLAN MemoryContextProvider 长期召回：%s%n",
                prompt.lines()
                        .filter(line -> line.contains("残晶") || line.contains("PLAN 记忆"))
                        .toList());
        assertThat(prompt)
                .contains("【PLAN 记忆上下文】", "第7章：残晶来源仍未解决",
                        "第7章：第7章确认残晶与北港旧案有关")
                .doesNotContain("【近期剧情记忆】");
    }

    @Test
    void shouldUseCanonicalMemoryForExplicitV1PlanWithoutLegacyBridge() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.canonicalMemoryContextItems = List.of(MemoryContextItem.canonical(
                "CANONICAL_FACT",
                "canonical-plan-state",
                MemoryContextCategory.CONSOLIDATED,
                "Canonical 计划状态",
                16,
                "chapter-v1",
                "PROJECTION_ACTIVE",
                null,
                1));
        repository.legacyMemoryContextItems = List.of(MemoryContextItem.bridge(
                "legacy-plan-state",
                MemoryContextCategory.CONSOLIDATED,
                "Legacy 计划状态",
                16));
        PlanningService service = new PlanningService(model, drafts, repository);

        service.generateChapterPlan("novel-001", 17, "需求", MemoryMode.V1);
        String prompt = model.userPrompts.get(0);

        System.out.printf(
                "PLAN V1 route: canonicalReads=%d, legacyBridgeReads=%d%n",
                repository.canonicalMemoryReads,
                repository.legacyBridgeReads);
        assertThat(prompt).contains("Canonical 计划状态")
                .doesNotContain("Legacy 计划状态", "【近期剧情记忆】");
        assertThat(repository.canonicalMemoryReads).isEqualTo(1);
        assertThat(repository.legacyBridgeReads).isZero();
        assertThat(repository.recentMemoryLimits).isEmpty();
    }

    @Test
    void shouldRejectInvalidChapterBeforeReadingContextOrCallingModel() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = new PlanningService(model, drafts, repository);

        assertThatThrownBy(() -> service.generateChapterPlan("novel-001", 0, "需求"))
                .isInstanceOf(RuntimeException.class);

        System.out.println("invalid chapter number rejected before context loading");
        assertThat(model.calls).isZero();
        assertThat(repository.listOutlinesCalls).isEmpty();
        assertThat(drafts.savedTypes).isEmpty();
    }

    @Test
    void shouldRejectChapterWithoutMostSpecificOutlineBeforeCallingModel() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.outlines = List.of(node(
                "BOOK_001", null, OutlineNodeKindEnum.BOOK,
                "全书", "全书方向", 1, 10
        ));
        PlanningService service = new PlanningService(model, drafts, repository);

        assertThatThrownBy(() -> service.generateChapterPlan("novel-001", 17, "需求"))
                .isInstanceOf(RuntimeException.class);

        System.out.println("chapter without a leaf outline rejected before model generation");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
    }

    @Test
    void shouldRejectChapterWithoutArcEvenWhenDefaultVolumeExists() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.outlines = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        "全书", "全书方向", 1, 300),
                node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                        "卷一", "", 1, 300)
        );
        PlanningService service = new PlanningService(model, drafts, repository);

        assertThatThrownBy(() -> service.generateChapterPlan("novel-001", 1, "需求"))
                .isInstanceOf(RuntimeException.class)
                .satisfies(error -> assertThat(error.toString()).contains("当前章节尚未创建章纲"));

        System.out.println("chapter plan generation requires an ARC under the real volume structure");
        assertThat(model.calls).isZero();
        assertThat(drafts.savedTypes).isEmpty();
    }

    @Test
    void shouldRenderGenericUpperDirectionWhenParentIsVolume() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.outlines = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        "全书", "全书方向", 1, 300),
                node("VOLUME_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                        "凡尘篇", "阶段方向", 1, 100),
                node("ARC_002", "VOLUME_001", OutlineNodeKindEnum.ARC,
                        "宗门大比", "当前剧情段", 11, 20)
        );
        PlanningService service = new PlanningService(model, drafts, repository);

        service.generateChapterPlan("novel-001", 17, "需求");
        String prompt = model.userPrompts.get(0);

        System.out.printf("volume-parent chapter prompt upper direction: %s%n",
                prompt.lines()
                        .filter(line -> line.startsWith("上级大纲：") || line.startsWith("全书方向："))
                        .toList());
        assertThat(prompt)
                .contains("【上层方向】", "上级大纲：", "凡尘篇", "全书方向：", "全书方向")
                .doesNotContain("本卷：", "全书：");
    }

    private static OutlineNodeVO node(
            String code,
            String parentCode,
            OutlineNodeKindEnum kind,
            String title,
            String summary,
            int start,
            int end
    ) {
        return new OutlineNodeVO(
                code, parentCode, kind, 1, title, summary, start, end, "READY"
        );
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private final List<Class<?>> responseTypes = new ArrayList<>();
        private final List<String> userPrompts = new ArrayList<>();
        private int calls;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            calls++;
            responseTypes.add(responseType);
            userPrompts.add(userPrompt);
            if (responseType == ChapterPlanDraftVO.class) {
                return (T) new ChapterPlanDraftVO(
                        "宗门大比的正面冲突",
                        "林渊在上一章结果基础上迎战赵无极并取得阶段性突破"
                );
            }
            throw new AssertionError("unexpected response type: " + responseType);
        }
    }

    private static final class RecordingDraftRepository implements IPlanningDraftRepository {
        private final List<String> savedTypes = new ArrayList<>();

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            savedTypes.add(draftType);
            return new PlanningDraftVO(
                    "draft-chapter-17", projectCode, draftType, payload,
                    Instant.parse("2026-08-29T01:00:00Z")
            );
        }

        @Override
        public Optional<PlanningDraftVO> find(String projectCode, String draftId) {
            return Optional.empty();
        }

        @Override
        public void remove(String projectCode, String draftId) {
        }

        @Override
        public void removeByProject(String projectCode) {
        }
    }

    private static final class RecordingPlanningRepository implements IPlanningRepository {
        private List<OutlineNodeVO> outlines = List.of(
                node("BOOK_001", null, OutlineNodeKindEnum.BOOK,
                        "全书", "全书寻找失忆真相", 1, 300),
                node("VOL_001", "BOOK_001", OutlineNodeKindEnum.VOLUME,
                        "青云宗卷", "当前卷方向", 1, 40),
                node("ARC_001", "VOL_001", OutlineNodeKindEnum.ARC,
                        "入宗", "主角进入青云宗", 1, 10),
                node("ARC_002", "VOL_001", OutlineNodeKindEnum.ARC,
                        "宗门大比", "林渊在宗门大比中与赵无极正面冲突", 11, 20)
        );
        private final List<String> listOutlinesCalls = new ArrayList<>();
        private final List<Integer> recentMemoryLimits = new ArrayList<>();
        private List<MemoryContextItem> memoryContextItems = List.of();
        private List<MemoryContextItem> canonicalMemoryContextItems = List.of();
        private List<MemoryContextItem> legacyMemoryContextItems = List.of();
        private int canonicalMemoryReads;
        private int legacyBridgeReads;
        private List<ChapterMemoryVO> recentMemories = List.of(
                new ChapterMemoryVO(
                        16,
                        "赵无极上一章负伤后退场",
                        List.of("赵无极在擂台负伤退场"),
                        List.of("宗门大比是否继续"),
                        "影步暴露"
                )
        );

        @Override
        public NovelProjectVO findProject(String projectCode) {
            return new NovelProjectVO(
                    projectCode, "失忆之城", "东方悬疑", 300,
                    3000, 16, "ACTIVE"
            );
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            return new StoryBibleVO(
                    "失忆剑客寻找真相", "自我与城市的选择", "林渊与守密势力的冲突",
                    "公开失忆真相", "雾城", "灵力来自记忆",
                    "[\"影步不能在强光下使用\",\"死者不能复生\"]",
                    "克制", "CONFIRMED"
            );
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            return List.of(
                    character("林渊", "MALE_LEAD", "ACTIVE", "完整人物背景", "完整人物外貌", "完整人物备注",
                            "{\"目标\":\"寻找失忆真相\",\"位置\":\"演武场\",\"状态\":\"警戒\"}"),
                    character("赵无极", "VILLAIN", "INACTIVE", "完整人物背景", "完整人物外貌", "完整人物备注",
                            "{\"目标\":\"夺取首席\",\"位置\":\"演武场\",\"状态\":\"负伤\"}"),
                    character("无关角色", "SUPPORTING", "ACTIVE", "完整人物背景", "完整人物外貌", "完整人物备注", "{}")
            );
        }

        @Override
        public String findPreviousChapterSummary(String projectCode, Integer chapterNumber) {
            return "赵无极上一章负伤后退场";
        }

        @Override
        public List<ChapterMemoryVO> findRecentChapterMemories(
                String projectCode,
                Integer chapterNumber,
                int limit
        ) {
            recentMemoryLimits.add(limit);
            return recentMemories;
        }

        @Override
        public List<MemoryContextItem> findMemoryContextItems(
                String projectCode,
                Integer chapterNumber
        ) {
            return memoryContextItems;
        }

        @Override
        public List<MemoryContextItem> findCanonicalMemoryContextItems(
                String projectCode,
                Integer chapterNumber
        ) {
            canonicalMemoryReads++;
            return canonicalMemoryContextItems;
        }

        @Override
        public List<MemoryContextItem> findLegacyMemoryContextItems(
                String projectCode,
                Integer chapterNumber
        ) {
            legacyBridgeReads++;
            return legacyMemoryContextItems;
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            listOutlinesCalls.add(projectCode);
            return outlines;
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return outlines.stream()
                    .filter(node -> nodeCode.equals(node.nodeCode()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return outlines.stream()
                    .anyMatch(node -> nodeCode.equals(node.parentNodeCode()));
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return false;
        }
    }

    private static StoryCharacterVO character(
            String name,
            String role,
            String status,
            String background,
            String appearance,
            String note,
            String currentStateJson
    ) {
        return new StoryCharacterVO(
                "character-" + name, name, role, "UNKNOWN", null,
                appearance, "性格", background, note, currentStateJson,
                "ALIVE", status
        );
    }

}
