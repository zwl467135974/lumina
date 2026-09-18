package io.lumina.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.api.dto.a2a.A2aAgentCard;
import io.lumina.agent.api.dto.a2a.A2aTask;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * A2aServiceImpl 单元测试（协议映射：message/send 提交、tasks/get 状态映射、卡片构建）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class A2aServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentService agentService = Mockito.mock(AgentService.class);

    private final AgentTaskService agentTaskService = Mockito.mock(AgentTaskService.class);

    private final A2aServiceImpl service = new A2aServiceImpl(agentService, agentTaskService);

    @Test
    void messageSendSubmitsTaskWithExtractedText() throws Exception {
        Mockito.when(agentService.getAgentById(3L)).thenReturn(activeAgent());
        Mockito.when(agentTaskService.submitTask(eq(3L), any(AgentTaskRequestDTO.class)))
                .thenAnswer(inv -> {
                    AgentTaskDO task = new AgentTaskDO();
                    task.setTaskUuid("task-uuid-1");
                    task.setConversationUuid("conv-1");
                    task.setStatus("QUEUED");
                    return task;
                });

        String paramsJson = """
                {"message": {"role": "user", "parts": [
                    {"type": "text", "text": "帮我总结这份报告"},
                    {"type": "text", "text": "并给出三点建议"}
                ]}}
                """;

        A2aTask task = service.messageSend(3L, objectMapper.readTree(paramsJson));

        assertThat(task.getId()).isEqualTo("task-uuid-1");
        assertThat(task.getContextId()).isEqualTo("conv-1");
        assertThat(task.getStatus().getState()).isEqualTo("submitted");
        Mockito.verify(agentTaskService).submitTask(eq(3L), Mockito.<AgentTaskRequestDTO>argThat(dto ->
                dto.getTask().contains("帮我总结这份报告") && dto.getTask().contains("三点建议")));
    }

    @Test
    void messageSendPassesContextIdAsConversation() throws Exception {
        Mockito.when(agentService.getAgentById(3L)).thenReturn(activeAgent());
        Mockito.when(agentTaskService.submitTask(eq(3L), any(AgentTaskRequestDTO.class)))
                .thenAnswer(inv -> {
                    AgentTaskDO task = new AgentTaskDO();
                    task.setTaskUuid("task-uuid-2");
                    task.setStatus("QUEUED");
                    return task;
                });

        String paramsJson = """
                {"contextId": "conv-42", "message": {"parts": [{"type": "text", "text": "继续上文"}]}}
                """;

        service.messageSend(3L, objectMapper.readTree(paramsJson));

        Mockito.verify(agentTaskService).submitTask(eq(3L), Mockito.<AgentTaskRequestDTO>argThat(dto ->
                "conv-42".equals(dto.getConversationId())));
    }

    @Test
    void messageSendRejectsEmptyParts() throws Exception {
        Mockito.when(agentService.getAgentById(3L)).thenReturn(activeAgent());

        assertThatThrownBy(() -> service.messageSend(3L,
                objectMapper.readTree("{\"message\": {\"parts\": []}}")))
                .hasMessageContaining("无文本内容");
    }

    @Test
    void messageSendRejectsInactiveAgent() {
        Agent agent = activeAgent();
        agent.setStatus(0);
        Mockito.when(agentService.getAgentById(3L)).thenReturn(agent);

        assertThatThrownBy(() -> service.messageSend(3L,
                objectMapper.valueToTree(java.util.Map.of())))
                .hasMessageContaining("未启用");
    }

    @Test
    void taskGetMapsCompletedWithTextArtifact() throws Exception {
        AgentTaskDO task = new AgentTaskDO();
        task.setTaskUuid("task-uuid-9");
        task.setConversationUuid("conv-9");
        task.setStatus("COMPLETED");
        task.setResult("最终结论：通过");
        Mockito.when(agentTaskService.getTask("task-uuid-9")).thenReturn(task);

        A2aTask a2aTask = service.taskGet(objectMapper.readTree("{\"id\": \"task-uuid-9\"}"));

        assertThat(a2aTask.getStatus().getState()).isEqualTo("completed");
        assertThat(a2aTask.getArtifacts()).hasSize(1);
        assertThat(a2aTask.getArtifacts().get(0).getParts().get(0).getText())
                .isEqualTo("最终结论：通过");
    }

    @Test
    void taskGetMapsFailedWithMessage() throws Exception {
        AgentTaskDO task = new AgentTaskDO();
        task.setTaskUuid("task-uuid-10");
        task.setStatus("FAILED");
        task.setErrorMessage("模型超时");
        Mockito.when(agentTaskService.getTask("task-uuid-10")).thenReturn(task);

        A2aTask a2aTask = service.taskGet(objectMapper.readTree("{\"id\": \"task-uuid-10\"}"));

        assertThat(a2aTask.getStatus().getState()).isEqualTo("failed");
        assertThat(a2aTask.getStatus().getMessage()).isEqualTo("模型超时");
    }

    @Test
    void taskGetRejectsMissingId() throws Exception {
        assertThatThrownBy(() -> service.taskGet(objectMapper.readTree("{}")))
                .hasMessageContaining("id");
    }

    @Test
    void messageStreamEmitsLifecycleUntilCompleted() throws Exception {
        Mockito.when(agentService.getAgentById(3L)).thenReturn(activeAgent());
        Mockito.when(agentTaskService.submitTask(eq(3L), any(AgentTaskRequestDTO.class)))
                .thenAnswer(inv -> {
                    AgentTaskDO task = new AgentTaskDO();
                    task.setTaskUuid("task-uuid-20");
                    task.setStatus("QUEUED");
                    return task;
                });
        // 进度事件流：QUEUED → RUNNING → COMPLETED(带结果)
        Mockito.when(agentTaskService.streamTaskProgress("task-uuid-20"))
                .thenReturn(reactor.core.publisher.Flux.just(
                        java.util.Map.of("taskUuid", "task-uuid-20", "status", "QUEUED"),
                        java.util.Map.of("taskUuid", "task-uuid-20", "status", "RUNNING"),
                        java.util.Map.of("taskUuid", "task-uuid-20", "status", "COMPLETED",
                                "result", "流式最终回答")));

        String paramsJson = "{\"message\":{\"parts\":[{\"type\":\"text\",\"text\":\"流式验证\"}]}}";
        var tasks = service.messageStream(3L, objectMapper.readTree(paramsJson))
                .collectList()
                .block(java.time.Duration.ofSeconds(5));

        assertThat(tasks).isNotNull();
        assertThat(tasks.size()).isEqualTo(4); // initial submitted + 3 progress
        assertThat(tasks.get(0).getStatus().getState()).isEqualTo("submitted");
        assertThat(tasks.get(1).getStatus().getState()).isEqualTo("submitted");
        assertThat(tasks.get(2).getStatus().getState()).isEqualTo("working");
        assertThat(tasks.get(3).getStatus().getState()).isEqualTo("completed");
        assertThat(tasks.get(3).getArtifacts()).hasSize(1);
        assertThat(tasks.get(3).getArtifacts().get(0).getParts().get(0).getText())
                .isEqualTo("流式最终回答");
    }

    @Test
    void messageStreamDegradestoFailedOnError() throws Exception {
        Mockito.when(agentService.getAgentById(3L)).thenReturn(activeAgent());
        Mockito.when(agentTaskService.submitTask(eq(3L), any(AgentTaskRequestDTO.class)))
                .thenAnswer(inv -> {
                    AgentTaskDO task = new AgentTaskDO();
                    task.setTaskUuid("task-uuid-21");
                    task.setStatus("QUEUED");
                    return task;
                });
        Mockito.when(agentTaskService.streamTaskProgress("task-uuid-21"))
                .thenReturn(reactor.core.publisher.Flux.error(new RuntimeException("sink gone")));

        String paramsJson = "{\"message\":{\"parts\":[{\"type\":\"text\",\"text\":\"异常验证\"}]}}";
        var tasks = service.messageStream(3L, objectMapper.readTree(paramsJson))
                .collectList()
                .block(java.time.Duration.ofSeconds(5));

        assertThat(tasks).isNotNull();
        assertThat(tasks.get(0).getStatus().getState()).isEqualTo("submitted");
        assertThat(tasks.get(1).getStatus().getState()).isEqualTo("failed");
        assertThat(tasks.get(1).getStatus().getMessage()).contains("sink gone");
    }

    @Test
    void agentCardPointsToJsonRpcEndpoint() {
        Mockito.when(agentService.getAgentById(3L)).thenReturn(activeAgent());

        A2aAgentCard card = service.getAgentCard(3L, "https://a2a.example.com");

        assertThat(card.getName()).isEqualTo("客服助手");
        assertThat(card.getUrl()).isEqualTo("https://a2a.example.com/v1/a2a/agents/3");
        assertThat(card.getPreferredTransport()).isEqualTo("JSONRPC");
        assertThat(card.getSkills()).hasSize(1);
    }

    private Agent activeAgent() {
        Agent agent = new Agent();
        agent.setAgentId(3L);
        agent.setAgentName("客服助手");
        agent.setAgentType("REACT");
        agent.setDescription("处理客服咨询");
        agent.setStatus(1);
        return agent;
    }
}
