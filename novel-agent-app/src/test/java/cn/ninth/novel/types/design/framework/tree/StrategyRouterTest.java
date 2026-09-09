package cn.ninth.novel.types.design.framework.tree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StrategyRouterTest {

    @Test
    void shouldRouteToTheSelectedNodeAndShareDynamicContext() throws Exception {
        EndNode endNode = new EndNode();
        RootNode rootNode = new RootNode(
                new BranchNode("FAST", endNode),
                new BranchNode("SAFE", endNode));
        DynamicContext dynamicContext = new DynamicContext();

        String result = rootNode.apply(new Request("SAFE"), dynamicContext);

        assertEquals("ROOT>SAFE>END", result);
        assertEquals(List.of("ROOT", "SAFE", "END"), dynamicContext.visitedNodes());
    }

    @Test
    void shouldReturnNullWhenTheTreeReachesTheDefaultHandler() throws Exception {
        StrategyHandler<Request, DynamicContext, String> terminalNode =
                new TerminalRoutingNode();

        String result = terminalNode.apply(new Request("FAST"), new DynamicContext());

        assertNull(result);
    }

    private record Request(String route) {
    }

    private static final class DynamicContext {
        private final List<String> visitedNodes = new ArrayList<>();

        private List<String> visitedNodes() {
            return List.copyOf(visitedNodes);
        }
    }

    private static final class RootNode
            extends AbstractStrategyRouter<Request, DynamicContext, String> {

        private final StrategyHandler<Request, DynamicContext, String> fastNode;
        private final StrategyHandler<Request, DynamicContext, String> safeNode;

        private RootNode(
                StrategyHandler<Request, DynamicContext, String> fastNode,
                StrategyHandler<Request, DynamicContext, String> safeNode) {
            this.fastNode = fastNode;
            this.safeNode = safeNode;
        }

        @Override
        protected String doApply(Request requestParameter, DynamicContext dynamicContext)
                throws Exception {
            dynamicContext.visitedNodes.add("ROOT");
            return router(requestParameter, dynamicContext);
        }

        @Override
        public StrategyHandler<Request, DynamicContext, String> get(
                Request requestParameter, DynamicContext dynamicContext) {
            return "FAST".equals(requestParameter.route()) ? fastNode : safeNode;
        }
    }

    private static final class BranchNode
            extends AbstractStrategyRouter<Request, DynamicContext, String> {

        private final String name;
        private final StrategyHandler<Request, DynamicContext, String> nextNode;

        private BranchNode(
                String name,
                StrategyHandler<Request, DynamicContext, String> nextNode) {
            this.name = name;
            this.nextNode = nextNode;
        }

        @Override
        protected String doApply(Request requestParameter, DynamicContext dynamicContext)
                throws Exception {
            dynamicContext.visitedNodes.add(name);
            return router(requestParameter, dynamicContext);
        }

        @Override
        public StrategyHandler<Request, DynamicContext, String> get(
                Request requestParameter, DynamicContext dynamicContext) {
            return nextNode;
        }
    }

    private static final class EndNode
            extends AbstractStrategyRouter<Request, DynamicContext, String> {

        @Override
        protected String doApply(Request requestParameter, DynamicContext dynamicContext) {
            dynamicContext.visitedNodes.add("END");
            return String.join(">", dynamicContext.visitedNodes);
        }

        @Override
        public StrategyHandler<Request, DynamicContext, String> get(
                Request requestParameter, DynamicContext dynamicContext) {
            return StrategyHandler.defaultHandler();
        }
    }

    private static final class TerminalRoutingNode
            extends AbstractStrategyRouter<Request, DynamicContext, String> {

        @Override
        protected String doApply(Request requestParameter, DynamicContext dynamicContext)
                throws Exception {
            return router(requestParameter, dynamicContext);
        }

        @Override
        public StrategyHandler<Request, DynamicContext, String> get(
                Request requestParameter, DynamicContext dynamicContext) {
            return StrategyHandler.defaultHandler();
        }
    }
}
