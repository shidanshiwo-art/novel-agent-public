package cn.ninth.novel.domain.memoryservice;

import cn.ninth.novel.domain.memory.model.MemoryConflict;

import java.util.List;

/** Canonical Gate 拒绝提交时抛出的领域异常。 */
public class MemoryCommitRejectedException
        extends cn.ninth.novel.domain.memory.service.MemoryCommitRejectedException {

    public MemoryCommitRejectedException(List<MemoryConflict> conflicts) {
        super(conflicts);
    }
}
