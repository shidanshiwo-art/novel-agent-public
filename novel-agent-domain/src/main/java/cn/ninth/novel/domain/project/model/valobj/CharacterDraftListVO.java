package cn.ninth.novel.domain.project.model.valobj;

import java.util.List;

/**
 * 人物创作草稿列表。
 */
public record CharacterDraftListVO(List<CharacterDraftVO> characters) {
}
