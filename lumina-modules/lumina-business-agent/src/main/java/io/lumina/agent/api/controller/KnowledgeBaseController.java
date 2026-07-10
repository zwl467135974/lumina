package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.KnowledgeBaseDTO;
import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;
import io.lumina.agent.service.KnowledgeBaseService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库联邦 API（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Audit(module = "knowledge_base", action = "CREATE", description = "创建知识库")
    @PostMapping
    public R<KnowledgeBaseDO> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return R.success(knowledgeBaseService.createKnowledgeBase(dto));
    }

    @GetMapping
    public R<List<KnowledgeBaseDO>> list(@RequestParam(required = false) String name) {
        return R.success(knowledgeBaseService.listKnowledgeBases(name));
    }

    @GetMapping("/{id}")
    public R<KnowledgeBaseDO> get(@PathVariable Long id) {
        return R.success(knowledgeBaseService.getKnowledgeBase(id));
    }

    @Audit(module = "knowledge_base", action = "DELETE", description = "删除知识库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return R.success();
    }

    @PostMapping("/{kbId}/agents/{agentId}/mount")
    public R<Void> mount(@PathVariable Long agentId, @PathVariable Long kbId) {
        knowledgeBaseService.mountKnowledgeBase(agentId, kbId);
        return R.success();
    }

    @DeleteMapping("/{kbId}/agents/{agentId}/mount")
    public R<Void> unmount(@PathVariable Long agentId, @PathVariable Long kbId) {
        knowledgeBaseService.unmountKnowledgeBase(agentId, kbId);
        return R.success();
    }

    @GetMapping("/agents/{agentId}")
    public R<List<KnowledgeBaseDO>> getAgentKnowledgeBases(@PathVariable Long agentId) {
        return R.success(knowledgeBaseService.getAccessibleKnowledgeBases(agentId));
    }
}
