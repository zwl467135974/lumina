package io.lumina.notification.api.controller;

import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.lumina.notification.api.dto.CreateWebhookDTO;
import io.lumina.notification.api.vo.WebhookVO;
import io.lumina.notification.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Webhook 订阅 Controller
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/webhooks")
@Validated
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * 创建 webhook 订阅（secret 明文仅在本次响应返回）
     */
    @PostMapping
    @Audit(module = "webhook", action = "CREATE", description = "创建 Webhook 订阅")
    public R<WebhookVO> createWebhook(@Valid @RequestBody CreateWebhookDTO dto) {
        log.info("创建 Webhook: name={}, channel={}", dto.getName(), dto.getChannel());
        return R.success(webhookService.createWebhook(dto));
    }

    /**
     * 查询当前用户的 webhook 订阅列表
     */
    @GetMapping
    public R<List<WebhookVO>> listWebhooks() {
        return R.success(webhookService.listWebhooks());
    }

    /**
     * 删除 webhook 订阅
     */
    @DeleteMapping("/{id}")
    @Audit(module = "webhook", action = "DELETE", description = "删除 Webhook 订阅", targetIdParam = "id")
    public R<Void> deleteWebhook(@PathVariable("id") Long id) {
        log.info("删除 Webhook: id={}", id);
        webhookService.deleteWebhook(id);
        return R.success();
    }

    /**
     * 发送测试消息（category=TEST）
     */
    @PostMapping("/{id}/test")
    @Audit(module = "webhook", action = "EXECUTE", description = "测试 Webhook", targetIdParam = "id")
    public R<Map<String, Boolean>> testWebhook(@PathVariable("id") Long id) {
        boolean success = webhookService.testWebhook(id);
        return R.success(Map.of("success", success));
    }
}
