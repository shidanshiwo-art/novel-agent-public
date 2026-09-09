package cn.ninth.novel.domain.chapter.service.workflow.checkpoint;

import cn.ninth.novel.domain.chapter.service.workflow.ChapterGraphState;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

/** 使用章节 checkpoint codec 克隆 LangGraph4j State。 */
public final class ChapterGraphStateSerializer
        extends StateSerializer<ChapterGraphState> {

    private final CheckpointStateCodec codec;

    public ChapterGraphStateSerializer(CheckpointStateCodec codec) {
        super(ChapterGraphState.FACTORY);
        this.codec = codec;
    }

    @Override
    public void writeData(
            Map<String, Object> data,
            ObjectOutput output
    ) throws IOException {
        try {
            Serializer.writeUTF(codec.encode(data), output);
        } catch (RuntimeException exception) {
            throw new IOException("章节图 State 序列化失败", exception);
        }
    }

    @Override
    public Map<String, Object> readData(ObjectInput input) throws IOException {
        try {
            return codec.decode(Serializer.readUTF(input));
        } catch (RuntimeException exception) {
            throw new IOException("章节图 State 反序列化失败", exception);
        }
    }
}
