package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.model.valobj.QualityFinding;
import cn.ninth.novel.domain.chapter.model.valobj.ReviewContext;

import java.util.List;

/** Quality Review 的窄范围审核端口。 */
@FunctionalInterface
public interface QualityReviewer {

    List<QualityFinding> review(ReviewContext reviewContext);
}
