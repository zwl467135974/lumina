package io.lumina.agent.domain.enums;

import lombok.Getter;

/**
 * Agent 类型枚举
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Getter
public enum AgentTypeEnum {

    /**
     * ReAct Agent（推理-行动）
     */
    REACT("ReAct", "ReAct Agent - 推理和行动范式"),

    /**
     * Plan and Execute Agent
     */
    PLAN_AND_EXECUTE("PlanAndExecute", "计划并执行 Agent"),

    /**
     * Multi-Agent（多智能体协作）
     */
    MULTI_AGENT("MultiAgent", "多智能体协作 Agent"),

    /**
     * RAG Agent（检索增强生成）
     */
    RAG("RAG", "检索增强生成 Agent");

    /**
     * 类型代码
     */
    private final String code;

    /**
     * 类型描述
     */
    private final String description;

    AgentTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     */
    public static AgentTypeEnum fromCode(String code) {
        for (AgentTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的 Agent 类型: " + code);
    }
}
