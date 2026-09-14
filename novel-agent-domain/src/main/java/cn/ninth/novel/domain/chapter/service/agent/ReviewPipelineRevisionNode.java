package cn.ninth.novel.domain.chapter.service.agent;

import cn.ninth.novel.domain.chapter.adapter.port.IChapterModelPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 旧类名的兼容包装；新 Pipeline 实际使用 TargetedRevisionNode 的窄输入实现。
 */
@Component
public class ReviewPipelineRevisionNode extends TargetedRevisionNode {

    /** 兼容离线骨架测试，不调用模型。 */
    public ReviewPipelineRevisionNode() {
        super();
    }

    @Autowired
    public ReviewPipelineRevisionNode(IChapterModelPort chapterModelPort) {
        super(chapterModelPort);
    }
}
