package io.lumina.agent.service;

import io.lumina.agent.api.dto.ModelPricingDTO;
import io.lumina.agent.api.vo.ModelPricingVO;

import java.util.List;

/**
 * 模型价格服务
 *
 * <p>承担模型输入/输出价格的 CRUD，供成本计算使用。
 *
 * @author Lumina Team
 * @since 3.10.0
 */
public interface ModelPricingService {

    List<ModelPricingVO> list();

    ModelPricingVO create(ModelPricingDTO dto);

    ModelPricingVO update(Long id, ModelPricingDTO dto);

    void delete(Long id);
}
