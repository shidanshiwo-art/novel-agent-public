package cn.ninth.novel.types.design.framework.tree;

@FunctionalInterface
public interface StrategyMapper<T, D, R> {

    StrategyHandler<T, D, R> get(T requestParameter, D dynamicContext) throws Exception;
}
