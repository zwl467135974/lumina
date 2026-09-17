package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.api.dto.SkillDTO;
import io.lumina.agent.api.dto.SkillImportResult;
import io.lumina.agent.api.vo.SkillVO;
import io.lumina.agent.domain.model.SkillScanReport;
import io.lumina.agent.infrastructure.entity.SkillDO;
import io.lumina.agent.infrastructure.mapper.SkillMapper;
import io.lumina.agent.security.PromptInjectionFilter;
import io.lumina.agent.security.SkillSecurityScanner;
import io.lumina.agent.service.SkillCatalogProvider;
import io.lumina.agent.service.SkillService;
import io.lumina.agent.service.support.SkillMarkdownParser;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.core.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 技能管理服务实现（同时实现渐进披露的目录提供者）
 *
 * <p>存储租户隔离（tenant_id）；{@link #loadContent} 加载前过注入检测
 * （fail-closed：命中返回 null，等同不可访问）。
 *
 * <p>SKILL.md 开放标准导入（3.12.0）：导入即强制体检——HIGH 拒收、MEDIUM
 * 落库禁用待复核、通过即启用；手工创建为咨询式体检（仅记录不阻断）。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService, SkillCatalogProvider {

    /** 单个 SKILL.md 最大字节数 */
    private static final long MAX_SKILLMD_BYTES = 2 * 1024 * 1024;

    /** 单次导入最大技能数 */
    private static final int MAX_SKILLS_PER_IMPORT = 100;

    /** zip 内最大条目数（防 zip 炸弹） */
    private static final int MAX_ZIP_ENTRIES = 1000;

    /** zip 解压总量上限（防 zip 炸弹） */
    private static final long MAX_ZIP_TOTAL_BYTES = 50L * 1024 * 1024;

    /** 描述/适用场景入库长度上限（与列宽一致，超出截断） */
    private static final int CATALOG_MAX_LENGTH = 500;

    /** 导出全部的技能数上限 */
    private static final int MAX_EXPORT_COUNT = 500;

    private final SkillMapper skillMapper;
    private final PromptInjectionFilter promptInjectionFilter;
    private final SkillSecurityScanner skillSecurityScanner;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO create(SkillDTO dto) {
        Long tenantId = currentTenant();
        if (existsByName(tenantId, dto.getName(), null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能名称已存在: " + dto.getName());
        }
        SkillDO skill = new SkillDO();
        applyDto(skill, dto);
        skill.setTenantId(tenantId);
        skill.setCreateBy(BaseContext.getUserId());
        skill.setIsDeleted(0);
        skill.setSource("MANUAL");
        // 手工创建为咨询式体检：记录结论供复核，不阻断管理员自己的写入
        applyScan(skill, dto.getName(), dto.getDescription(), dto.getContent(), List.of());
        skillMapper.insert(skill);
        log.info("创建技能: name={}, tenantId={}, scanStatus={}", dto.getName(), tenantId, skill.getScanStatus());
        return SkillVO.from(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO update(Long id, SkillDTO dto) {
        SkillDO skill = requireOwned(id);
        if (existsByName(skill.getTenantId(), dto.getName(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能名称已存在: " + dto.getName());
        }
        applyDto(skill, dto);
        skillMapper.updateById(skill);
        log.info("更新技能: id={}, name={}", id, dto.getName());
        return SkillVO.from(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO setEnabled(Long id, boolean enabled) {
        SkillDO skill = requireOwned(id);
        skill.setEnabled(enabled ? 1 : 0);
        skillMapper.updateById(skill);
        log.info("技能{}: id={}, name={}", enabled ? "启用" : "禁用", id, skill.getName());
        return SkillVO.from(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SkillDO skill = requireOwned(id);
        skillMapper.deleteById(skill.getId());
        log.info("删除技能: id={}, name={}", id, skill.getName());
    }

    @Override
    public List<SkillVO> list(String name, int pageNum, int pageSize) {
        LambdaQueryWrapper<SkillDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillDO::getTenantId, currentTenant());
        wrapper.eq(SkillDO::getIsDeleted, 0);
        if (name != null && !name.isBlank()) {
            wrapper.like(SkillDO::getName, name.trim());
        }
        wrapper.orderByDesc(SkillDO::getUpdateTime);
        Page<SkillDO> page = skillMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        PageResult<SkillVO> result = new PageResult<>();
        result.setList(page.getRecords().stream().map(SkillVO::from).toList());
        result.setTotal(page.getTotal());
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result.getList();
    }

    // ==================== SKILL.md 开放标准导入/导出（3.12.0） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillImportResult importSkills(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名为空");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        SkillImportResult result = new SkillImportResult();
        if (lower.endsWith(".zip")) {
            importFromZip(file, result);
        } else if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            String markdown = readAsText(file, filename);
            importOne(filename, markdown, List.of(), result);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 .md / .markdown / .zip 文件");
        }
        log.info("导入 SKILL.md: file={}, imported={}, rejected={}",
                filename, result.getImported().size(), result.getRejected().size());
        return result;
    }

    @Override
    public String exportMarkdown(Long id) {
        SkillDO skill = requireOwned(id);
        return SkillMarkdownParser.toMarkdown(
                skill.getName(), skill.getDescription(), skill.getWhenToUse(), skill.getContent());
    }

    @Override
    public byte[] exportAllAsZip() {
        List<SkillDO> skills = skillMapper.selectList(new LambdaQueryWrapper<SkillDO>()
                .eq(SkillDO::getTenantId, currentTenant())
                .eq(SkillDO::getIsDeleted, 0)
                .orderByAsc(SkillDO::getName)
                .last("LIMIT " + MAX_EXPORT_COUNT));
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (SkillDO skill : skills) {
                // name 为 kebab-case，可直接作目录名（开放标准目录结构 {name}/SKILL.md）
                zip.putNextEntry(new ZipEntry(skill.getName() + "/SKILL.md"));
                zip.write(SkillMarkdownParser.toMarkdown(
                        skill.getName(), skill.getDescription(), skill.getWhenToUse(), skill.getContent())
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出 zip 失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO rescan(Long id) {
        SkillDO skill = requireOwned(id);
        applyScan(skill, skill.getName(), skill.getDescription(), skill.getContent(), List.of());
        if ("REJECTED".equals(skill.getScanStatus())) {
            // fail-closed：复查出注入/破坏性内容时强制禁用（loadContent 同样会拒绝加载）
            skill.setEnabled(0);
        }
        skillMapper.updateById(skill);
        log.info("技能重扫: id={}, name={}, verdict={}", id, skill.getName(), skill.getScanStatus());
        return SkillVO.from(skill);
    }

    /** zip 导入：按顶层目录分组，含 SKILL.md 的目录为一个技能，其余文件作为捆绑项参与体检 */
    private void importFromZip(MultipartFile file, SkillImportResult result) {
        Map<String, StringBuilder> skillMdByFolder = new LinkedHashMap<>();
        Map<String, List<String>> bundledByFolder = new LinkedHashMap<>();
        int entries = 0;
        long totalBytes = 0;
        try (InputStream in = file.getInputStream(); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "zip 条目数超过上限 " + MAX_ZIP_ENTRIES);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..")) {
                    continue;
                }
                byte[] bytes = zip.readAllBytes();
                totalBytes += bytes.length;
                if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "zip 解压总量超过上限");
                }
                String folder = topFolder(name);
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if ("skill.md".equalsIgnoreCase(fileName) && bytes.length <= MAX_SKILLMD_BYTES) {
                    StringBuilder existing = skillMdByFolder.get(folder);
                    if (existing != null) {
                        result.addRejected(name, "目录内存在多个 SKILL.md");
                        continue;
                    }
                    StringBuilder builder = new StringBuilder(new String(bytes, StandardCharsets.UTF_8));
                    skillMdByFolder.put(folder, builder);
                } else if (bytes.length > MAX_SKILLMD_BYTES) {
                    result.addRejected(name, "SKILL.md 超过 " + MAX_SKILLMD_BYTES / 1024 / 1024 + "MB 上限");
                } else if (!folder.isEmpty()) {
                    // 捆绑文件（脚本/资源）不入库，但参与体检（可执行捆绑 → FLAGGED）
                    bundledByFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(fileName);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "zip 读取失败: " + e.getMessage());
        }
        if (skillMdByFolder.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "zip 中未找到 SKILL.md");
        }
        if (skillMdByFolder.size() > MAX_SKILLS_PER_IMPORT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次导入技能数超过上限 " + MAX_SKILLS_PER_IMPORT);
        }
        skillMdByFolder.forEach((folder, builder) -> importOne(
                folder.isEmpty() ? "SKILL.md" : folder,
                builder.toString(),
                bundledByFolder.getOrDefault(folder, List.of()),
                result));
    }

    /** 单技能导入：解析 → 查重 → 体检 → 落库（PASSED 启用 / FLAGGED 禁用待复核 / REJECTED 拒收） */
    private void importOne(String label, String markdown, List<String> bundledFiles, SkillImportResult result) {
        SkillMarkdownParser.ParsedSkill parsed;
        try {
            parsed = SkillMarkdownParser.parse(markdown);
        } catch (BusinessException e) {
            result.addRejected(label, e.getMessage());
            return;
        }
        Long tenantId = currentTenant();
        if (existsByName(tenantId, parsed.name(), null)) {
            result.addRejected(parsed.name(), "名称已存在（如需覆盖请先删除原技能）");
            return;
        }
        SkillScanReport report = skillSecurityScanner.scan(
                parsed.name(), parsed.description(), parsed.content(), bundledFiles);
        if (report.isRejected()) {
            result.addRejected(parsed.name(), "安全体检未通过: " + findingsSummary(report));
            log.warn("技能导入被拒收: name={}, findings={}", parsed.name(), findingsSummary(report));
            return;
        }

        SkillDO skill = new SkillDO();
        skill.setName(parsed.name());
        skill.setDescription(truncateCatalog(parsed.description()));
        skill.setWhenToUse(parsed.whenToUse().isEmpty() ? null : truncateCatalog(parsed.whenToUse()));
        skill.setContent(parsed.content());
        skill.setEnabled(report.isClean() ? 1 : 0);
        skill.setTenantId(tenantId);
        skill.setCreateBy(BaseContext.getUserId());
        skill.setIsDeleted(0);
        skill.setSource("IMPORT");
        skill.setScanStatus(report.getVerdict());
        skill.setScanReport(toJson(report));
        skillMapper.insert(skill);
        result.addImported(new SkillImportResult.ImportedSkill(
                skill.getId(), skill.getName(), skill.getScanStatus(), skill.getEnabled() == 1));
        log.info("导入技能: name={}, verdict={}, enabled={}", skill.getName(), report.getVerdict(), skill.getEnabled());
    }

    /** 扫描并写入体检结论（不改动 enabled，由调用方决定策略） */
    private void applyScan(SkillDO skill, String name, String description, String content, List<String> bundled) {
        SkillScanReport report = skillSecurityScanner.scan(name, description, content, bundled);
        skill.setScanStatus(report.getVerdict());
        skill.setScanReport(toJson(report));
    }

    private String readAsText(MultipartFile file, String label) {
        if (file.getSize() > MAX_SKILLMD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    label + " 超过 " + MAX_SKILLMD_BYTES / 1024 / 1024 + "MB 上限");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件读取失败: " + e.getMessage());
        }
    }

    private static String topFolder(String entryName) {
        int slash = entryName.indexOf('/');
        return slash < 0 ? "" : entryName.substring(0, slash);
    }

    private static String truncateCatalog(String value) {
        return value.length() <= CATALOG_MAX_LENGTH ? value : value.substring(0, CATALOG_MAX_LENGTH);
    }

    private String findingsSummary(SkillScanReport report) {
        return report.getFindings().stream()
                .map(f -> f.getSeverity() + ":" + f.getRule())
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");
    }

    private String toJson(SkillScanReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("体检报告序列化失败，仅存结论: {}", e.getMessage());
            return "{\"verdict\":\"" + report.getVerdict() + "\"}";
        }
    }

    // ==================== SkillCatalogProvider（渐进披露） ====================

    @Override
    public List<SkillCatalogEntry> listSkills() {
        LambdaQueryWrapper<SkillDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillDO::getTenantId, currentTenant());
        wrapper.eq(SkillDO::getIsDeleted, 0);
        wrapper.eq(SkillDO::getEnabled, 1);
        wrapper.orderByAsc(SkillDO::getName);
        return skillMapper.selectList(wrapper).stream()
                .map(s -> new SkillCatalogEntry(s.getName(), s.getDescription(), s.getWhenToUse()))
                .toList();
    }

    @Override
    public String loadContent(String name) {
        SkillDO skill = skillMapper.selectOne(new LambdaQueryWrapper<SkillDO>()
                .eq(SkillDO::getTenantId, currentTenant())
                .eq(SkillDO::getName, name)
                .eq(SkillDO::getIsDeleted, 0)
                .eq(SkillDO::getEnabled, 1)
                .last("LIMIT 1"));
        if (skill == null) {
            return null;
        }
        // fail-closed：技能内容被篡改（直连 DB 写入等）时按不可访问处理
        try {
            promptInjectionFilter.check(skill.getContent());
        } catch (Exception e) {
            log.warn("技能内容未过注入检测，拒绝加载: name={}, reason={}", name, e.getMessage());
            return null;
        }
        return skill.getContent();
    }

    // ==================== 私有方法 ====================

    private SkillDO requireOwned(Long id) {
        SkillDO skill = skillMapper.selectById(id);
        if (skill == null || skill.getIsDeleted() != 0
                || !skill.getTenantId().equals(currentTenant())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技能不存在");
        }
        return skill;
    }

    private boolean existsByName(Long tenantId, String name, Long excludeId) {
        LambdaQueryWrapper<SkillDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillDO::getTenantId, tenantId);
        wrapper.eq(SkillDO::getName, name);
        wrapper.eq(SkillDO::getIsDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(SkillDO::getId, excludeId);
        }
        return skillMapper.selectCount(wrapper) > 0;
    }

    private void applyDto(SkillDO skill, SkillDTO dto) {
        skill.setName(dto.getName());
        skill.setDescription(dto.getDescription());
        skill.setWhenToUse(dto.getWhenToUse());
        skill.setContent(dto.getContent());
        skill.setEnabled(dto.getEnabled() == null || dto.getEnabled() ? 1 : 0);
    }

    private Long currentTenant() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
