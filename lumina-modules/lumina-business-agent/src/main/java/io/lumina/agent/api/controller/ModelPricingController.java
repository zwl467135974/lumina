package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.ModelPricingDTO;
import io.lumina.agent.api.vo.ModelPricingVO;
import io.lumina.agent.service.ModelPricingService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 模型价格管理 Controller
 *
 * <p>提供模型输入/输出价格的 CRUD 接口，供成本计算使用。
 * 仅承担请求接收与响应，业务逻辑（含默认值、事务）下沉到 {@link ModelPricingService}。
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

    private final ModelPricingService modelPricingService;

    /**
     * 查询全部模型价格
     */
    @Operation(summary = "查询全部模型价格")
    @GetMapping
    public R<List<ModelPricingVO>> list() {
        return R.success(modelPricingService.list());
    }

    /**
     * 创建模型价格
     */
    @Operation(summary = "创建模型价格")
    @PostMapping
    @RequirePermission("model:create")
    public R<ModelPricingVO> create(@Valid @RequestBody ModelPricingDTO dto) {
        return R.success(modelPricingService.create(dto));
    }

    /**
     * 更新模型价格
     */
    @Operation(summary = "更新模型价格")
    @PutMapping("/{id}")
    @RequirePermission("model:update")
    public R<ModelPricingVO> update(@PathVariable Long id, @Valid @RequestBody ModelPricingDTO dto) {
        return R.success(modelPricingService.update(id, dto));
    }

    /**
     * 删除模型价格
     */
    @Operation(summary = "删除模型价格")
    @DeleteMapping("/{id}")
    @RequirePermission("model:delete")
    public R<Void> delete(@PathVariable Long id) {
        modelPricingService.delete(id);
        return R.success();
    }
}
