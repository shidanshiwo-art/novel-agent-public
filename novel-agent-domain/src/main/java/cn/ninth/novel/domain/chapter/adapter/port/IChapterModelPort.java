package cn.ninth.novel.domain.chapter.adapter.port;

import cn.ninth.novel.domain.chapter.model.valobj.ChapterModelResponse;
import cn.ninth.novel.domain.chapter.model.valobj.ChapterTokenUsage;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * 连接外部的agent
 */
public interface IChapterModelPort {

    //流式输出内容
    Flux<String> stream(String systemPrompt, String userPrompt);

    /**
     * 带观测上下文的流式模型调用。旧适配器默认回退到原始方法，保持兼容。
     */
    default Flux<String> stream(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt
    ) {
        return stream(systemPrompt, userPrompt);
    }

    /**
     * 带 Usage 回调的流式调用。Usage 通常在流结束时才可取得，无法取得时回调参数为空。
     */
    default Flux<String> stream(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt,
            Consumer<ChapterTokenUsage> usageConsumer
    ) {
        return stream(systemPrompt, userPrompt, stage, attempt);
    }

    /** 纯文本产出，用于 DRAFT / REVISE。 */
    String call(String systemPrompt, String userPrompt);

    /** 带观测上下文的纯文本模型调用。 */
    default String call(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt
    ) {
        return call(systemPrompt, userPrompt);
    }

    /** 纯文本产出并保留 Usage，用于 REVISE。 */
    default ChapterModelResponse<String> callWithUsage(
            String systemPrompt,
            String userPrompt,
            String stage,
            int attempt
    ) {
        return new ChapterModelResponse<>(
                call(systemPrompt, userPrompt, stage, attempt),
                null,
                null
        );
    }

    /** 结构化产出，用于需要结构化结果的领域任务。 */
    <T> T call(String systemPrompt, String userPrompt, Class<T> responseType);

    /** 带观测上下文的结构化模型调用。 */
    default <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt
    ) {
        return callWithRawResponse(
                systemPrompt,
                userPrompt,
                responseType,
                stage,
                attempt
        ).value();
    }

    /**
     * 结构化产出并保留模型原始文本，供 Prompt Trace 排错。
     * 旧适配器只实现 {@link #call(String, String, Class)} 时仍可兼容，
     * 但无法提供原始文本，调用方会从解析结果生成兜底摘要。
     */
    default <T> ChapterModelResponse<T> callWithRawResponse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        return new ChapterModelResponse<>(
                call(systemPrompt, userPrompt, responseType),
                null,
                null
        );
    }

    /**
     * 带观测上下文的结构化模型调用。旧适配器默认回退到原始方法，保持兼容。
     */
    default <T> ChapterModelResponse<T> callWithRawResponse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt
    ) {
        return callWithRawResponse(systemPrompt, userPrompt, responseType);
    }

    /**
     * 结构化产出并允许 REVIEW 实验按单次调用覆盖“是否启用思考”。
     * {@code null} 表示沿用该 stage 的默认配置；false 只用于关闭思考，
     * 不改变模型、温度、Prompt 或其他 stage 配置。
     */
    default <T> ChapterModelResponse<T> callWithRawResponse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType,
            String stage,
            int attempt,
            Boolean reasoningEnabledOverride
    ) {
        return callWithRawResponse(
                systemPrompt,
                userPrompt,
                responseType,
                stage,
                attempt
        );
    }

}
