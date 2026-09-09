package cn.ninth.novel.domain.project.model.valobj;

public record CharacterUpdateResultVO(
        StoryCharacterVO character,
        String warning
) {
}
