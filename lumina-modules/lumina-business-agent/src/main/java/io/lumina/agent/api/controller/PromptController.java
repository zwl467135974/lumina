package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.dto.PromptDTO;
import io.lumina.agent.api.vo.PromptVO;
import io.lumina.agent.service.PromptService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Prompt 管理 API
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Tag(name = "Prompt 管理", description = "Prompt 模板的版本管理与发布")
@RestController
@RequirePermission("prompt:list")
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
@Validated
public class PromptController {

    private final PromptService promptService;

    @Audit(module = "prompt", action = "CREATE", description = "创建Prompt")
    @Operation(summary = "创建 Prompt")
    @PostMapping
    public R<PromptVO> create(@Valid @RequestBody PromptDTO dto) {
        return R.success(PromptVO.from(promptService.create(dto)));
    }

    @Audit(module = "prompt", action = "UPDATE", description = "更新Prompt")
    @Operation(summary = "更新 Prompt")
    @PutMapping("/{id}")
    public R<PromptVO> update(@PathVariable Long id, @Valid @RequestBody PromptDTO dto) {
        return R.success(PromptVO.from(promptService.update(id, dto)));
    }

    @Audit(module = "prompt", action = "UPDATE", description = "发布Prompt")
    @Operation(summary = "发布 Prompt")
    @PostMapping("/{id}/publish")
    public R<PromptVO> publish(@PathVariable Long id) {
        return R.success(PromptVO.from(promptService.publish(id)));
    }

    @Audit(module = "prompt", action = "CREATE", description = "创建Prompt新版本")
    @Operation(summary = "创建 Prompt 新版本")
    @PostMapping("/{id}/new-version")
    public R<PromptVO> newVersion(@PathVariable Long id, @Valid @RequestBody PromptDTO dto) {
        return R.success(PromptVO.from(promptService.newVersion(id, dto)));
    }

    @Audit(module = "prompt", action = "DELETE", description = "删除Prompt")
    @Operation(summary = "删除 Prompt")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        promptService.delete(id);
        return R.success();
    }

    @Operation(summary = "查询 Prompt 列表")

    @GetMapping
    public R<List<PromptVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.success(promptService.list(name, pageNum, pageSize).stream()
                .map(PromptVO::from)
                .toList());
    }

    @Operation(summary = "查询 Prompt 所有版本")

    @GetMapping("/{name}/versions")
    public R<List<PromptVO>> getVersions(@PathVariable String name) {
        return R.success(promptService.getVersions(name).stream()
                .map(PromptVO::from)
                .toList());
    }

    @Operation(summary = "查询 Prompt 当前激活版本")

    @GetMapping("/{name}/active")
    public R<PromptVO> getActive(@PathVariable String name) {
        return R.success(PromptVO.from(promptService.getActive(name)));
    }
}
