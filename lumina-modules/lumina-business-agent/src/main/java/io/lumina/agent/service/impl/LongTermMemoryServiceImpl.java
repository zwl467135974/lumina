package io.lumina.agent.service.impl;

import io.lumina.agent.api.vo.LongTermMemoryVO;
import io.lumina.agent.infrastructure.entity.LongTermMemoryDO;
import io.lumina.agent.infrastructure.mapper.LongTermMemoryMapper;
import io.lumina.agent.service.LongTermMemoryService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

/**
 * 长期记忆服务实现
 *
 * <p>所有方法都强制 userId 属主校验，匿名请求直接拒绝（{@link ErrorCode#UNAUTHORIZED}）。
 *
 * @author Lumina Team
 * @since 3.10.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryServiceImpl implements LongTermMemoryService {

    private final LongTermMemoryMapper memoryMapper;

    @Override
    public List<LongTermMemoryVO> list(Long userId, Long agentId, int limit) {
        requireAuthenticated(userId);

        LambdaQueryWrapper<LongTermMemoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongTermMemoryDO::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(LongTermMemoryDO::getAgentId, agentId);
        }
        wrapper.orderByDesc(LongTermMemoryDO::getImportance)
                .orderByDesc(LongTermMemoryDO::getCreateTime)
                .last("LIMIT " + Math.min(limit, 500));

        return memoryMapper.selectList(wrapper).stream()
                .map(LongTermMemoryServiceImpl::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireAuthenticated(userId);

        LongTermMemoryDO memory = memoryMapper.selectById(id);
        if (memory == null || !userId.equals(memory.getUserId())) {
            // 不存在 / 非本人：统一返回 NOT_FOUND，避免泄露存在性
            throw new BusinessException(ErrorCode.NOT_FOUND, "记忆不存在");
        }
        memoryMapper.deleteById(id);
        log.info("删除长期记忆: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAll(Long userId, Long agentId) {
        requireAuthenticated(userId);

        LambdaQueryWrapper<LongTermMemoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongTermMemoryDO::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(LongTermMemoryDO::getAgentId, agentId);
        }
        int deleted = memoryMapper.delete(wrapper);
        log.info("清空长期记忆: userId={}, agentId={}, deleted={}", userId, agentId, deleted);
        return deleted;
    }

    /**
     * 校验登录态：userId 为空直接拒绝。
     *
     * <p>此前 Controller 直接调用 Mapper 时，delete/deleteAll 未做此校验，
     * 匿名请求触发 {@code eq(...::getUserId, null)} 会被 MyBatis-Plus 渲染成
     * {@code user_id IS NULL}，存在全表删除风险。
     */
    private void requireAuthenticated(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }

    private static LongTermMemoryVO toVO(LongTermMemoryDO do_) {
        LongTermMemoryVO vo = new LongTermMemoryVO();
        BeanUtils.copyProperties(do_, vo);
        return vo;
    }
}
