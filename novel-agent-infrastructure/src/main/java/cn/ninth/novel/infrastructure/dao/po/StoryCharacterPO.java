package cn.ninth.novel.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryCharacterPO {
    /** 角色主键，用于关联角色相关数据。 */
    private Long id;
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
    /** 当前状态 JSON，用于在续写时同步角色能力、位置和关系变化。 */
    private String currentStateJson;
    /** 生存状态，用于避免已死亡或失踪角色在正文中错误出现。 */
    private String lifeStatus;
    /** 角色资料状态，用于控制该角色是否参与当前生成。 */
    private String status;
    /** 角色创建时间，用于审计。 */
    private LocalDateTime createdAt;
    /** 角色最后更新时间，用于追踪设定变化。 */
    private LocalDateTime updatedAt;
}
