package io.lumina.agent.security;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.lumina.agent.engine.impl.DefaultAgentExecutionEngine;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 知识库隔离回归测试
 *
 * <p>回归历史 bug：AgentConfig.knowledgeBaseIds 填了但引擎不按 KB 过滤，
 * 导致 Agent 读取同租户下未授权的知识库内容。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
class KnowledgeBaseIsolationTest {

    /**
     * 回归：未挂载任何 KB 时，检索返回空结果（不泄露其他 KB）
     */
    @Test
    void noMountedKbReturnsEmpty() throws Exception {
        var engine = createEngineWithMockKnowledge();
        var scoped = invokeScopedKnowledge(engine, Collections.emptyList());

        // 未挂载 KB 时检索应返回空列表
        List<Document> docs = scoped.retrieve("test", RetrieveConfig.builder().build()).block();
        assertThat(docs).isEmpty();
    }

    /**
     * 回归：挂载特定 KB 时，只返回该 KB 的文档（过滤掉其他 KB）
     */
    @Test
    void mountedKbFiltersOthers() throws Exception {
        // Mock delegate 返回 KB 101 + KB 202 的文档
        io.agentscope.core.rag.Knowledge delegate = Mockito.mock(io.agentscope.core.rag.Knowledge.class);
        Document allowed = document("allowed", "kb_id", 101L);
        Document unmounted = document("unmounted", "kbId", "202");
        Mockito.when(delegate.retrieve(anyString(), any(RetrieveConfig.class)))
                .thenReturn(reactor.core.publisher.Mono.just(List.of(allowed, unmounted)));

        var engine = createEngineWithKnowledge(delegate);
        var scoped = invokeScopedKnowledge(engine, List.of(101L));

        List<Document> docs = scoped.retrieve("test", RetrieveConfig.builder().build()).block();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getMetadata().getContentText()).isEqualTo("allowed");
    }

    @SuppressWarnings("unchecked")
    private io.agentscope.core.rag.Knowledge invokeScopedKnowledge(
            DefaultAgentExecutionEngine engine, List<Long> kbIds) throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("scopedKnowledge", List.class);
        method.setAccessible(true);
        return (io.agentscope.core.rag.Knowledge) method.invoke(engine, kbIds);
    }

    private DefaultAgentExecutionEngine createEngineWithMockKnowledge() {
        return createEngineWithKnowledge(Mockito.mock(io.agentscope.core.rag.Knowledge.class));
    }

    private DefaultAgentExecutionEngine createEngineWithKnowledge(io.agentscope.core.rag.Knowledge knowledge) {
        return new DefaultAgentExecutionEngine(
                Mockito.mock(io.lumina.agent.loader.ConfigLoader.class),
                Mockito.mock(io.lumina.agent.loader.PromptLoader.class),
                Mockito.mock(io.lumina.agent.manager.MemoryManager.class),
                new io.lumina.agent.config.LuminaAgentProperties(),
                Mockito.mock(io.lumina.agent.model.ChatModelFactory.class),
                Mockito.mock(io.lumina.agent.resilience.LlmResilienceWrapper.class),
                Mockito.mock(org.springframework.context.ApplicationContext.class),
                Mockito.mock(io.lumina.agent.manager.EnhancedToolManager.class),
                Mockito.mock(io.lumina.agent.monitor.ToolInvocationRecorder.class),
                Mockito.mock(io.lumina.agent.monitor.ToolCircuitBreaker.class),
                Mockito.mock(io.micrometer.core.instrument.MeterRegistry.class),
                knowledge,
                Mockito.mock(io.lumina.agent.config.RagProperties.class));
    }

    private Document document(String content, String kbKey, Object kbId) {
        return new Document(DocumentMetadata.builder()
                .content(TextBlock.builder().text(content).build())
                .docId(content)
                .chunkId(content + "-c0")
                .addPayload(kbKey, kbId)
                .build());
    }
}
