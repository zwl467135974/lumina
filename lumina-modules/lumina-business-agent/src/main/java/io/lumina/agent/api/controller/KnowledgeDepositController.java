package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.KnowledgeDepositDTO;
import io.lumina.agent.api.dto.KnowledgeDepositReviewDTO;
import io.lumina.agent.api.vo.KnowledgeDepositVO;
import io.lumina.agent.service.KnowledgeDepositService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识沉淀 API（自维护 Wiki 知识飞轮：任务产物 → 人工审核 → 入知识库）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Tag(name = "知识沉淀", description = "任务产物沉淀 → 审核 → 入库，让知识库随使用变厚")
@RestController
@RequirePermission("knowledge:deposit")
@RequestMapping("/api/v1/knowledge/deposits")
@RequiredArgsConstructor
@Validated
public class KnowledgeDepositController {

    private final KnowledgeDepositService knowledgeDepositService;

    @Audit(module = "knowledge_deposit", action = "CREATE", description = "发起知识沉淀")
    @Operation(summary = "发起沉淀（任务产物/手动内容进入待审队列）")
    @RequirePermission("knowledge:deposit")
    @PostMapping
    public R<KnowledgeDepositVO> create(@Valid @RequestBody KnowledgeDepositDTO dto) {
        return R.success(knowledgeDepositService.create(dto));
    }

    @Operation(summary = "沉淀分页列表（status/kbId 可选过滤）")
    @GetMapping
    public R<PageResult<KnowledgeDepositVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long kbId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.success(knowledgeDepositService.page(status, kbId, pageNum, pageSize));
    }

    @Audit(module = "knowledge_deposit", action = "UPDATE", description = "审核知识沉淀")
    @Operation(summary = "审核沉淀（通过即入库目标知识库，驳回留痕）")
    @RequirePermission("knowledge:review")
    @PostMapping("/{id}/review")
    public R<KnowledgeDepositVO> review(@PathVariable("id") Long id,
                                        @Valid @RequestBody KnowledgeDepositReviewDTO dto) {
        return R.success(knowledgeDepositService.review(id, dto));
    }
}
