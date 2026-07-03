package io.lumina.agent.orchestration.loader;

import io.lumina.agent.orchestration.model.WorkflowDefinition;

/**
 * 工作流加载器接口
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface WorkflowLoader {

    /**
     * 从 YAML 字符串加载工作流定义
     */
    WorkflowDefinition load(String yaml);

    /**
     * 从 ClassPath YAML 文件加载
     */
    WorkflowDefinition loadFromClasspath(String path);

    /**
     * 将工作流定义序列化为 YAML
     */
    String toYaml(WorkflowDefinition definition);
}
