package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.ModelPricingDTO;
import io.lumina.agent.infrastructure.entity.ModelPricingDO;
import io.lumina.agent.infrastructure.mapper.ModelPricingMapper;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 模型价格管理 Controller
 *
 * <p>提供模型输入/输出价格的 CRUD 接口，供成本计算使用。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
@Slf4j
@Tag(name = "模型价格", description = "模型输入/输出价格 CRUD，供成本计算使用")
@RestController
@RequestMapping("/api/v1/model-pricing")
@RequiredArgsConstructor
@Validated
@RequirePermission("cost:view")
public class ModelPricingController {

    private final ModelPricingMapper modelPricingMapper;

    /**
     * 查询全部模型价格
     */
    @Operation(summary = "查询全部模型价格")
    @GetMapping
    public R<List<ModelPricingDO>> list() {
        List<ModelPricingDO> list = modelPricingMapper.selectList(null);
        return R.success(list);
    }

    /**
     * 创建模型价格
     */
    @Operation(summary = "创建模型价格")
    @PostMapping
    @RequirePermission("model:create")
    public R<ModelPricingDO> create(@Valid @RequestBody ModelPricingDTO dto) {
        log.info("创建模型价格: provider={}, model={}", dto.getProvider(), dto.getModelName());
        ModelPricingDO pricing = new ModelPricingDO();
        BeanUtils.copyProperties(dto, pricing);
        if (pricing.getCurrency() == null || pricing.getCurrency().isBlank()) {
            pricing.setCurrency("CNY");
        }
        if (pricing.getIsActive() == null) {
            pricing.setIsActive(1);
        }
        pricing.setCreateTime(LocalDateTime.now());
        pricing.setUpdateTime(LocalDateTime.now());
        modelPricingMapper.insert(pricing);
        return R.success(pricing);
    }

    /**
     * 更新模型价格
     */
    @Operation(summary = "更新模型价格")
    @PutMapping("/{id}")
    @RequirePermission("model:update")
    public R<ModelPricingDO> update(@PathVariable Long id, @Valid @RequestBody ModelPricingDTO dto) {
        log.info("更新模型价格: id={}", id);
        ModelPricingDO existing = modelPricingMapper.selectById(id);
        if (existing == null) {
            return R.fail(404, "模型价格不存在");
        }
        BeanUtils.copyProperties(dto, existing, "id", "createTime");
        existing.setUpdateTime(LocalDateTime.now());
        modelPricingMapper.updateById(existing);
        return R.success(existing);
    }

    /**
     * 删除模型价格
     */
    @Operation(summary = "删除模型价格")
    @DeleteMapping("/{id}")
    @RequirePermission("model:delete")
    public R<Void> delete(@PathVariable Long id) {
        log.info("删除模型价格: id={}", id);
        modelPricingMapper.deleteById(id);
        return R.success();
    }
}
