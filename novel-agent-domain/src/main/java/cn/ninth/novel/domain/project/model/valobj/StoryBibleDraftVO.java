package cn.ninth.novel.domain.project.model.valobj;

import java.util.List;

/**
 * 故事圣经的模型草稿，只包含创作内容，不包含状态、项目编码或数据库字段。
 */
public record StoryBibleDraftVO(
        String oneSentencePremise,
        String coreTheme,
        String mainConflict,
        String endingDirection,
        String worldBackground,
        PowerSystemDraftVO powerSystem,
        List<String> hardRules,
        String styleGuide
) {
}
