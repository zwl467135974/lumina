package io.lumina.agent.service;

import io.lumina.agent.api.dto.ab.CreateAbExperimentDTO;
import io.lumina.agent.api.vo.AbExperimentVO;
import io.lumina.agent.model.AgentConfig;

import java.util.List;

/**
 * A/B 测试服务
 *
 * <p>管理实验生命周期 + 变体流量分配 + 曝光记录。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
public interface AbTestService {

    /**
     * 创建实验
     */
    AbExperimentVO createExperiment(CreateAbExperimentDTO dto);

    /**
     * 查询实验详情
     */
    AbExperimentVO getExperiment(Long id);

    /**
     * 查询实验列表
     */
    List<AbExperimentVO> listExperiments(Long agentId, String status);

    /**
     * 启动实验
     */
    void startExperiment(Long id);

    /**
     * 暂停实验
     */
    void pauseExperiment(Long id);

    /**
     * 完成实验
     */
    void completeExperiment(Long id);

    /**
     * 删除实验
     */
    void deleteExperiment(Long id);

    /**
     * 为指定 Agent 分配变体（同一 conversation 粘滞）
     *
     * <p>返回变体的 LLMConfig 覆盖配置（null 表示不覆盖，走默认）。
     *
     * @param agentId        Agent ID
     * @param conversationId 会话 ID（用于粘滞分配）
     * @return 变体上下文（experimentId + variantId + llmConfig），或 null 表示无活跃实验
     */
    VariantContext assignVariant(Long agentId, String conversationId);

    /**
     * 记录曝光结果
     */
    void recordExposure(Long experimentId, Long variantId, String variantName,
                        String conversationId, boolean success, long latencyMs,
                        Integer tokens, String errorMsg);

    /**
     * 变体分配上下文
     */
    record VariantContext(Long experimentId, Long variantId, String variantName,
                          AgentConfig.LLMConfig llmConfig, String promptName) {}
}
