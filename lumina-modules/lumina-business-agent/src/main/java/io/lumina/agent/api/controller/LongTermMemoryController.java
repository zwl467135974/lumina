package io.lumina.agent.api.controller;

import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

/**
 * 长期记忆管理 Controller
 *
 * <p>查看/删除 Reflective Memory 提取的长期记忆。
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

    private final LongTermMemoryMapper memoryMapper;

    @GetMapping
    @Operation(summary = "查询当前用户的长期记忆")
    public R<List<LongTermMemoryDO>> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "100") int limit) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        LambdaQueryWrapper<LongTermMemoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongTermMemoryDO::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(LongTermMemoryDO::getAgentId, agentId);
        }
        wrapper.orderByDesc(LongTermMemoryDO::getImportance)
                .orderByDesc(LongTermMemoryDO::getCreateTime)
                .last("LIMIT " + Math.min(limit, 500));

        return R.success(memoryMapper.selectList(wrapper));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除单条长期记忆")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = BaseContext.getUserId();
        LongTermMemoryDO memory = memoryMapper.selectById(id);
        if (memory == null || !memory.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "记忆不存在");
        }
        memoryMapper.deleteById(id);
        log.info("删除长期记忆: id={}, userId={}", id, userId);
        return R.success();
    }

    @DeleteMapping
    @Operation(summary = "清空当前用户的全部长期记忆")
    public R<Void> deleteAll(@RequestParam(required = false) Long agentId) {
        Long userId = BaseContext.getUserId();
        LambdaQueryWrapper<LongTermMemoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongTermMemoryDO::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(LongTermMemoryDO::getAgentId, agentId);
        }
        int deleted = memoryMapper.delete(wrapper);
        log.info("清空长期记忆: userId={}, agentId={}, deleted={}", userId, agentId, deleted);
        return R.success();
    }
}
