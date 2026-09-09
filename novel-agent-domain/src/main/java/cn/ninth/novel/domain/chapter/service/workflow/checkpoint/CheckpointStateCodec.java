package cn.ninth.novel.domain.chapter.service.workflow.checkpoint;

import java.util.Map;

/** 章节工作流 checkpoint State 的编解码契约。 */
public interface CheckpointStateCodec {

    String encode(Map<String, Object> state);

    Map<String, Object> decode(String state);
}
