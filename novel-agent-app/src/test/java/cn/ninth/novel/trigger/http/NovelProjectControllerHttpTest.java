package cn.ninth.novel.trigger.http;

import cn.ninth.novel.domain.project.model.valobj.GeneratedChapterVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftListVO;
import cn.ninth.novel.domain.project.model.valobj.CharacterDraftVO;
import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.domain.project.model.valobj.StoryBibleVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import cn.ninth.novel.domain.project.model.valobj.VolumeChapterGroupVO;
import cn.ninth.novel.domain.project.service.INovelProjectService;
import cn.ninth.novel.types.exception.AppException;
import cn.ninth.novel.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NovelProjectController.class)
@Import(NovelProjectControllerHttpTest.ControllerTestConfiguration.class)
@ActiveProfiles("test")
class NovelProjectControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private INovelProjectService projectService;

    @Test
    void shouldDeleteCharacterUsingDeleteMethod() throws Exception {
        StubNovelProjectService service = (StubNovelProjectService) projectService;
        service.deletedProjectCode = null;
        service.deletedCharacterCode = null;
        System.out.println("执行 DELETE 人物接口：novel-delete / char-delete");
        mockMvc.perform(delete("/api/v1/novels/projects/novel-delete/characters/char-delete"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(service.deletedProjectCode).isEqualTo("novel-delete");
        assertThat(service.deletedCharacterCode).isEqualTo("char-delete");
        System.out.println("DELETE 返回成功，Service 收到路径中的项目和人物编码");
    }

    @Test
    void shouldReturnCharacterDeletionRejectionReason() throws Exception {
        System.out.println("HTTP DELETE 遇到正式数据引用时，应向前端返回明确业务错误");
        mockMvc.perform(delete("/api/v1/novels/projects/novel-delete/characters/referenced-character"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()))
                .andExpect(jsonPath("$.info").value("角色被其他正式数据引用或受数据约束限制，无法删除；请先处理相关引用或约束"))
                .andExpect(jsonPath("$.data").doesNotExist());
        System.out.println("引用拒绝原因完整返回，前端可直接展示");
    }

    @Test
    void shouldCreateProject() throws Exception {
        mockMvc.perform(post("/api/v1/novels/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectCode": "novel-001",
                                  "title": "北境夜行",
                                  "genre": "玄幻",
                                  "targetChapterCount": 100,
                                  "wordsPerChapter": 2500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.projectCode").value("novel-001"))
                .andExpect(jsonPath("$.data.currentChapterNumber").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void shouldUpdateTargetChapterCountThroughPost() throws Exception {
        StubNovelProjectService service = (StubNovelProjectService) projectService;
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/target-chapter-count"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetChapterCount":120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.projectCode").value("novel-001"))
                .andExpect(jsonPath("$.data.targetChapterCount").value(120));
        assertThat(service.updatedTargetChapterCount).isEqualTo(120);
        System.out.println("HTTP 已通过 POST 调整预计章节数，并返回同步后的项目设置");
    }

    @Test
    void shouldSaveStoryBible() throws Exception {
        mockMvc.perform(post("/api/v1/novels/projects/novel-001/bible")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oneSentencePremise": "林渊调查父亲失踪真相",
                                  "coreTheme": "真相与代价",
                                  "worldBackground": "北境诸城",
                                  "powerSystemJson": "{\\\"realms\\\":[\\\"启灵\\\"]}",
                                  "hardRulesJson": "[\\\"能力必须付出代价\\\"]"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oneSentencePremise")
                        .value("林渊调查父亲失踪真相"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void shouldAddCharacter() throws Exception {
        mockMvc.perform(post("/api/v1/novels/projects/novel-001/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "林渊",
                                  "roleType": "MALE_LEAD",
                                  "gender": "MALE",
                                  "personality": "克制、谨慎"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characterCode").value("char-test"))
                .andExpect(jsonPath("$.data.name").value("林渊"))
                .andExpect(jsonPath("$.data.lifeStatus").value("ALIVE"))
                .andExpect(jsonPath("$.data.currentStateJson").value("{}"));
    }

    @Test
    void shouldGenerateCharacterDraft() throws Exception {
        StubNovelProjectService service = (StubNovelProjectService) projectService;
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/characters/generate"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredCount":4,"requirement":"男主偏理性，女主与核心谜案有关"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value("draft-character-001"))
                .andExpect(jsonPath("$.data.draftType").value("CHARACTERS"))
                .andExpect(jsonPath("$.data.payload.characters[0].name").value("林澈"))
                .andExpect(jsonPath("$.data.payload.characters[0].role").value("男主角"));
        assertThat(service.generatedCharacterPreferredCount).isEqualTo(4);
        assertThat(service.generatedCharacterRequirement)
                .isEqualTo("男主偏理性，女主与核心谜案有关");
        System.out.println("HTTP 人物生成请求已传递 preferredCount=4 和用户要求");
    }

    @Test
    void shouldUpdateCharacter() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/characters/lin-yuan"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "林渊·修订",
                                  "roleType": "MALE_LEAD",
                                  "gender": "MALE",
                                  "personality": "更克制、更谨慎"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characterCode").value("lin-yuan"))
                .andExpect(jsonPath("$.data.name").value("林渊·修订"))
                .andExpect(jsonPath("$.data.personality")
                        .value("更克制、更谨慎"));
    }

    @Test
    void shouldOverwriteGeneratedChapterContent() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapters/1"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "午夜钟楼·新题",
                                  "content": "林渊踏入钟楼，风声掠过残破的铜钟。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterNumber").value(1))
                .andExpect(jsonPath("$.data.title").value("午夜钟楼·新题"))
                .andExpect(jsonPath("$.data.content")
                        .value("林渊踏入钟楼，风声掠过残破的铜钟。"))
                .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    @Test
    void shouldResyncDirtyChapterDerivedDataThroughHttp() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapters/1/resync"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.chapterNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("FINALIZED"));
        System.out.println("HTTP 正文派生数据重新同步接口已接入项目章节路由");
    }

    @Test
    void shouldGetGeneratedChapter() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/novels/projects/novel-001/chapters/1"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterNumber").value(1))
                .andExpect(jsonPath("$.data.title").value("午夜钟楼"))
                .andExpect(jsonPath("$.data.content").value("第一章正文"));
    }

    @Test
    void shouldListGeneratedChapters() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/novels/projects/novel-001/chapters"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].chapterNumber").value(1))
                .andExpect(jsonPath("$.data[0].title").value("午夜钟楼"))
                .andExpect(jsonPath("$.data[1].chapterNumber").value(2))
                .andExpect(jsonPath("$.data[1].title").value("议会徽记"));
    }

    @Test
    void shouldListProjectsThroughPost() throws Exception {
        mockMvc.perform(post("/api/v1/novels/projects/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].projectCode")
                        .value("novel-002"))
                .andExpect(jsonPath("$.data[0].title").value("雾海长灯"))
                .andExpect(jsonPath("$.data[0].currentChapterNumber")
                        .value(8))
                .andExpect(jsonPath("$.data[1].projectCode")
                        .value("novel-001"));
    }

    @Test
    void shouldListChaptersGroupedByVolumeThroughPost() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/novels/projects/novel-001/chapters/list"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].volumeCode")
                        .value("VOL_001"))
                .andExpect(jsonPath("$.data[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.data[0].title").value("北境开篇"))
                .andExpect(jsonPath("$.data[0].startChapter").value(1))
                .andExpect(jsonPath("$.data[0].endChapter").value(30))
                .andExpect(jsonPath("$.data[0].chapters.length()").value(2))
                .andExpect(jsonPath("$.data[0].chapters[0].chapterNumber")
                        .value(1))
                .andExpect(jsonPath("$.data[0].chapters[1].chapterNumber")
                        .value(2));
    }

    @Test
    void shouldGetProjectProgress() throws Exception {
        mockMvc.perform(get("/api/v1/novels/projects/novel-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCode").value("novel-001"))
                .andExpect(jsonPath("$.data.currentChapterNumber").value(3))
                .andExpect(jsonPath("$.data.status").value("WRITING"));
    }

    @TestConfiguration
    static class ControllerTestConfiguration {

        @Bean
        INovelProjectService novelProjectService() {
            return new StubNovelProjectService();
        }
    }

    private static final class StubNovelProjectService
            implements INovelProjectService {

        private String deletedProjectCode;
        private String deletedCharacterCode;
        private Integer generatedCharacterPreferredCount;
        private String generatedCharacterRequirement;
        private Integer updatedTargetChapterCount;

        @Override
        public void deleteCharacter(String projectCode, String characterCode) {
            if ("referenced-character".equals(characterCode)) {
                throw AppException.user(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "角色被其他正式数据引用或受数据约束限制，无法删除；请先处理相关引用或约束"
                );
            }
            deletedProjectCode = projectCode;
            deletedCharacterCode = characterCode;
        }

        @Override
        public NovelProjectVO createProject(NovelProjectVO project) {
            return new NovelProjectVO(
                    project.projectCode(),
                    project.title(),
                    project.genre(),
                    project.targetChapterCount(),
                    project.wordsPerChapter(),
                    0,
                    "DRAFT"
            );
        }

        @Override
        public NovelProjectVO updateTargetChapterCount(
                String projectCode,
                Integer targetChapterCount
        ) {
            updatedTargetChapterCount = targetChapterCount;
            return new NovelProjectVO(
                    projectCode,
                    "北境夜行",
                    "玄幻",
                    targetChapterCount,
                    2500,
                    3,
                    "WRITING"
            );
        }

        @Override
        public StoryBibleVO saveBible(String projectCode, StoryBibleVO bible) {
            return new StoryBibleVO(
                    bible.oneSentencePremise(),
                    bible.coreTheme(),
                    bible.mainConflict(),
                    bible.endingDirection(),
                    bible.worldBackground(),
                    bible.powerSystemJson(),
                    bible.hardRulesJson(),
                    bible.styleGuide(),
                    "CONFIRMED"
            );
        }

        @Override
        public StoryBibleVO getBible(String projectCode) {
            return null;
        }

        @Override
        public PlanningDraftVO generateStoryBible(
                String projectCode, String requirement
        ) {
            return null;
        }

        @Override
        public PlanningDraftVO generateCharacters(
                String projectCode,
                Integer preferredCount,
                String requirement
        ) {
            generatedCharacterPreferredCount = preferredCount;
            generatedCharacterRequirement = requirement;
            return new PlanningDraftVO(
                    "draft-character-001", projectCode, "CHARACTERS",
                    new CharacterDraftListVO(List.of(new CharacterDraftVO(
                            "林澈", "男主角", "男", "二十七岁", "清瘦克制",
                            "理性谨慎", "在旧城长大，擅长追查线索", "与核心谜案有个人牵连"
                    ))), null
            );
        }

        @Override
        public List<StoryCharacterVO> confirmCharacters(
                String projectCode,
                String draftId,
                List<CharacterDraftVO> characters
        ) {
            return List.of();
        }

        @Override
        public void discardCharacterDraft(String projectCode, String draftId) {
        }

        @Override
        public StoryBibleVO confirmStoryBible(
                String projectCode, String draftId, StoryBibleVO editedBible
        ) {
            return new StoryBibleVO(
                    editedBible.oneSentencePremise(), editedBible.coreTheme(),
                    editedBible.mainConflict(), editedBible.endingDirection(),
                    editedBible.worldBackground(), editedBible.powerSystemJson(),
                    editedBible.hardRulesJson(), editedBible.styleGuide(), "CONFIRMED"
            );
        }

        @Override
        public List<StoryCharacterVO> listCharacters(String projectCode) {
            return List.of();
        }

        @Override
        public StoryCharacterVO addCharacter(
                String projectCode,
                StoryCharacterVO character
        ) {
            return new StoryCharacterVO(
                    "char-test",
                    character.name(),
                    character.roleType(),
                    character.gender(),
                    character.ageDescription(),
                    character.appearance(),
                    character.personality(),
                    character.backgroundStory(),
                    character.note(),
                    "{}",
                    "ALIVE",
                    "ACTIVE"
            );
        }

        @Override
        public StoryCharacterVO updateCharacter(
                String projectCode,
                String characterCode,
                StoryCharacterVO character
        ) {
            return new StoryCharacterVO(
                    characterCode,
                    character.name(),
                    character.roleType(),
                    character.gender(),
                    character.ageDescription(),
                    character.appearance(),
                    character.personality(),
                    character.backgroundStory(),
                    character.note(),
                    character.currentStateJson(),
                    "ALIVE",
                    "ACTIVE"
            );
        }

        @Override
        public GeneratedChapterVO overwriteChapterContent(
                String projectCode,
                int chapterNumber,
                String title,
                String content
        ) {
            return new GeneratedChapterVO(
                    chapterNumber,
                    title,
                    content,
                    18,
                    "FINALIZED"
            );
        }

        @Override
        public GeneratedChapterVO resyncChapterDerivedData(
                String projectCode,
                int chapterNumber
        ) {
            return new GeneratedChapterVO(
                    chapterNumber,
                    "午夜钟楼",
                    "第一章正文",
                    5,
                    "FINALIZED"
            );
        }

        @Override
        public void deleteChapter(String projectCode, int chapterNumber) {
        }

        @Override
        public GeneratedChapterVO getChapter(
                String projectCode,
                int chapterNumber
        ) {
            return new GeneratedChapterVO(
                    chapterNumber,
                    "午夜钟楼",
                    "第一章正文",
                    5,
                    "FINALIZED"
            );
        }

        @Override
        public List<GeneratedChapterVO> listChapters(String projectCode) {
            return List.of(
                    new GeneratedChapterVO(
                            1,
                            "午夜钟楼",
                            "第一章正文",
                            5,
                            "FINALIZED"
                    ),
                    new GeneratedChapterVO(
                            2,
                            "议会徽记",
                            "第二章正文",
                            5,
                            "FINALIZED"
                    )
            );
        }

        @Override
        public List<NovelProjectVO> listProjects() {
            return List.of(
                    new NovelProjectVO(
                            "novel-002", "雾海长灯", "奇幻",
                            80, 2200, 8, "WRITING"
                    ),
                    new NovelProjectVO(
                            "novel-001", "北境夜行", "玄幻",
                            100, 2500, 3, "WRITING"
                    )
            );
        }

        @Override
        public List<VolumeChapterGroupVO> listChaptersByVolume(
                String projectCode
        ) {
            return List.of(new VolumeChapterGroupVO(
                    "VOL_001",
                    1,
                    "北境开篇",
                    1,
                    30,
                    "READY",
                    listChapters(projectCode)
            ));
        }

        @Override
        public NovelProjectVO getProject(String projectCode) {
            return new NovelProjectVO(
                    projectCode,
                    "北境夜行",
                    "玄幻",
                    100,
                    2500,
                    3,
                    "WRITING"
            );
        }
    }
}
