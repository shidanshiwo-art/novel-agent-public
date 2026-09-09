package cn.ninth.novel.api.dto;

/** 人物创作草稿生成所需的用户补充要求。 */
public record GenerateCharacterRequestDTO(Integer preferredCount, String requirement) {
}
