package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.vo.ConversationVO;
import io.lumina.agent.api.vo.MessageVO;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.agent.service.ConversationService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import java.util.stream.Collectors;

/**
 * 会话 Controller
 *
 * <p>提供会话生命周期管理与历史消息查询接口。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@RestController
@RequirePermission("agent:list")
@RequestMapping("/api/v1/conversations")
@Validated
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建会话
     *
     * @param agentId 关联 Agent ID
     * @param title   会话标题（可选）
     */
    @Audit(module = "conversation", action = "CREATE", description = "创建会话")
    @PostMapping
    public R<ConversationVO> create(
            @RequestParam Long agentId,
            @RequestParam(required = false) String title) {
        log.info("创建会话: agentId={}, title={}", agentId, title);
        ConversationDO conv = conversationService.createConversation(agentId, title);
        return R.success(toVO(conv));
    }

    /**
     * 分页查询会话列表
     */
    @GetMapping
    public R<PageResult<ConversationVO>> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "20") @Min(1) Integer pageSize) {
        PageResult<ConversationDO> result = conversationService.listConversations(agentId, pageNum, pageSize);

        PageResult<ConversationVO> voResult = new PageResult<>();
        voResult.setPageNum(result.getPageNum());
        voResult.setPageSize(result.getPageSize());
        voResult.setTotal(result.getTotal());
        voResult.setPages(result.getPages());
        voResult.setList(result.getList().stream().map(this::toVO).collect(Collectors.toList()));

        return R.success(voResult);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{uuid}")
    public R<ConversationVO> get(@PathVariable("uuid") String uuid) {
        ConversationDO conv = conversationService.getByUuid(uuid);
        return R.success(toVO(conv));
    }

    /**
     * 删除会话（逻辑删除 + 清空记忆）
     */
    @Audit(module = "conversation", action = "DELETE", description = "删除会话")
    @DeleteMapping("/{uuid}")
    public R<Void> delete(@PathVariable("uuid") String uuid) {
        log.info("删除会话: uuid={}", uuid);
        conversationService.deleteByUuid(uuid);
        return R.success();
    }

    /**
     * 分页查询会话历史消息
     */
    @GetMapping("/{uuid}/messages")
    public R<PageResult<MessageVO>> messages(
            @PathVariable("uuid") String uuid,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "50") @Min(1) Integer pageSize) {
        PageResult<MessageDO> result = conversationService.listMessages(uuid, pageNum, pageSize);

        PageResult<MessageVO> voResult = new PageResult<>();
        voResult.setPageNum(result.getPageNum());
        voResult.setPageSize(result.getPageSize());
        voResult.setTotal(result.getTotal());
        voResult.setPages(result.getPages());
        voResult.setList(result.getList().stream().map(m -> {
            MessageVO vo = new MessageVO();
            BeanUtils.copyProperties(m, vo);
            return vo;
        }).collect(Collectors.toList()));

        return R.success(voResult);
    }

    private ConversationVO toVO(ConversationDO conv) {
        ConversationVO vo = new ConversationVO();
        BeanUtils.copyProperties(conv, vo);
        return vo;
    }
}
