package cn.ninth.novel.types.design.framework.tree;

public abstract class AbstractStrategyRouter<T, D, R>
        implements StrategyHandler<T, D, R>, StrategyMapper<T, D, R> {

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        return doApply(requestParameter, dynamicContext);
    }

    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;

    protected R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> nextHandler = get(requestParameter, dynamicContext);
        if (nextHandler == null) {
            nextHandler = StrategyHandler.defaultHandler();
        }
        return nextHandler.apply(requestParameter, dynamicContext);
    }
}
