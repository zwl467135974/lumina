package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.dto.SkillDTO;
import io.lumina.agent.api.vo.SkillVO;
import io.lumina.agent.infrastructure.entity.SkillDO;
import io.lumina.agent.infrastructure.mapper.SkillMapper;
import io.lumina.agent.security.PromptInjectionFilter;
import io.lumina.agent.service.SkillCatalogProvider;
import io.lumina.agent.service.SkillService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.core.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 技能管理服务实现（同时实现渐进披露的目录提供者）
 *
 * <p>存储租户隔离（tenant_id）；{@link #loadContent} 加载前过注入检测
 * （fail-closed：命中返回 null，等同不可访问）。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService, SkillCatalogProvider {

    private final SkillMapper skillMapper;
    private final PromptInjectionFilter promptInjectionFilter;

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
        skillMapper.insert(skill);
        log.info("创建技能: name={}, tenantId={}", dto.getName(), tenantId);
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
