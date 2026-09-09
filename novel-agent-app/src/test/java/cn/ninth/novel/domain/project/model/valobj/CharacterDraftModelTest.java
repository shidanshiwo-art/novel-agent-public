package cn.ninth.novel.domain.project.model.valobj;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterDraftModelTest {

    @Test
    void shouldExposeOnlyCreativeCharacterFields() {
        String[] fields = java.util.Arrays.stream(CharacterDraftVO.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
        CharacterDraftVO draft = new CharacterDraftVO(
                "林澈", "男主角", "男", "二十岁",
                "眉眼清冷", "谨慎坚韧", "边城遗孤", "不轻易许诺"
        );
        CharacterDraftListVO drafts = new CharacterDraftListVO(List.of(draft));

        System.out.printf("character draft fields=%s, list size=%d%n",
                List.of(fields), drafts.characters().size());
        assertThat(fields).containsExactly(
                "name", "role", "gender", "ageDescription", "appearance",
                "personality", "backgroundStory", "note"
        );
        assertThat(drafts.characters()).containsExactly(draft);
    }
}
