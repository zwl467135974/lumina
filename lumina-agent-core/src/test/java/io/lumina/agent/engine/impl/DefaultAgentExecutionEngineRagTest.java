package io.lumina.agent.engine.impl;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DefaultAgentExecutionEngine RAG 集成相关逻辑单元测试
 *
 * <p>验证 RAG 模式解析与知识库隔离逻辑（不依赖 Spring 容器与 AgentScope 运行时）。
 *
 * @author Lumina Team
 * @since 1.2.0
 */
class DefaultAgentExecutionEngineRagTest {

    private DefaultAgentExecutionEngine createEngine(Knowledge knowledge) {
        return new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                Mockito.mock(PromptLoader.class),
                Mockito.mock(MemoryManager.class),
                new LuminaAgentProperties(),
                Mockito.mock(ChatModelFactory.class),
                Mockito.mock(LlmResilienceWrapper.class),
                Mockito.mock(ApplicationContext.class),
                Mockito.mock(EnhancedToolManager.class),
                Mockito.mock(ToolInvocationRecorder.class),
                Mockito.mock(ToolCircuitBreaker.class),
                Mockito.mock(MeterRegistry.class),
                knowledge,
                Mockito.mock(RagProperties.class));
    }

    private DefaultAgentExecutionEngine createEngine() {
        return createEngine(Mockito.mock(Knowledge.class));
    }

    @Test
    void parseRagModeGeneric() {
        assertThat(createEngine().parseRagMode("generic")).isEqualTo(RAGMode.GENERIC);
    }

    @Test
    void parseRagModeAgentic() {
        assertThat(createEngine().parseRagMode("agentic")).isEqualTo(RAGMode.AGENTIC);
    }

    @Test
    void parseRagModeNone() {
        assertThat(createEngine().parseRagMode("none")).isEqualTo(RAGMode.NONE);
    }

    @Test
    void parseRagModeCaseInsensitive() {
        assertThat(createEngine().parseRagMode("GENERIC")).isEqualTo(RAGMode.GENERIC);
        assertThat(createEngine().parseRagMode("Agentic")).isEqualTo(RAGMode.AGENTIC);
    }

    @Test
    void parseRagModeTrimWhitespace() {
        assertThat(createEngine().parseRagMode("  generic  ")).isEqualTo(RAGMode.GENERIC);
    }

    @Test
    void parseRagModeNullDefaultsToGeneric() {
        assertThat(createEngine().parseRagMode(null)).isEqualTo(RAGMode.GENERIC);
    }

    @Test
    void parseRagModeBlankDefaultsToGeneric() {
        assertThat(createEngine().parseRagMode("   ")).isEqualTo(RAGMode.GENERIC);
    }

    @Test
    void parseRagModeInvalidFallsBackToGeneric() {
        assertThat(createEngine().parseRagMode("unknown")).isEqualTo(RAGMode.GENERIC);
    }

    @Test
    void scopedKnowledgeOnlyReturnsMountedKnowledgeBases() throws Exception {
        Knowledge delegate = Mockito.mock(Knowledge.class);
        Document allowed = document("allowed", "kb_id", 101L);
        Document unmounted = document("unmounted", "kbId", "202");
        when(delegate.retrieve(anyString(), any(RetrieveConfig.class)))
                .thenReturn(Mono.just(List.of(allowed, unmounted)));

        Knowledge scoped = scopedKnowledge(createEngine(delegate), List.of(101L));

        assertThat(scoped.retrieve("test", RetrieveConfig.builder().build()).block())
                .extracting(document -> document.getMetadata().getContentText())
                .containsExactly("allowed");
    }

    @Test
    void scopedKnowledgeWithoutMountsReturnsNoDocuments() throws Exception {
        Knowledge delegate = Mockito.mock(Knowledge.class);
        Knowledge scoped = scopedKnowledge(createEngine(delegate), List.of());

        assertThat(scoped.retrieve("test", RetrieveConfig.builder().build()).block()).isEmpty();
        Mockito.verify(delegate, Mockito.never()).retrieve(anyString(), any(RetrieveConfig.class));
    }

    @SuppressWarnings("unchecked")
    private Knowledge scopedKnowledge(DefaultAgentExecutionEngine engine, List<Long> knowledgeBaseIds) throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("scopedKnowledge", List.class);
        method.setAccessible(true);
        return (Knowledge) method.invoke(engine, knowledgeBaseIds);
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
