package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.dto.llm.UpdateLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.service.LlmProviderService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@Slf4j
@Tag(name = "LLM 供应商", description = "LLM 供应商配置与连通性测试")
@RestController
@RequirePermission("model:list")
@RequestMapping("/api/v1/llm-providers")
@Validated
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderService llmProviderService;

    @Operation(summary = "查询 LLM 供应商列表")

    @GetMapping
    public R<List<LlmProviderVO>> list(@Valid QueryLlmProviderDTO query) {
        return R.success(llmProviderService.list(query));
    }

    @Operation(summary = "查询 LLM 供应商详情")

    @GetMapping("/{id}")
    public R<LlmProviderVO> getById(@PathVariable Long id) {
        return R.success(llmProviderService.getById(id));
    }

    @Operation(summary = "创建 LLM 供应商")

    @PostMapping
    @Audit(module = "llm_provider", action = "CREATE")
    public R<LlmProviderVO> create(@Valid @RequestBody CreateLlmProviderDTO dto) {
        return R.success(llmProviderService.create(dto));
    }

    @Operation(summary = "更新 LLM 供应商")

    @PutMapping("/{id}")
    @Audit(module = "llm_provider", action = "UPDATE")
    public R<LlmProviderVO> update(@PathVariable Long id, @Valid @RequestBody UpdateLlmProviderDTO dto) {
        return R.success(llmProviderService.update(id, dto));
    }

    @Operation(summary = "删除 LLM 供应商")

    @DeleteMapping("/{id}")
    @Audit(module = "llm_provider", action = "DELETE")
    public R<Void> delete(@PathVariable Long id) {
        llmProviderService.delete(id);
        return R.success();
    }

    @Operation(summary = "测试 LLM 供应商连通性")

    @PostMapping("/{id}/test")
    @Audit(module = "llm_provider", action = "TEST")
    public R<Boolean> testConnection(@PathVariable Long id) {
        return R.success(llmProviderService.testConnection(id));
    }
}
