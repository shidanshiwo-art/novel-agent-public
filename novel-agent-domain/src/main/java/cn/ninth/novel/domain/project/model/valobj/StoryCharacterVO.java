package cn.ninth.novel.domain.project.model.valobj;

public record StoryCharacterVO(
        String characterCode,
        String name,
        String roleType,
        String gender,
        String ageDescription,
        String appearance,
        String personality,
        String backgroundStory,
        String note,
        String currentStateJson,
        String lifeStatus,
        String status
)
{
}
