package io.lumina.agent.api.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A2A Task（Agent2Agent 协议的任务对象）
 *
 * <p>state 取值对齐协议：submitted / working / input-required / completed /
 * failed / canceled；completed 时以 artifacts 携带文本产出。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aTask {

    /** 任务 ID（Lumina taskUuid） */
    private String id;

    /** 会话上下文 ID（Lumina conversationUuid，多轮时由客户端回传） */
    private String contextId;

    /** 固定为 task */
    private String kind = "task";

    private TaskStatus status;

    /** 完成时的产出（completed 状态） */
    private List<Artifact> artifacts;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TaskStatus {
        private String state;
        /** 失败原因 / 状态补充说明 */
        private String message;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact {
        private String artifactId;
        private String name;
        private List<Part> parts;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        private String type;
        private String text;
    }

    public static A2aTask of(String id, String contextId, String state, String message) {
        A2aTask task = new A2aTask();
        task.setId(id);
        task.setContextId(contextId);
        TaskStatus status = new TaskStatus();
        status.setState(state);
        status.setMessage(message);
        task.setStatus(status);
        return task;
    }
}
