package cn.ninth.novel.domain.chapter.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 故事圣经实体。
 * 保存作品长期稳定的主题、主线矛盾、世界规则和文风约束，是章节生成不可绕过的全局设定。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoryBibleEntity {

    /** 所属小说项目主键，保证每份设定归属明确。 */
    private Long projectId;
    /** 一句话梗概，用于快速向模型说明故事核心。 */
    private String oneSentencePremise;
    /** 核心主题，用于保持作品表达的一致性。 */
    private String coreTheme;
    /** 主线矛盾，用于约束全书的核心对抗。 */
    private String mainConflict;
    /** 结局方向，用于指导长线伏笔和剧情收束。 */
    private String endingDirection;
    /** 世界背景，用于提供时代、地域和社会规则上下文。 */
    private String worldBackground;
    /** 力量体系 JSON，用于约束能力等级和成长逻辑。 */
    private String powerSystemJson;
    /** 不可违背的硬规则 JSON，用于避免设定冲突。 */
    private String hardRulesJson;
    /** 文风指南，用于统一生成内容的叙述风格。 */
    private String styleGuide;
    /** 设定状态，用于控制其是否作为有效生成上下文。 */
    private String status;
}
