package cn.ninth.novel.domain.planning.adapter.repository;

import cn.ninth.novel.domain.planning.model.valobj.PlanningDraftVO;

import java.util.Optional;

public interface IPlanningDraftRepository {

    PlanningDraftVO save(String projectCode, String draftType, Object payload);

    Optional<PlanningDraftVO> find(String projectCode, String draftId);

    void remove(String projectCode, String draftId);

    void removeByProject(String projectCode);
}
