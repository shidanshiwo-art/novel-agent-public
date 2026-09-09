package cn.ninth.novel.domain.project.model.valobj;

/**
 * 模型输出的通用特殊体系对象，不包含业务元数据。
 * description 和 supplement 是开放的自然语言容器，用于保留题材特有设定。
 */
public record PowerSystemDraftVO(
        String name,
        String description,
        String levels,
        String supplement
) {
}
