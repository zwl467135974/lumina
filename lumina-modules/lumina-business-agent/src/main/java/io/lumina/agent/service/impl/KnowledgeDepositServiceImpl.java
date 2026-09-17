package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.dto.KnowledgeDepositDTO;
import io.lumina.agent.api.dto.KnowledgeDepositReviewDTO;
import io.lumina.agent.api.vo.KnowledgeDepositVO;
import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;
import io.lumina.agent.infrastructure.entity.KnowledgeDepositDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDepositMapper;
import io.lumina.agent.service.KnowledgeBaseService;
import io.lumina.agent.service.KnowledgeDepositService;
import io.lumina.agent.service.KnowledgeService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识沉淀服务实现
 *
 * <p>审核通过即同步入库（复用 {@link KnowledgeService#ingestText}：
 * KB 级分块 → 向量化 → MySQL chunk 双写），docUuid 回链文档列表。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDepositServiceImpl implements KnowledgeDepositService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final KnowledgeDepositMapper depositMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeService knowledgeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDepositVO create(KnowledgeDepositDTO dto) {
        KnowledgeBaseDO kb = knowledgeBaseService.getKnowledgeBase(dto.getKbId());
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标知识库不存在: " + dto.getKbId());
        }

        KnowledgeDepositDO deposit = new KnowledgeDepositDO();
        deposit.setTitle(dto.getTitle());
        deposit.setContent(dto.getContent());
        deposit.setSourceType(dto.getSourceType() == null ? "MANUAL" : dto.getSourceType());
        deposit.setSourceId(dto.getSourceId());
        deposit.setAgentId(dto.getAgentId());
        deposit.setKbId(dto.getKbId());
        deposit.setStatus(STATUS_PENDING);
        deposit.setTenantId(currentTenant());
        deposit.setCreateBy(BaseContext.getUserId());
        deposit.setIsDeleted(0);
        depositMapper.insert(deposit);
        log.info("知识沉淀已提交待审: id={}, title={}, kbId={}, sourceType={}",
                deposit.getId(), dto.getTitle(), dto.getKbId(), deposit.getSourceType());
        return KnowledgeDepositVO.from(deposit);
    }

    @Override
    public PageResult<KnowledgeDepositVO> page(String status, Long kbId, int pageNum, int pageSize) {
        LambdaQueryWrapper<KnowledgeDepositDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDepositDO::getTenantId, currentTenant());
        wrapper.eq(KnowledgeDepositDO::getIsDeleted, 0);
        if (status != null && !status.isBlank()) {
            wrapper.eq(KnowledgeDepositDO::getStatus, status);
        }
        if (kbId != null) {
            wrapper.eq(KnowledgeDepositDO::getKbId, kbId);
        }
        wrapper.orderByDesc(KnowledgeDepositDO::getId);
        Page<KnowledgeDepositDO> page = depositMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        PageResult<KnowledgeDepositVO> result = new PageResult<>();
        result.setList(page.getRecords().stream().map(KnowledgeDepositVO::from).toList());
        result.setTotal(page.getTotal());
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDepositVO review(Long id, KnowledgeDepositReviewDTO dto) {
        KnowledgeDepositDO deposit = requireOwned(id);
        if (!STATUS_PENDING.equals(deposit.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "沉淀已审核过（当前状态 " + deposit.getStatus() + "），不可重复审核");
        }

        deposit.setStatus(Boolean.TRUE.equals(dto.getApproved()) ? STATUS_APPROVED : STATUS_REJECTED);
        deposit.setReviewComment(dto.getComment());
        deposit.setReviewedBy(BaseContext.getUserId());
        deposit.setReviewTime(LocalDateTime.now());

        if (Boolean.TRUE.equals(dto.getApproved())) {
            // 审核通过 → 同步入库（失败抛异常回滚，沉淀保持 PENDING 可重新审核）
            String docUuid = knowledgeService.ingestText(deposit.getTitle(), deposit.getContent(), deposit.getKbId());
            deposit.setDocUuid(docUuid);
            log.info("知识沉淀审核通过并入库: id={}, docUuid={}, kbId={}", id, docUuid, deposit.getKbId());
        } else {
            log.info("知识沉淀审核驳回: id={}, comment={}", id, dto.getComment());
        }
        depositMapper.updateById(deposit);
        return KnowledgeDepositVO.from(deposit);
    }

    // ==================== 私有方法 ====================

    private KnowledgeDepositDO requireOwned(Long id) {
        KnowledgeDepositDO deposit = depositMapper.selectById(id);
        if (deposit == null || deposit.getIsDeleted() != 0
                || !deposit.getTenantId().equals(currentTenant())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识沉淀不存在");
        }
        return deposit;
    }

    private Long currentTenant() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
