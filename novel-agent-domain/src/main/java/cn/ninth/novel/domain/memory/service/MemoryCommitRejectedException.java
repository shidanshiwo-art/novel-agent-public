package cn.ninth.novel.domain.memory.service;

import cn.ninth.novel.domain.memory.model.MemoryConflict;

import java.util.List;

/**
 * Canonical Gate 拒绝提交时的兼容异常类型。
 *
 * <p>生产实现位于 {@code memoryservice} 包；保留该类型以兼容既有领域测试和调用方。</p>
 */
@Deprecated
public class MemoryCommitRejectedException extends IllegalStateException {

    private final List<MemoryConflict> conflicts;

    public MemoryCommitRejectedException(List<MemoryConflict> conflicts) {
        super(message(conflicts));
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public List<MemoryConflict> conflicts() {
        return conflicts;
    }

    private static String message(List<MemoryConflict> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return "Canonical Gate 拒绝提交";
        }
        return "Canonical Gate 拒绝提交："
                + conflicts.stream().map(MemoryConflict::code).toList();
    }
}
