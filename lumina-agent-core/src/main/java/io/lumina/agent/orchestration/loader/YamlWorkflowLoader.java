package io.lumina.agent.orchestration.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowEdge;
import io.lumina.agent.orchestration.model.WorkflowNode;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * YAML 工作流加载器实现
 *
 * <p>使用 Jackson YAML mapper 解析/序列化工作流定义，
 * 通过 {@code @JsonTypeInfo} 多态处理自动识别节点类型。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class YamlWorkflowLoader implements WorkflowLoader {

    private final ObjectMapper yamlMapper;

    public YamlWorkflowLoader() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(factory);
        this.yamlMapper.findAndRegisterModules();
    }

    @Override
    public WorkflowDefinition load(String yaml) {
        try {
            WorkflowDefinition def = yamlMapper.readValue(yaml, WorkflowDefinition.class);
            validate(def);
            log.info("工作流定义加载成功: name={}, nodes={}", def.getName(), def.getNodes().size());
            return def;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORKFLOW_PARSE_FAILED, "解析工作流 YAML 失败", e);
        }
    }

    @Override
    public WorkflowDefinition loadFromClasspath(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new BusinessException(ErrorCode.WORKFLOW_PARSE_FAILED, "工作流配置文件不存在: " + path);
            }
            try (InputStream is = resource.getInputStream()) {
                WorkflowDefinition def = yamlMapper.readValue(is, WorkflowDefinition.class);
                validate(def);
                log.info("从 ClassPath 加载工作流: path={}, name={}", path, def.getName());
                return def;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORKFLOW_PARSE_FAILED, "加载工作流文件失败: " + path, e);
        }
    }

    @Override
    public String toYaml(WorkflowDefinition definition) {
        try {
            return yamlMapper.writeValueAsString(definition);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORKFLOW_PARSE_FAILED, "序列化工作流定义失败", e);
        }
    }

    private void validate(WorkflowDefinition def) {
        if (def.getName() == null || def.getName().isBlank()) {
            throw new IllegalArgumentException("工作流名称不能为空");
        }
        if (def.getNodes() == null || def.getNodes().isEmpty()) {
            throw new IllegalArgumentException("工作流节点不能为空");
        }

        Set<String> nodeIds = new java.util.HashSet<>();
        for (WorkflowNode node : def.getNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                throw new IllegalArgumentException("节点 ID 不能为空: " + node.getName());
            }
            if (!nodeIds.add(node.getId())) {
                throw new IllegalArgumentException("节点 ID 重复: " + node.getId());
            }
        }

        if (def.getEdges() != null) {
            for (WorkflowEdge edge : def.getEdges()) {
                if (!nodeIds.contains(edge.getFrom())) {
                    throw new IllegalArgumentException("边的源节点不存在: " + edge.getFrom());
                }
                if (!nodeIds.contains(edge.getTo())) {
                    throw new IllegalArgumentException("边的目标节点不存在: " + edge.getTo());
                }
            }
        }

        if (def.getStartNodes().isEmpty()) {
            throw new IllegalArgumentException("工作流没有起始节点（所有节点都有入边）");
        }
    }
}
