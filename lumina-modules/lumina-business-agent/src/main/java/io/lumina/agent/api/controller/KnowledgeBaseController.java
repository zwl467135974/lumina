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

/**
 * 知识库联邦 API（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Slf4j
@RestController
@RequirePermission("knowledge-base:list")
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
@Validated
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Audit(module = "knowledge_base", action = "CREATE", description = "创建知识库")
    @PostMapping
    public R<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return R.success(KnowledgeBaseVO.from(knowledgeBaseService.createKnowledgeBase(dto)));
    }

    @GetMapping
    public R<List<KnowledgeBaseVO>> list(@RequestParam(required = false) String name) {
        List<KnowledgeBaseVO> list = knowledgeBaseService.listKnowledgeBases(name).stream()
                .map(KnowledgeBaseVO::from)
                .toList();
        return R.success(list);
    }

    @GetMapping("/{id}")
    public R<KnowledgeBaseVO> get(@PathVariable Long id) {
        return R.success(KnowledgeBaseVO.from(knowledgeBaseService.getKnowledgeBase(id)));
    }

    @Audit(module = "knowledge_base", action = "DELETE", description = "删除知识库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return R.success();
    }

    @Audit(module = "knowledge_base", action = "UPDATE", description = "挂载知识库")
    @PostMapping("/{kbId}/agents/{agentId}/mount")
    public R<Void> mount(@PathVariable Long agentId, @PathVariable Long kbId) {
        knowledgeBaseService.mountKnowledgeBase(agentId, kbId);
        return R.success();
    }

    @Audit(module = "knowledge_base", action = "UPDATE", description = "卸载知识库")
    @DeleteMapping("/{kbId}/agents/{agentId}/mount")
    public R<Void> unmount(@PathVariable Long agentId, @PathVariable Long kbId) {
        knowledgeBaseService.unmountKnowledgeBase(agentId, kbId);
        return R.success();
    }

    @GetMapping("/agents/{agentId}")
    public R<List<KnowledgeBaseVO>> getAgentKnowledgeBases(@PathVariable Long agentId) {
        List<KnowledgeBaseVO> list = knowledgeBaseService.getAccessibleKnowledgeBases(agentId).stream()
                .map(KnowledgeBaseVO::from)
                .toList();
        return R.success(list);
    }
}
