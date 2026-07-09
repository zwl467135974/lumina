package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.dto.PromptDTO;
import io.lumina.agent.infrastructure.entity.PromptDO;
import io.lumina.agent.infrastructure.mapper.PromptMapper;
import io.lumina.agent.service.PromptService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Prompt 版本管理服务实现
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptMapper promptMapper;

    @Override
    @Transactional
    public PromptDO create(PromptDTO dto) {
        Long tenantId = currentTenant();

        LambdaQueryWrapper<PromptDO> check = new LambdaQueryWrapper<>();
        check.eq(PromptDO::getName, dto.getName());
        check.eq(PromptDO::getTenantId, tenantId);
        check.eq(PromptDO::getIsDeleted, 0);
        if (promptMapper.selectCount(check) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Prompt 名称已存在: " + dto.getName());
        }

        PromptDO entity = new PromptDO();
        entity.setName(dto.getName());
        entity.setVersion(1);
        entity.setContent(dto.getContent() != null ? dto.getContent() : "");
        entity.setDescription(dto.getDescription());
        entity.setAgentType(dto.getAgentType());
        entity.setVariables(dto.getVariables());
        entity.setStatus(0);
        entity.setIsActive(0);
        entity.setTenantId(tenantId);
        entity.setCreateBy(BaseContext.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsDeleted(0);
        promptMapper.insert(entity);

        log.info("Prompt 创建: name={}, version=1", dto.getName());
        return entity;
    }

    @Override
    public PromptDO update(Long id, PromptDTO dto) {
        PromptDO entity = getById(id);
        if (entity.getStatus() == 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "已发布的版本不能修改，请新建版本");
        }

        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getAgentType() != null) entity.setAgentType(dto.getAgentType());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        entity.setUpdateTime(LocalDateTime.now());
        promptMapper.updateById(entity);

        return entity;
    }

    @Override
    @Transactional
    public PromptDO publish(Long id) {
        PromptDO entity = getById(id);

        if (entity.getStatus() == 1 && entity.getIsActive() == 1) {
            return entity;
        }

        LambdaQueryWrapper<PromptDO> deactivate = new LambdaQueryWrapper<>();
        deactivate.eq(PromptDO::getName, entity.getName());
        deactivate.eq(PromptDO::getTenantId, entity.getTenantId());
        deactivate.eq(PromptDO::getIsActive, 1);
        for (PromptDO old : promptMapper.selectList(deactivate)) {
            old.setIsActive(0);
            promptMapper.updateById(old);
        }

        entity.setStatus(1);
        entity.setIsActive(1);
        entity.setUpdateTime(LocalDateTime.now());
        promptMapper.updateById(entity);

        log.info("Prompt 发布: name={}, version={}", entity.getName(), entity.getVersion());
        return entity;
    }

    @Override
    @Transactional
    public PromptDO newVersion(Long sourceId, PromptDTO dto) {
        PromptDO source = getById(sourceId);

        LambdaQueryWrapper<PromptDO> maxVersion = new LambdaQueryWrapper<>();
        maxVersion.eq(PromptDO::getName, source.getName());
        maxVersion.eq(PromptDO::getTenantId, source.getTenantId());
        maxVersion.orderByDesc(PromptDO::getVersion);
        maxVersion.last("LIMIT 1");
        PromptDO latest = promptMapper.selectOne(maxVersion);
        int nextVersion = (latest != null ? latest.getVersion() : 0) + 1;

        PromptDO entity = new PromptDO();
        entity.setName(source.getName());
        entity.setVersion(nextVersion);
        entity.setContent(dto.getContent() != null ? dto.getContent() : source.getContent());
        entity.setDescription(dto.getDescription() != null ? dto.getDescription() : source.getDescription());
        entity.setAgentType(source.getAgentType());
        entity.setVariables(dto.getVariables() != null ? dto.getVariables() : source.getVariables());
        entity.setStatus(0);
        entity.setIsActive(0);
        entity.setTenantId(source.getTenantId());
        entity.setCreateBy(BaseContext.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsDeleted(0);
        promptMapper.insert(entity);

        log.info("Prompt 新版本: name={}, version={}", entity.getName(), nextVersion);
        return entity;
    }

    @Override
    public void delete(Long id) {
        PromptDO entity = getById(id);
        entity.setIsDeleted(1);
        promptMapper.updateById(entity);
    }

    @Override
    public List<PromptDO> list(String name, int pageNum, int pageSize) {
        Long tenantId = currentTenant();
        LambdaQueryWrapper<PromptDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDO::getTenantId, tenantId);
        wrapper.eq(PromptDO::getIsDeleted, 0);
        if (StringUtils.hasText(name)) {
            wrapper.like(PromptDO::getName, name);
        }
        wrapper.orderByDesc(PromptDO::getUpdateTime);

        Page<PromptDO> page = promptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.getRecords();
    }

    @Override
    public List<PromptDO> getVersions(String name) {
        Long tenantId = currentTenant();
        LambdaQueryWrapper<PromptDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDO::getName, name);
        wrapper.eq(PromptDO::getTenantId, tenantId);
        wrapper.eq(PromptDO::getIsDeleted, 0);
        wrapper.orderByDesc(PromptDO::getVersion);
        return promptMapper.selectList(wrapper);
    }

    @Override
    public PromptDO getActive(String name) {
        Long tenantId = currentTenant();
        PromptDO tenantPrompt = selectActivePrompt(name, tenantId);
        if (tenantPrompt != null || tenantId == 0L) {
            return tenantPrompt;
        }
        return selectActivePrompt(name, 0L);
    }

    private PromptDO selectActivePrompt(String name, Long tenantId) {
        LambdaQueryWrapper<PromptDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDO::getName, name);
        wrapper.eq(PromptDO::getTenantId, tenantId);
        wrapper.eq(PromptDO::getIsActive, 1);
        wrapper.eq(PromptDO::getStatus, 1);
        wrapper.eq(PromptDO::getIsDeleted, 0);
        wrapper.last("LIMIT 1");
        return promptMapper.selectOne(wrapper);
    }

    @Override
    public PromptDO getById(Long id) {
        PromptDO entity = promptMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Prompt 不存在");
        }
        if (!currentTenant().equals(entity.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return entity;
    }

    @Override
    public String fillTemplate(String template, Map<String, String> variables) {
        if (template == null) return "";
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return result;
    }

    private Long currentTenant() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
