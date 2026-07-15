package io.lumina.agent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 工作流模板 VO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class WorkflowTemplateVO {
    private String name;
    private String description;
    private String definitionYaml;

    /**
     * 模板所需的 Agent 角色列表（用于前端展示"按模板创建"时的 Agent 映射表单）
     *
     * @since 3.3.0
     */
    private List<AgentRole> requiredAgents;

    /**
     * 模板中一个 Agent 角色的占位符与描述
     */
    @Data
    public static class AgentRole {
        /** YAML 中的占位符（如 ${agent1}） */
        private String placeholder;
        /** 角色描述（如"Planner - 负责分解任务"） */
        private String description;
    }
}
