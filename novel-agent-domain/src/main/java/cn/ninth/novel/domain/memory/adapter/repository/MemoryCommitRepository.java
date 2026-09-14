package cn.ninth.novel.domain.memory.adapter.repository;

import cn.ninth.novel.domain.memory.model.MemoryCommitPlan;
import cn.ninth.novel.domain.memory.model.MemoryCommitResult;

/**
 * Canonical Commit 的事务端口。
 * 实现必须让正文版本、Canonical、状态变更和 outbox 共用一个业务事务。
 */
public interface MemoryCommitRepository {

    MemoryCommitResult commit(MemoryCommitPlan plan);
}
