package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.KnowledgeDepositDTO;
import io.lumina.agent.api.dto.KnowledgeDepositReviewDTO;
import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;
import io.lumina.agent.infrastructure.entity.KnowledgeDepositDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeDepositMapper;
import io.lumina.agent.service.KnowledgeBaseService;
import io.lumina.agent.service.KnowledgeService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KnowledgeDepositServiceImpl 单元测试（审核状态机、入库回链、租户隔离）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class KnowledgeDepositServiceImplTest {

    private final KnowledgeDepositMapper depositMapper = Mockito.mock(KnowledgeDepositMapper.class);

    private final KnowledgeBaseService knowledgeBaseService = Mockito.mock(KnowledgeBaseService.class);

    private final KnowledgeService knowledgeService = Mockito.mock(KnowledgeService.class);

    private final KnowledgeDepositServiceImpl service =
            new KnowledgeDepositServiceImpl(depositMapper, knowledgeBaseService, knowledgeService);

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createValidatesTargetKb() {
        BaseContext.setTenantId(7L);
        Mockito.when(knowledgeBaseService.getKnowledgeBase(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(dto("标题", "内容", 5L)))
                .hasMessageContaining("知识库不存在");
    }

    @Test
    void createInsertsPendingDeposit() {
        BaseContext.setTenantId(7L);
        Mockito.when(knowledgeBaseService.getKnowledgeBase(5L)).thenReturn(new KnowledgeBaseDO());
        Mockito.when(depositMapper.insert(Mockito.any(KnowledgeDepositDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, KnowledgeDepositDO.class).setId(1L);
            return 1;
        });

        var vo = service.create(dto("退款口径", "七天无理由…", 5L));

        assertThat(vo.getStatus()).isEqualTo("PENDING");
        Mockito.verify(depositMapper).insert(Mockito.<KnowledgeDepositDO>argThat(d ->
                "PENDING".equals(d.getStatus()) && d.getKbId().equals(5L) && d.getTenantId().equals(7L)));
    }

    @Test
    void reviewApprovedIngestsAndLinksDocUuid() {
        BaseContext.setTenantId(7L);
        Mockito.when(depositMapper.selectById(9L)).thenReturn(pendingDeposit());
        Mockito.when(knowledgeService.ingestText("退款口径", "七天无理由…", 5L)).thenReturn("doc-uuid-1");

        KnowledgeDepositReviewDTO review = new KnowledgeDepositReviewDTO();
        review.setApproved(true);
        var vo = service.review(9L, review);

        assertThat(vo.getStatus()).isEqualTo("APPROVED");
        assertThat(vo.getDocUuid()).isEqualTo("doc-uuid-1");
        Mockito.verify(depositMapper).updateById(Mockito.<KnowledgeDepositDO>argThat(d ->
                "APPROVED".equals(d.getStatus()) && "doc-uuid-1".equals(d.getDocUuid())
                        && d.getReviewTime() != null));
    }

    @Test
    void reviewRejectedSkipsIngest() {
        BaseContext.setTenantId(7L);
        Mockito.when(depositMapper.selectById(9L)).thenReturn(pendingDeposit());

        KnowledgeDepositReviewDTO review = new KnowledgeDepositReviewDTO();
        review.setApproved(false);
        review.setComment("内容过时");
        var vo = service.review(9L, review);

        assertThat(vo.getStatus()).isEqualTo("REJECTED");
        assertThat(vo.getDocUuid()).isNull();
        Mockito.verify(knowledgeService, Mockito.never()).ingestText(Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void reviewRejectsAlreadyReviewed() {
        BaseContext.setTenantId(7L);
        KnowledgeDepositDO done = pendingDeposit();
        done.setStatus("APPROVED");
        Mockito.when(depositMapper.selectById(9L)).thenReturn(done);

        KnowledgeDepositReviewDTO review = new KnowledgeDepositReviewDTO();
        review.setApproved(true);
        assertThatThrownBy(() -> service.review(9L, review))
                .hasMessageContaining("不可重复审核");
    }

    @Test
    void reviewRejectsCrossTenantAccess() {
        BaseContext.setTenantId(8L);
        Mockito.when(depositMapper.selectById(9L)).thenReturn(pendingDeposit());

        KnowledgeDepositReviewDTO review = new KnowledgeDepositReviewDTO();
        review.setApproved(true);
        assertThatThrownBy(() -> service.review(9L, review))
                .hasMessageContaining("不存在");
    }

    private KnowledgeDepositDTO dto(String title, String content, Long kbId) {
        KnowledgeDepositDTO dto = new KnowledgeDepositDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setKbId(kbId);
        dto.setSourceType("TASK");
        dto.setSourceId("task-uuid-1");
        return dto;
    }

    private KnowledgeDepositDO pendingDeposit() {
        KnowledgeDepositDO deposit = new KnowledgeDepositDO();
        deposit.setId(9L);
        deposit.setTitle("退款口径");
        deposit.setContent("七天无理由…");
        deposit.setKbId(5L);
        deposit.setStatus("PENDING");
        deposit.setTenantId(7L);
        deposit.setIsDeleted(0);
        return deposit;
    }
}
