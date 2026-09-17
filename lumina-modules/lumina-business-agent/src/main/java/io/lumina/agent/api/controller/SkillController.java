package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.SkillDTO;
import io.lumina.agent.api.dto.SkillImportResult;
import io.lumina.agent.api.vo.SkillVO;
import io.lumina.agent.service.SkillService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 技能管理 API（渐进披露：目录进上下文，全文按需加载）
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Tag(name = "技能管理", description = "Agent 技能的渐进披露管理")
@RestController
@RequirePermission("skill:list")
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Validated
public class SkillController {

    private final SkillService skillService;

    @Audit(module = "skill", action = "CREATE", description = "创建技能")
    @Operation(summary = "创建技能")
    @RequirePermission("skill:create")
    @PostMapping
    public R<SkillVO> create(@Valid @RequestBody SkillDTO dto) {
        return R.success(skillService.create(dto));
    }

    @Audit(module = "skill", action = "UPDATE", description = "更新技能")
    @Operation(summary = "更新技能")
    @RequirePermission("skill:update")
    @PutMapping("/{id}")
    public R<SkillVO> update(@PathVariable("id") Long id, @Valid @RequestBody SkillDTO dto) {
        return R.success(skillService.update(id, dto));
    }

    @Audit(module = "skill", action = "UPDATE", description = "启用/禁用技能")
    @Operation(summary = "启用/禁用技能")
    @RequirePermission("skill:update")
    @PostMapping("/{id}/enabled")
    public R<SkillVO> setEnabled(@PathVariable("id") Long id, @RequestParam("enabled") boolean enabled) {
        return R.success(skillService.setEnabled(id, enabled));
    }

    @Audit(module = "skill", action = "DELETE", description = "删除技能")
    @Operation(summary = "删除技能")
    @RequirePermission("skill:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        skillService.delete(id);
        return R.success();
    }

    @Operation(summary = "查询技能列表")
    @GetMapping
    public R<List<SkillVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.success(skillService.list(name, pageNum, pageSize));
    }

    @Audit(module = "skill", action = "CREATE", description = "导入SKILL.md技能")
    @Operation(summary = "导入 SKILL.md（开放标准，单个 .md 或多技能 .zip，导入前强制安全体检）")
    @RequirePermission("skill:create")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SkillImportResult> importSkills(@RequestParam("file") MultipartFile file) {
        return R.success(skillService.importSkills(file));
    }

    @Operation(summary = "导出技能为 SKILL.md")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportSkill(@PathVariable("id") Long id) {
        byte[] body = skillService.exportMarkdown(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=SKILL.md")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(body);
    }

    @Operation(summary = "导出全部技能为 zip（{name}/SKILL.md 目录结构）")
    @GetMapping("/export-all")
    public ResponseEntity<byte[]> exportAll() {
        byte[] body = skillService.exportAllAsZip();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lumina-skills.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @Audit(module = "skill", action = "UPDATE", description = "技能安全体检")
    @Operation(summary = "重跑安全体检（REJECTED 将强制禁用）")
    @RequirePermission("skill:update")
    @PostMapping("/{id}/rescan")
    public R<SkillVO> rescan(@PathVariable("id") Long id) {
        return R.success(skillService.rescan(id));
    }
}
