package cn.ninth.novel.domain.chapter.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 故事人物实体。
 * 表示项目内具有稳定身份的正式人物静态档案。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoryCharacterEntity {
    /** 所属小说项目主键，用于隔离不同作品的角色。 */
    private Long projectId;
    /** 角色业务编码，用于在项目内稳定引用角色。 */
    private String characterCode;
    /** 角色姓名，用于正文生成和角色检索。 */
    private String name;
    /** 剧情角色类型，用于识别主角、配角或反派等定位。 */
    private String roleType;
    /** 角色性别，用于人物设定一致性校验。 */
    private String gender;
    /** 年龄描述，用于保持外貌和行为的合理性。 */
    private String ageDescription;
    /** 外貌描述，用于生成角色形象和保持描写一致。 */
    private String appearance;
    /** 性格特征，用于约束角色行为和决策。 */
    private String personality;
    /** 背景故事，用于解释角色动机和既往经历。 */
    private String backgroundStory;
    /** 作者备注。 */
    private String note;
    /** 角色资料状态，用于控制该角色是否参与当前生成。 */
    private String status;
}
