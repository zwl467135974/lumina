package io.lumina.agent.api.controller;

import io.lumina.agent.service.ApprovalPendingRegistry;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具调用审批控制器
 *
 * <p>审批人（或企微回调）通过此端点对高危工具调用做出 allow-once 决定。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@RestController
@RequestMapping("/agents/tools/approvals")
@Validated
@RequiredArgsConstructor
@RequirePermission("agent:tool-approval")
public class ToolApprovalController {

    private final ApprovalPendingRegistry approvalPendingRegistry;

    /**
     * 处理一条待审批的工具调用
     *
     * @param approvalId 审批 ID（通知中携带）
     * @param approved   true=本次放行（allow-once）；false=拒绝
     * @return true=处理成功；false=审批不存在或已处理/超时
     */
    @PostMapping("/{approvalId}")
    public R<Boolean> decideApproval(@PathVariable("approvalId") String approvalId,
                                     @RequestParam("approved") boolean approved) {
        boolean handled = approvalPendingRegistry.complete(approvalId, approved);
        if (!handled) {
            log.warn("审批处理失败（不存在或已处理/超时）: approvalId={}, approved={}", approvalId, approved);
        } else {
            log.info("审批已处理: approvalId={}, approved={}, operator={}",
                    approvalId, approved, io.lumina.common.core.BaseContext.getUsername());
        }
        return R.success(handled);
    }
}
