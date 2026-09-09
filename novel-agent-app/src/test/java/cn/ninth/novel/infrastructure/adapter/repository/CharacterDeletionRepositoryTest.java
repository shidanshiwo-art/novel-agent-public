package cn.ninth.novel.infrastructure.adapter.repository;

import cn.ninth.novel.Application;
import cn.ninth.novel.domain.project.adapter.repository.INovelProjectRepository;
import cn.ninth.novel.domain.project.model.valobj.NovelProjectVO;
import cn.ninth.novel.infrastructure.dao.INovelProjectDao;
import cn.ninth.novel.infrastructure.dao.IStoryCharacterDao;
import cn.ninth.novel.infrastructure.dao.po.StoryCharacterPO;
import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
@Transactional
class CharacterDeletionRepositoryTest {

    @Autowired
    private INovelProjectRepository repository;
    @Autowired
    private INovelProjectDao projectDao;
    @Autowired
    private IStoryCharacterDao characterDao;
    @Autowired
    private JdbcTemplate jdbc;

    private final String projectCode = "character-delete-" + UUID.randomUUID();
    private final String otherProjectCode = "character-delete-" + UUID.randomUUID();
    private Long projectId;
    private Long otherProjectId;

    @BeforeEach
    void setUp() {
        projectId = createProject(projectCode);
        otherProjectId = createProject(otherProjectCode);
        insertCharacter(projectId, "target");
        insertCharacter(projectId, "retained");
        insertCharacter(otherProjectId, "target");
        System.out.printf("删除测试准备：projectId=%s，otherProjectId=%s，插入三个人物；测试后事务回滚%n",
                projectId, otherProjectId);
    }

    @Test
    void shouldDeleteOnlyCharacterInRequestedProject() {
        System.out.println("执行删除：当前项目 target");
        repository.deleteCharacter(projectCode, "target");

        assertThat(characterDao.queryByCharacterCode(projectId, "target")).isNull();
        assertThat(characterDao.queryByCharacterCode(projectId, "retained")).isNotNull();
        assertThat(characterDao.queryByCharacterCode(otherProjectId, "target")).isNotNull();
        System.out.println("删除结果：目标行已物理删除，同项目其他人物与其他项目同编码人物均保留");
    }

    @Test
    void shouldRejectRepeatedDeletion() {
        repository.deleteCharacter(projectCode, "target");
        System.out.println("首次删除成功，执行重复删除");
        assertCharacterMissing(projectCode, "target");
    }

    @Test
    void shouldRejectCharacterBelongingOnlyToAnotherProject() {
        System.out.println("尝试从另一项目删除仅属于当前项目的 retained");
        assertCharacterMissing(otherProjectCode, "retained");
        assertThat(characterDao.queryByCharacterCode(projectId, "retained")).isNotNull();
        assertThat(characterDao.queryByProjectId(otherProjectId)).hasSize(1);
        System.out.println("跨项目删除被拒绝，双方人物记录均保留");
    }

    @Test
    void shouldRejectMissingProject() {
        String missingProjectCode = "missing-" + UUID.randomUUID();
        System.out.println("执行不存在项目的人物删除");
        AppException exception = assertThrows(AppException.class,
                () -> repository.deleteCharacter(missingProjectCode, "target"));
        assertThat(exception.getCode()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
        assertThat(exception.getInternalDetail())
                .isEqualTo("项目不存在，projectCode=" + missingProjectCode);
        assertThat(characterDao.queryByCharacterCode(projectId, "target")).isNotNull();
        System.out.println("删除结果：" + exception.getInternalDetail() + "，已有项目人物保留");
    }

    @Test
    void shouldRejectCharacterThatNeverExisted() {
        assertCharacterMissing(projectCode, "missing-character");
        assertThat(characterDao.queryByProjectId(projectId)).hasSize(2);
        System.out.println("从未存在的人物删除失败，现有人物列表未改变");
    }

    @Test
    void shouldIgnoreOtherProjectDataAndNeverCascadeStoryData() {
        insertStoryData(otherProjectId);
        insertStoryData(projectId);
        var currentStory = storySnapshot(projectId);
        var otherStory = storySnapshot(otherProjectId);
        repository.deleteCharacter(projectCode, "target");
        assertThat(characterDao.queryByCharacterCode(projectId, "target")).isNull();
        assertThat(characterDao.queryByCharacterCode(otherProjectId, "target")).isNotNull();
        assertThat(storySnapshot(projectId)).isEqualTo(currentStory);
        assertThat(storySnapshot(otherProjectId)).isEqualTo(otherStory);
        System.out.println("当前项目无引用人物删除成功，其他项目同编码引用及所有故事数据均保留");
    }

    private void insertStoryData(Long ownerId) {
        jdbc.update("""
                INSERT INTO outline_node(project_id, node_code, node_kind, sequence_no, title, summary)
                VALUES (?, 'book', 'BOOK', 1, '测试大纲', '测试大纲')
                """, ownerId);
        Long outlineId = jdbc.queryForObject("SELECT id FROM outline_node WHERE project_id = ?", Long.class, ownerId);
        jdbc.update("""
                INSERT INTO chapter_plan(project_id, outline_node_id, chapter_number, title, summary, status)
                VALUES (?, ?, 1, '第一章', '正式章节计划', 'COMPLETED')
                """, ownerId, outlineId);
        Long planId = jdbc.queryForObject("SELECT id FROM chapter_plan WHERE project_id = ?", Long.class, ownerId);
        jdbc.update("""
                INSERT INTO story_chapter(project_id, chapter_plan_id, chapter_number, title, content, word_count, status)
                VALUES (?, ?, 1, '第一章', '必须保留的正文', 8, 'FINALIZED')
                """, ownerId, planId);
    }

    private List<?> storySnapshot(Long ownerId) {
        return List.of(
                jdbc.queryForList("SELECT * FROM chapter_plan WHERE project_id = ? ORDER BY id", ownerId),
                jdbc.queryForList("SELECT * FROM story_chapter WHERE project_id = ? ORDER BY id", ownerId));
    }

    private void assertCharacterMissing(String code, String characterCode) {
        AppException exception = assertThrows(AppException.class,
                () -> repository.deleteCharacter(code, characterCode));
        assertThat(exception.getCode()).isEqualTo(ResponseCode.ILLEGAL_PARAMETER.getCode());
        assertThat(exception.getInfo()).isEqualTo("人物不存在");
        System.out.println("删除结果：" + exception.getInfo());
    }

    private Long createProject(String code) {
        repository.createProject(new NovelProjectVO(code, "人物删除测试", "玄幻", 10, 2000, 0, "DRAFT"));
        return projectDao.queryByProjectCode(code).getId();
    }

    private void insertCharacter(Long ownerProjectId, String code) {
        StoryCharacterPO character = new StoryCharacterPO();
        character.setProjectId(ownerProjectId);
        character.setCharacterCode(code);
        character.setName("测试人物");
        character.setRoleType("SUPPORTING");
        character.setGender("OTHER");
        character.setCurrentStateJson("{}");
        character.setLifeStatus("ALIVE");
        character.setStatus("ACTIVE");
        characterDao.insert(character);
    }
}
