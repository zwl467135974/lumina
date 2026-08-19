package io.lumina.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具审批待决注册表
 *
 * <p>审批端口发起审批时注册 future，审批端点（人工/回调）完成它。
 * 完成或超时后自动清理，无残留。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
public class ApprovalPendingRegistry {

    private final Map<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    /**
     * 注册一个待审批请求
     *
     * @return 审批结果 future（true=allow-once，false=拒绝）
     */
    public CompletableFuture<Boolean> register(String approvalId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.whenComplete((result, error) -> pending.remove(approvalId));
        pending.put(approvalId, future);
        log.debug("审批请求注册: approvalId={}, 当前待决={}", approvalId, pending.size());
        return future;
    }

    /**
     * 完成一个待审批请求
     *
     * @return true=完成成功；false=不存在或已处理/超时
     */
    public boolean complete(String approvalId, boolean approved) {
        CompletableFuture<Boolean> future = pending.remove(approvalId);
        return future != null && future.complete(approved);
    }
}
