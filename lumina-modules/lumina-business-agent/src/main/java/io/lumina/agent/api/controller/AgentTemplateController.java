package io.lumina.agent.api.controller;

import io.lumina.agent.api.vo.AgentTemplateVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.service.AgentTemplateService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 模板与分享中心 API（角色包 = Agent 模板 + 技能集合）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Tag(name = "分享中心", description = "角色包导入导出、模板实例化、技能 URL 拉取")
@RestController
@RequirePermission("share:list")
@RequestMapping("/api/v1/agent-templates")
@RequiredArgsConstructor
@Validated
public class AgentTemplateController {

    private final AgentTemplateService agentTemplateService;

    @Operation(summary = "模板列表")
    @GetMapping
    public R<List<AgentTemplateVO>> list(@RequestParam(required = false) String name) {
        return R.success(agentTemplateService.list(name));
    }

    @Audit(module = "agent_template", action = "CREATE", description = "导入角色包")
    @Operation(summary = "导入角色包 zip（技能过体检入库 + 模板入库）")
    @RequirePermission("share:import")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AgentTemplateVO> importBundle(@RequestParam("file") MultipartFile file) {
        return R.success(agentTemplateService.importBundle(file));
    }

    @Audit(module = "agent_template", action = "CREATE", description = "模板实例化")
    @Operation(summary = "从模板实例化 Agent（名称冲突自动加后缀）")
    @RequirePermission("share:import")
    @PostMapping("/{id}/instantiate")
    public R<Map<String, Object>> instantiate(@PathVariable("id") Long id,
                                              @RequestParam(required = false) String name) {
        Agent agent = agentTemplateService.instantiate(id, name);
        return R.success(Map.of(
                "agentId", agent.getAgentId(),
                "agentName", agent.getAgentName(),
                "agentType", agent.getAgentType() != null ? agent.getAgentType() : ""));
    }

    @Audit(module = "agent_template", action = "DELETE", description = "删除模板")
    @Operation(summary = "删除模板（不影响已实例化的 Agent 与技能）")
    @RequirePermission("share:import")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        agentTemplateService.delete(id);
        return R.success();
    }

    /** 供 AgentController 复用的下载响应构造 */
    static ResponseEntity<byte[]> zipResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }
}
