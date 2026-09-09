package cn.ninth.novel.types.design.framework.tree;

@FunctionalInterface
public interface StrategyHandler<T, D, R> {

    StrategyHandler<Object, Object, Object> DEFAULT =
            (requestParameter, dynamicContext) -> null;

    R apply(T requestParameter, D dynamicContext) throws Exception;

    @SuppressWarnings("unchecked")
    static <T, D, R> StrategyHandler<T, D, R> defaultHandler() {
        return (StrategyHandler<T, D, R>) DEFAULT;
    }
}
