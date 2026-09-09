package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.adapter.port.IPlanningModelPort;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningDraftRepository;
import cn.ninth.novel.domain.planning.adapter.repository.IPlanningRepository;
import cn.ninth.novel.domain.planning.model.valobj.OutlineNodeVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.RootOutlineDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.enums.OutlineNodeKindEnum;
import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import org.junit.jupiter.api.Test;
import cn.ninth.novel.types.exception.AppException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RootOutlineGenerationTest {

    @Test
    void shouldBuildRootNodeFromProjectContextAndSaveRootOutlineDraft() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingDraftRepository drafts = new RecordingDraftRepository();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        PlanningService service = new PlanningService(
                model, drafts, repository
        );
        String requirement = "主角从边城查明灭门真相，最终守护天下";

        PlanningDraftVO result = service.generateRootOutline(
                "novel-001", requirement
        );

        System.out.printf(
                "root outline draft: type=%s, node=%s, range=%d-%d%n",
                result.draftType(),
                ((OutlineNodeVO) result.payload()).nodeCode(),
                ((OutlineNodeVO) result.payload()).startChapter(),
                ((OutlineNodeVO) result.payload()).endChapter()
        );
        assertThat(model.responseTypes).containsExactly(RootOutlineDraftVO.class);
        assertThat(PlanningPrompts.ROOT_OUTLINE_SYSTEM)
                .contains("\"summary\"")
                .doesNotContain("\"title\"");
        assertThat(model.userPrompts).singleElement().asString()
                .contains("【项目】", "标题：归途", "题材：东方奇幻", "预计章节数：120")
                .contains("【创作方向】", "一句话故事：少年背负灭门之谜",
                        "核心主题：人在真相前的选择", "主要矛盾：主角与幕后势力的长期对抗",
                        "结局方向：主角守护众生并完成自我和解")
                .contains("【世界背景】", "边城与诸国共存的奇幻大陆")
                .contains("【已确认核心人物】", "以下人物设定属于既定事实。",
                        "不得修改姓名、身份、核心背景、主要关系",
                        "不得将已有角色替换为新角色承担同一核心职责",
                        "大纲必须围绕已有核心人物展开",
                        "可以安排角色发展，但不能把未来发展写成初始既定事实",
                        "如剧情需要，可引入尚未定义的次要人物，但不要为其建立详细人物档案",
                        "name：沈砚；角色：男主角；personality：谨慎坚韧")
                .contains("【用户补充要求】", requirement)
                .doesNotContain("故事圣经=", "人物=", "NovelProjectVO", "StoryBibleVO",
                        "projectCode=", "status=DRAFT", "gender=", "backgroundStory=",
                        "powerSystemJson", "hardRulesJson", "styleGuide",
                        "灵力来自记忆", "不得复活已死之人", "克制、递进、重视因果",
                        "roleType", "MALE_LEAD");
        assertThat(result.draftType()).isEqualTo("ROOT_OUTLINE");

        OutlineNodeVO root = (OutlineNodeVO) result.payload();
        assertThat(root).extracting(
                OutlineNodeVO::nodeCode,
                OutlineNodeVO::parentNodeCode,
                OutlineNodeVO::nodeKind,
                OutlineNodeVO::sequenceNo,
                OutlineNodeVO::title,
                OutlineNodeVO::summary,
                OutlineNodeVO::startChapter,
                OutlineNodeVO::endChapter,
                OutlineNodeVO::status
        ).containsExactly(
                "BOOK_001", null, OutlineNodeKindEnum.BOOK, 1,
                "归途", "主角查明灭门真相并守护天下的全书主线",
                1, 120, "PLANNED"
        );
        assertThat(drafts.savedTypes).containsExactly("ROOT_OUTLINE");
        assertThat(repository.findProjectCalls).containsExactly("novel-001");
        assertThat(repository.findBibleCalls).containsExactly("novel-001");
        assertThat(repository.findCharactersCalls).containsExactly("novel-001");
    }

    @Test
    void shouldAllowMissingRootRequirementAndNormalizeItToEmptyText() {
        RecordingModelPort model = new RecordingModelPort();
        PlanningService service = new PlanningService(
                model, new RecordingDraftRepository(), new RecordingPlanningRepository()
        );

        service.generateRootOutline("novel-001", null);

        String prompt = model.userPrompts.get(0);
        int requirementStart = prompt.indexOf("【用户补充要求】");
        assertThat(requirementStart).isGreaterThanOrEqualTo(0);
        System.out.printf("root outline without requirement: %s%n", prompt.substring(requirementStart));
        assertThat(prompt).contains("【用户补充要求】").doesNotContain("null");
    }

    @Test
    void shouldRejectRootOutlineGenerationWithoutConfirmedCharacters() {
        RecordingModelPort model = new RecordingModelPort();
        RecordingPlanningRepository repository = new RecordingPlanningRepository();
        repository.characters = List.of();
        PlanningService service = new PlanningService(
                model, new RecordingDraftRepository(), repository
        );

        System.out.println("未确认核心人物时，RootOutline 应在模型调用前拒绝");
        assertThatThrownBy(() -> service.generateRootOutline("novel-001", ""))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getUserMessage())
                                .isEqualTo("请先创建并确认核心人物，再生成故事大纲"));
        assertThat(model.userPrompts).isEmpty();
        System.out.println("RootOutline 前置检查已拦截模型调用，提示：请先创建并确认核心人物，再生成故事大纲");
    }

    private static final class RecordingModelPort implements IPlanningModelPort {
        private final List<Class<?>> responseTypes = new ArrayList<>();
        private final List<String> userPrompts = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
            responseTypes.add(responseType);
            userPrompts.add(userPrompt);
            if (responseType == RootOutlineDraftVO.class) {
                return (T) new RootOutlineDraftVO(
                        "主角查明灭门真相并守护天下的全书主线"
                );
            }
            throw new AssertionError("unexpected response type: " + responseType);
        }
    }

    private static final class RecordingDraftRepository
            implements IPlanningDraftRepository {
        private final List<String> savedTypes = new ArrayList<>();

        @Override
        public PlanningDraftVO save(String projectCode, String draftType, Object payload) {
            savedTypes.add(draftType);
            return new PlanningDraftVO(
                    "draft-root", projectCode, draftType, payload,
                    Instant.parse("2026-08-28T01:00:00Z")
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

    private static final class RecordingPlanningRepository
            implements IPlanningRepository {
        private final List<String> findProjectCalls = new ArrayList<>();
        private final List<String> findBibleCalls = new ArrayList<>();
        private final List<String> findCharactersCalls = new ArrayList<>();
        private List<StoryCharacterVO> characters = List.of(new StoryCharacterVO(
                "CHAR_001", "沈砚", "MALE_LEAD", "MALE", "二十岁",
                "眉眼清冷", "谨慎坚韧", "边城遗孤", "不轻易许诺",
                null, "ALIVE", "ACTIVE"
        ));

        @Override
        public NovelProjectVO findProject(String projectCode) {
            findProjectCalls.add(projectCode);
            return new NovelProjectVO(
                    projectCode, "归途", "东方奇幻", 120,
                    2500, 0, "DRAFT"
            );
        }

        @Override
        public StoryBibleVO findBible(String projectCode) {
            findBibleCalls.add(projectCode);
            return new StoryBibleVO(
                    "少年背负灭门之谜", "人在真相前的选择", "主角与幕后势力的长期对抗",
                    "主角守护众生并完成自我和解", "边城与诸国共存的奇幻大陆",
                    "灵力来自记忆", "不得复活已死之人", "克制、递进、重视因果", "CONFIRMED"
            );
        }

        @Override
        public List<StoryCharacterVO> findCharacters(String projectCode) {
            findCharactersCalls.add(projectCode);
            return characters;
        }

        @Override
        public OutlineNodeVO findOutline(String projectCode, String nodeCode) {
            return null;
        }

        @Override
        public List<OutlineNodeVO> listOutlines(String projectCode) {
            return List.of();
        }

        @Override
        public boolean hasChildren(String projectCode, String nodeCode) {
            return false;
        }

        @Override
        public boolean hasChapterPlans(String projectCode, String nodeCode) {
            return false;
        }
    }
}
