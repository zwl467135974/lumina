package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.vo.AgentTraceVO;
import io.lumina.agent.infrastructure.entity.AgentTraceDO;
import io.lumina.agent.infrastructure.mapper.AgentTraceMapper;
import io.lumina.agent.service.AgentTraceService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent 推理链追踪查询服务实现
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private final AgentTraceMapper agentTraceMapper;

    @Override
    public PageResult<AgentTraceVO> list(Long agentId, String status, int pageNum, int pageSize) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;

        LambdaQueryWrapper<AgentTraceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTraceDO::getTenantId, tenantId);

        if (agentId != null) {
            wrapper.eq(AgentTraceDO::getAgentId, agentId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AgentTraceDO::getStatus, status);
        }
        wrapper.orderByDesc(AgentTraceDO::getCreateTime);

        Page<AgentTraceDO> page = agentTraceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<AgentTraceVO> voList = page.getRecords().stream()
                .map(AgentTraceVO::from)
                .toList();

        return new PageResult<>(voList, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AgentTraceVO getByUuid(String traceUuid) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;

        LambdaQueryWrapper<AgentTraceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTraceDO::getTraceUuid, traceUuid);
        wrapper.eq(AgentTraceDO::getTenantId, tenantId);

        AgentTraceDO trace = agentTraceMapper.selectOne(wrapper);
        if (trace == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Trace 不存在");
        }
        return AgentTraceVO.from(trace);
    }
}
