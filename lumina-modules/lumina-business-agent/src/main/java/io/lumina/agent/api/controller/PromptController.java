package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.PromptDTO;
import io.lumina.agent.infrastructure.entity.PromptDO;
import io.lumina.agent.service.PromptService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prompt 管理 API
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    @Audit(module = "prompt", action = "CREATE", description = "创建Prompt")
    @PostMapping
    public R<PromptDO> create(@Valid @RequestBody PromptDTO dto) {
        return R.success(promptService.create(dto));
    }

    @Audit(module = "prompt", action = "UPDATE", description = "更新Prompt")
    @PutMapping("/{id}")
    public R<PromptDO> update(@PathVariable Long id, @RequestBody PromptDTO dto) {
        return R.success(promptService.update(id, dto));
    }

    @Audit(module = "prompt", action = "UPDATE", description = "发布Prompt")
    @PostMapping("/{id}/publish")
    public R<PromptDO> publish(@PathVariable Long id) {
        return R.success(promptService.publish(id));
    }

    @PostMapping("/{id}/new-version")
    public R<PromptDO> newVersion(@PathVariable Long id, @RequestBody PromptDTO dto) {
        return R.success(promptService.newVersion(id, dto));
    }

    @Audit(module = "prompt", action = "DELETE", description = "删除Prompt")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        promptService.delete(id);
        return R.success();
    }

    @GetMapping
    public R<List<PromptDO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.success(promptService.list(name, pageNum, pageSize));
    }

    @GetMapping("/{name}/versions")
    public R<List<PromptDO>> getVersions(@PathVariable String name) {
        return R.success(promptService.getVersions(name));
    }

    @GetMapping("/{name}/active")
    public R<PromptDO> getActive(@PathVariable String name) {
        return R.success(promptService.getActive(name));
    }
}
