package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.service.prompt.PlanningPrompts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterPromptContractTest {

    @Test
    void shouldDescribeBusinessContextAndCreativeCharacterSchema() {
        String prompt = PlanningPrompts.CHARACTER_SYSTEM;

        System.out.printf("character prompt length=%d%n%s%n", prompt.length(), prompt);
        assertThat(prompt)
                .contains("项目标题", "题材", "Story Bible", "故事设定", "已有角色摘要", "用户补充要求",
                        "已有大纲剧情位置")
                .contains(
                        "补充新的故事人物。",
                        "已有角色属于已经确认的人物设定，不得重新创建其替代版本，也不要生成与已有角色承担完全相同剧情功能的新人物。",
                        "Story Bible 中出现的“主角”“反派”“调查者”“导师”等描述，",
                        "表示剧情所需要的人物功能，不代表已经存在正式人物实体。",
                        "正式人物以“已有角色摘要”为准。",
                        "已确认人物和已确认故事设定是后续生成的事实输入；已有大纲中的未命名人物只表示待补充的剧情位置，",
                        "不得据此覆盖、修改或替换已有确认人物。",
                        "你需要根据这些需求补充正式人物。",
                        "如果 Story Bible 中出现了具体姓名，只有用户补充要求明确要求使用该姓名时才予以尊重；",
                        "“已有角色摘要”中不存在该人物",
                        "不要把该姓名视为已经确认的人物身份",
                        "优先按故事功能重新设计人物。",
                        "已有角色承担的剧情功能应优先复用",
                        "生成新人物时，优先补充当前故事中尚缺少的剧情功能。",
                        "例如已有主角时，可考虑盟友、对手、反派、关键配角等，",
                        "不要为了凑数量而重复已有角色类型或剧情作用。"
                )
                .contains("男主角", "女主角", "反派", "盟友", "配角")
                .contains("男", "女", "其他")
                .contains(
                        "characters", "name", "role", "gender", "ageDescription",
                        "appearance", "personality", "backgroundStory", "note"
                )
                .doesNotContain(
                        "必须一个男主", "必须一个女主", "必须一个反派",
                        "characterCode", "roleType", "MALE_LEAD", "FEMALE_LEAD",
                        "ALIVE", "ACTIVE", "currentStateJson", "数据库 ID", "数据库ID",
                        "数据库字段", "projectCode", "status"
                );
    }
}
