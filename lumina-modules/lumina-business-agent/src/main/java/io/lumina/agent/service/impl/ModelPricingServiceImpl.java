package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.ModelPricingDTO;
import io.lumina.agent.api.vo.ModelPricingVO;
import io.lumina.agent.infrastructure.entity.ModelPricingDO;
import io.lumina.agent.infrastructure.mapper.ModelPricingMapper;
import io.lumina.agent.service.ModelPricingService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型价格服务实现
 *
 * @author Lumina Team
 * @since 3.10.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelPricingServiceImpl implements ModelPricingService {

    private final ModelPricingMapper modelPricingMapper;

    @Override
    public List<ModelPricingVO> list() {
        return modelPricingMapper.selectList(null).stream()
                .map(ModelPricingServiceImpl::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelPricingVO create(ModelPricingDTO dto) {
        log.info("创建模型价格: provider={}, model={}", dto.getProvider(), dto.getModelName());

        ModelPricingDO pricing = new ModelPricingDO();
        BeanUtils.copyProperties(dto, pricing);
        // 业务默认值（原散落在 Controller 内）
        if (pricing.getCurrency() == null || pricing.getCurrency().isBlank()) {
            pricing.setCurrency("CNY");
        }
        if (pricing.getIsActive() == null) {
            pricing.setIsActive(1);
        }
        pricing.setCreateTime(LocalDateTime.now());
        pricing.setUpdateTime(LocalDateTime.now());

        modelPricingMapper.insert(pricing);
        return toVO(pricing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelPricingVO update(Long id, ModelPricingDTO dto) {
        log.info("更新模型价格: id={}", id);

        ModelPricingDO existing = modelPricingMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型价格不存在");
        }
        BeanUtils.copyProperties(dto, existing, "id", "createTime");
        existing.setUpdateTime(LocalDateTime.now());
        modelPricingMapper.updateById(existing);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("删除模型价格: id={}", id);
        modelPricingMapper.deleteById(id);
    }

    private static ModelPricingVO toVO(ModelPricingDO do_) {
        ModelPricingVO vo = new ModelPricingVO();
        BeanUtils.copyProperties(do_, vo);
        return vo;
    }
}
