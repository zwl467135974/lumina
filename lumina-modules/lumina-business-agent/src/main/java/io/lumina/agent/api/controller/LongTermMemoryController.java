package io.lumina.agent.api.controller;

import io.lumina.agent.api.vo.LongTermMemoryVO;
import io.lumina.agent.service.LongTermMemoryService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 长期记忆管理 Controller
 *
 * <p>查看/删除 Reflective Memory 提取的长期记忆。仅承担请求接收与响应，
 * 业务逻辑（含用户鉴权）全部下沉到 {@link LongTermMemoryService}。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/long-term-memories")
@RequiredArgsConstructor
@Validated
@Tag(name = "长期记忆", description = "Reflective Memory 管理")
public class LongTermMemoryController {

    private final LongTermMemoryService memoryService;

    @GetMapping
    @Operation(summary = "查询当前用户的长期记忆")
    public R<List<LongTermMemoryVO>> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "100") int limit) {
        return R.success(memoryService.list(BaseContext.getUserId(), agentId, limit));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除单条长期记忆")
    public R<Void> delete(@PathVariable Long id) {
        memoryService.delete(BaseContext.getUserId(), id);
        return R.success();
    }

    @DeleteMapping
    @Operation(summary = "清空当前用户的全部长期记忆")
    public R<Void> deleteAll(@RequestParam(required = false) Long agentId) {
        memoryService.deleteAll(BaseContext.getUserId(), agentId);
        return R.success();
    }
}
