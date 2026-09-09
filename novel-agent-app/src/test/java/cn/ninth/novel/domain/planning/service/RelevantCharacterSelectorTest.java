package cn.ninth.novel.domain.planning.service;

import cn.ninth.novel.domain.planning.model.valobj.CharacterBriefVO;
import cn.ninth.novel.domain.project.model.valobj.StoryCharacterVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelevantCharacterSelectorTest {

    private final RelevantCharacterSelector selector = new RelevantCharacterSelector();

    @Test
    void shouldTranslateRoleEnumsToNaturalLanguageLabels() {
        List<CharacterBriefVO> selected = selector.select(
                "男主 女主 反派 配角",
                "当前冲突",
                List.of(
                        character("男主", "MALE_LEAD", "ACTIVE", "背景", "外貌", "备注", "{}"),
                        character("女主", "FEMALE_LEAD", "ACTIVE", "背景", "外貌", "备注", "{}"),
                        character("反派", "ANTAGONIST", "ACTIVE", "背景", "外貌", "备注", "{}"),
                        character("配角", "SUPPORTING", "ACTIVE", "背景", "外貌", "备注", "{}")
                )
        );

        System.out.printf("character role labels: %s%n",
                selected.stream().map(CharacterBriefVO::role).toList());
        assertThat(selected).extracting(CharacterBriefVO::role)
                .containsExactly("男主角", "女主角", "反派", "配角");
    }

    @Test
    void shouldNotFillTheCharacterLimitWithUnrelatedActiveCharacters() {
        List<StoryCharacterVO> characters = new ArrayList<>();
        for (int index = 1; index <= 50; index++) {
            characters.add(character(
                    "角色" + index,
                    index == 1 ? "MALE_LEAD" : "SUPPORTING",
                    index == 1 ? "ACTIVE" : "INACTIVE",
                    "完整 backgroundStory " + index,
                    "完整 appearance " + index,
                    "完整 note " + index,
                    "{\"位置\":\"地点" + index + "\",\"境界\":\"状态" + index + "\"}"
            ));
        }
        characters.set(4, character(
                "赵无极", "VILLAIN", "ACTIVE", "无关完整档案", "无关外貌", "无关备注",
                "{\"目标\":\"争夺宗门首席\",\"位置\":\"演武场\",\"状态\":\"负伤\"}"
        ));
        characters.set(8, character(
                "林渊", "MALE_LEAD", "ACTIVE", "无关完整档案", "无关外貌", "无关备注",
                "{\"目标\":\"寻找神器残片\",\"位置\":\"青云宗\",\"状态\":\"警戒\"}"
        ));
        characters.set(12, character(
                "苏晚", "SUPPORTING", "ACTIVE", "无关完整档案", "无关外貌", "无关备注",
                "{\"位置\":\"山门\",\"状态\":\"等待接应\"}"
        ));
        characters.set(13, character(
                "周衡", "SUPPORTING", "ACTIVE", "无关完整档案", "无关外貌", "无关备注",
                "{\"位置\":\"看台\",\"状态\":\"观战\"}"
        ));

        List<CharacterBriefVO> selected = selector.select(
                "赵无极在上一章负伤后退场",
                "宗门大比",
                "林渊在青云宗与赵无极正面冲突",
                characters
        );

        System.out.printf("50-character selection count=%d, names=%s%n",
                selected.size(), selected.stream().map(CharacterBriefVO::name).toList());
        assertThat(selected).hasSize(2);
        assertThat(selected).extracting(CharacterBriefVO::name)
                .containsExactly("赵无极", "林渊");
        assertThat(selected).extracting(CharacterBriefVO::role)
                .containsExactly("反派", "男主角");
        assertThat(selected.toString())
                .doesNotContain("backgroundStory", "appearance", "note", "角色1");
        assertThat(selected.get(1))
                .extracting(CharacterBriefVO::currentGoal, CharacterBriefVO::currentLocation)
                .containsExactly("寻找神器残片", "青云宗");
        assertThat(selected.toString()).doesNotContain("警戒", "负伤", "当前状态");
    }

    @Test
    void shouldFallbackToOnlyOneProtagonistWhenNoSignalExists() {
        List<StoryCharacterVO> characters = List.of(
                character("配角甲", "SUPPORTING", "INACTIVE", "背景", "外貌", "备注", "{}"),
                character("主角", "PROTAGONIST", "ACTIVE", "背景", "外貌", "备注",
                        "{\"目标\":\"找到父亲\",\"位置\":\"北城\",\"状态\":\"隐忍\"}"),
                character("配角乙", "SUPPORTING", "INACTIVE", "背景", "外貌", "备注", "{}")
        );

        List<CharacterBriefVO> selected = selector.select(
                "没有人物名称命中",
                "没有人物名称命中",
                characters
        );

        System.out.printf("protagonist fallback count=%d, value=%s%n",
                selected.size(), selected);
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).name()).isEqualTo("主角");
        assertThat(selected.toString()).doesNotContain("背景", "外貌", "备注");
    }

    @Test
    void shouldExcludeDeadAndInactiveCharactersFromNormalCandidates() {
        List<CharacterBriefVO> selected = selector.select(
                "已故配角和未激活配角曾经出现",
                "当前剧情涉及已故配角和未激活配角",
                List.of(
                        character("已故配角", "SUPPORTING", "ACTIVE", "DEAD", "背景", "外貌", "备注", "{}"),
                        character("未激活配角", "SUPPORTING", "INACTIVE", "背景", "外貌", "备注", "{\"状态\":\"警戒\"}"),
                        character("活跃配角", "SUPPORTING", "ACTIVE", "背景", "外貌", "备注", "{\"状态\":\"警戒\"}")
                )
        );

        System.out.printf("active character filtering result=%s%n", selected);
        assertThat(selected).isEmpty();
    }

    @Test
    void shouldPrioritizePreviousChapterCharactersOverTargetCharacters() {
        List<CharacterBriefVO> selected = selector.select(
                "甲和乙在上一章共同追踪线索",
                "丙在当前剧情段出现",
                List.of(
                        character("甲", "SUPPORTING", "ACTIVE", "背景", "外貌", "备注", "{}"),
                        character("乙", "SUPPORTING", "ACTIVE", "背景", "外貌", "备注", "{}"),
                        character("丙", "SUPPORTING", "ACTIVE", "背景", "外貌", "备注", "{}")
                )
        );

        System.out.printf("previous chapter character priority result=%s%n", selected);
        assertThat(selected).extracting(CharacterBriefVO::name)
                .containsExactly("甲", "乙", "丙");
    }

    @Test
    void shouldNotFallbackToADeadProtagonist() {
        List<CharacterBriefVO> selected = selector.select(
                "没有人物名称命中",
                "没有人物名称命中",
                List.of(
                        character("已故主角", "PROTAGONIST", "ACTIVE", "DEAD", "背景", "外貌", "备注", "{}"),
                        character("活跃配角", "SUPPORTING", "ACTIVE", "背景", "外貌", "备注", "{}")
                )
        );

        System.out.printf("dead protagonist fallback result=%s%n", selected);
        assertThat(selected).isEmpty();
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
        return character(name, role, status, "ALIVE", background, appearance, note, currentStateJson);
    }

    private static StoryCharacterVO character(
            String name,
            String role,
            String status,
            String lifeStatus,
            String background,
            String appearance,
            String note,
            String currentStateJson
    ) {
        return new StoryCharacterVO(
                "character-" + name,
                name,
                role,
                "UNKNOWN",
                null,
                appearance,
                "性格",
                background,
                note,
                currentStateJson,
                lifeStatus,
                status
        );
    }
}
