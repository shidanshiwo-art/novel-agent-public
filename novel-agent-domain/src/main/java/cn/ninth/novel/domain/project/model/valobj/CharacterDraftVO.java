package cn.ninth.novel.domain.project.model.valobj;

/**
 * 人物创作草稿，只承载模型生成所需的创作字段。
 */
public record CharacterDraftVO(
        String name,
        String role,
        String gender,
        String ageDescription,
        String appearance,
        String personality,
        String backgroundStory,
        String note
) {
}
