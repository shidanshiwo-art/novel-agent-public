package cn.ninth.novel.domain.planning.model.valobj;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterPlanContextVOTest {

    @Test
    void shouldExposeOnlyPromptSafeFields() {
        RecordComponent[] recordComponents = ChapterPlanContextVO.class.getRecordComponents();
        List<String> componentNames = Arrays.stream(recordComponents)
                .map(RecordComponent::getName)
                .toList();
        Map<String, RecordComponent> components = Arrays.stream(recordComponents)
                .collect(java.util.stream.Collectors.toMap(
                        RecordComponent::getName, component -> component));

        System.out.printf("ChapterPlanContextVO fields=%s%n", componentNames);
        assertThat(Arrays.stream(CharacterBriefVO.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("name", "role", "currentGoal", "currentLocation");
        assertThat(componentNames).containsExactly(
                "chapterNumber",
                "bookOutline",
                "parentOutline",
                "targetOutline",
                "storyBible",
                "position",
                "previousChapterSummary",
                "recentMemories",
                "relevantCharacters",
                "hardRules",
                "requirement"
        );
        assertThat(componentNames)
                .noneMatch(name -> name.equals("id")
                        || name.equals("projectId")
                        || name.equals("outlineNodeId")
                        || name.equals("nodeCode")
                        || name.equals("parentNodeCode"));
        assertThat(components.get("bookOutline").getType())
                .isEqualTo(OutlineBriefVO.class);
        assertThat(components.get("parentOutline").getType())
                .isEqualTo(OutlineBriefVO.class);
        assertThat(components.get("targetOutline").getType())
                .isEqualTo(OutlineBriefVO.class);
        assertThat(components.get("storyBible").getType())
                .isEqualTo(StoryBibleBriefVO.class);
        assertThat(components.get("position").getType())
                .isEqualTo(ChapterPositionVO.class);
        assertThat(components.get("relevantCharacters").getGenericType())
                .isEqualTo(parameterizedListOf(CharacterBriefVO.class));
        assertThat(components.get("recentMemories").getGenericType())
                .isEqualTo(parameterizedListOf(ChapterMemoryBriefVO.class));
        assertThat(components.get("hardRules").getGenericType())
                .isEqualTo(parameterizedListOf(String.class));

        assertThat(Arrays.stream(ChapterPlanContextVO.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .noneMatch(type -> type.contains("StoryBibleVO")
                        || type.contains("StoryCharacterVO")
                        || type.contains("OutlineNodeVO"));
        assertThat(Arrays.stream(ChapterPlanContextVO.class.getRecordComponents())
                .map(RecordComponent::getGenericType))
                .noneMatch(type -> type.getTypeName().contains("OutlineNodeVO"));
    }

    @Test
    void shouldKeepTheContextImmutableAndWithinTheLengthBudget() {
        List<ChapterMemoryBriefVO> memories = new ArrayList<>(List.of(
                new ChapterMemoryBriefVO(
                        16, "第16章摘要", List.of("事件 1"), List.of("问题 1"), "结尾钩子"),
                new ChapterMemoryBriefVO(
                        15, "第15章摘要", List.of("事件 2"), List.of(), "无")
        ));
        ChapterPlanContextVO context = new ChapterPlanContextVO(
                17,
                new OutlineBriefVO("全书", "全书方向", 1, 300),
                new OutlineBriefVO("青云宗", "当前卷", 1, 40),
                new OutlineBriefVO("宗门大比", "当前剧情段", 11, 20),
                new ChapterPositionVO(17, 300),
                "上一章摘要",
                memories,
                List.of(new CharacterBriefVO(
                        "赵无极", "对手", "赢下大比", "演武场")),
                List.of("宗门弟子不得私斗"),
                "加强正面冲突"
        );

        memories.set(0, new ChapterMemoryBriefVO(
                99, "不可写入", List.of(), List.of(), "不可写入"));
        System.out.printf("ChapterPlanContextVO budget memories=%d, characters=%d, rules=%d%n",
                context.recentMemories().size(),
                context.relevantCharacters().size(),
                context.hardRules().size());
        assertThat(context.recentMemories()).extracting(ChapterMemoryBriefVO::shortSummary)
                .containsExactly("第16章摘要", "第15章摘要");
        assertThat(context.recentMemories()).isUnmodifiable();
        assertThat(context.relevantCharacters()).hasSize(1);
        assertThat(context.hardRules()).hasSize(1);
    }

    @Test
    void shouldRejectCollectionsOverThePromptBudget() {
        ChapterPlanContextVO base = new ChapterPlanContextVO(
                17, null, null, null, null, null,
                List.of(), List.of(), List.of(), ""
        );

        assertThatThrownBy(() -> new ChapterPlanContextVO(
                base.chapterNumber(), base.bookOutline(), base.parentOutline(),
                base.targetOutline(), base.position(), base.previousChapterSummary(),
                IntStream.rangeClosed(1, 6)
                        .mapToObj(index -> new ChapterMemoryBriefVO(
                                index, "摘要", List.of(), List.of(), "无"))
                        .toList(),
                base.relevantCharacters(), base.hardRules(), base.requirement()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recentMemories");
        assertThatThrownBy(() -> new ChapterPlanContextVO(
                base.chapterNumber(), base.bookOutline(), base.parentOutline(),
                base.targetOutline(), base.position(), base.previousChapterSummary(),
                base.recentMemories(),
                List.of(
                        new CharacterBriefVO("1", "", "", ""),
                        new CharacterBriefVO("2", "", "", ""),
                        new CharacterBriefVO("3", "", "", ""),
                        new CharacterBriefVO("4", "", "", ""),
                        new CharacterBriefVO("5", "", "", "")),
                base.hardRules(), base.requirement()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relevantCharacters");
        assertThatThrownBy(() -> new ChapterPlanContextVO(
                base.chapterNumber(), base.bookOutline(), base.parentOutline(),
                base.targetOutline(), base.position(), base.previousChapterSummary(),
                base.recentMemories(), base.relevantCharacters(),
                List.of("1", "2", "3", "4", "5", "6"), base.requirement()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hardRules");

        System.out.println("ChapterPlanContextVO rejects memories>5, characters>4, and hardRules>5");
    }

    private static ParameterizedType parameterizedListOf(Class<?> elementType) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{elementType};
            }

            @Override
            public Type getRawType() {
                return List.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }
}
