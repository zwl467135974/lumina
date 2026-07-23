package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.dto.KnowledgeBaseDTO;
import io.lumina.agent.api.vo.KnowledgeBaseVO;
import io.lumina.agent.service.KnowledgeBaseService;
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
 * 知识库联邦 API（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Slf4j
@Tag(name = "知识库联邦", description = "知识库创建与 Agent 挂载/卸载")
@RestController
@RequirePermission("knowledge-base:list")
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
@Validated
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Audit(module = "knowledge_base", action = "CREATE", description = "创建知识库")
    @Operation(summary = "创建知识库")
    @PostMapping
    public R<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return R.success(KnowledgeBaseVO.from(knowledgeBaseService.createKnowledgeBase(dto)));
    }

    @Operation(summary = "查询知识库列表")

    @GetMapping
    public R<List<KnowledgeBaseVO>> list(@RequestParam(required = false) String name) {
        List<KnowledgeBaseVO> list = knowledgeBaseService.listKnowledgeBases(name).stream()
                .map(KnowledgeBaseVO::from)
                .toList();
        return R.success(list);
    }

    @Operation(summary = "查询知识库详情")

    @GetMapping("/{id}")
    public R<KnowledgeBaseVO> get(@PathVariable Long id) {
        return R.success(KnowledgeBaseVO.from(knowledgeBaseService.getKnowledgeBase(id)));
    }

    @Audit(module = "knowledge_base", action = "DELETE", description = "删除知识库")
    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return R.success();
    }

    @Audit(module = "knowledge_base", action = "UPDATE", description = "挂载知识库")
    @Operation(summary = "挂载知识库到 Agent")
    @PostMapping("/{kbId}/agents/{agentId}/mount")
    public R<Void> mount(@PathVariable Long agentId, @PathVariable Long kbId) {
        knowledgeBaseService.mountKnowledgeBase(agentId, kbId);
        return R.success();
    }

    @Audit(module = "knowledge_base", action = "UPDATE", description = "卸载知识库")
    @Operation(summary = "从 Agent 卸载知识库")
    @DeleteMapping("/{kbId}/agents/{agentId}/mount")
    public R<Void> unmount(@PathVariable Long agentId, @PathVariable Long kbId) {
        knowledgeBaseService.unmountKnowledgeBase(agentId, kbId);
        return R.success();
    }

    @Operation(summary = "查询 Agent 关联的知识库")

    @GetMapping("/agents/{agentId}")
    public R<List<KnowledgeBaseVO>> getAgentKnowledgeBases(@PathVariable Long agentId) {
        List<KnowledgeBaseVO> list = knowledgeBaseService.getAccessibleKnowledgeBases(agentId).stream()
                .map(KnowledgeBaseVO::from)
                .toList();
        return R.success(list);
    }
}
