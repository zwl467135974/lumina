package io.lumina.agent.engine.impl;

import io.agentscope.core.rag.RAGMode;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.MemoryManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultAgentExecutionEngine RAG 集成相关逻辑单元测试
 *
 * <p>验证 RAG 模式解析逻辑（不依赖 Spring 容器与 AgentScope 运行时）。
 *
 * @author Lumina Team
 * @since 1.2.0
 */
class DefaultAgentExecutionEngineRagTest {

    private DefaultAgentExecutionEngine createEngine() {
        return new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                Mockito.mock(PromptLoader.class),
                Mockito.mock(MemoryManager.class),
                new LuminaAgentProperties());
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
}
