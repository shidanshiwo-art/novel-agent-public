package cn.ninth.novel.api.dto;

/** 故事圣经初始生成或调整的用户要求；当前正式设定由 projectCode 对应项目提供。 */
public record GenerateStoryBibleRequestDTO(String requirement) {
}
