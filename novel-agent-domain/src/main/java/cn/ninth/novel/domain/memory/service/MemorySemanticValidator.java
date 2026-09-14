package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryCommitRequest;
import cn.ninth.novel.domain.memory.model.MemoryConflict;

import java.util.List;

/** P0.5 语义校验端口；默认实现不猜测未定义的语义。 */
@FunctionalInterface
public interface MemorySemanticValidator {

    List<MemoryConflict> validate(MemoryCommitRequest request);

    static MemorySemanticValidator noOp() {
        return request -> List.of();
    }
}
