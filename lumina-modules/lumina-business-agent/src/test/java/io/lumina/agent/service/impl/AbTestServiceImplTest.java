package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.AbExperimentDO;
import io.lumina.agent.infrastructure.entity.AbVariantDO;
import io.lumina.agent.infrastructure.mapper.AbExperimentMapper;
import io.lumina.agent.infrastructure.mapper.AbExposureMapper;
import io.lumina.agent.infrastructure.mapper.AbVariantMapper;
import io.lumina.agent.service.AbTestService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AbTestServiceImpl 单元测试
 *
 * <p>重点验证变体分配逻辑（流量判断 + 权重分配 + 粘滞）。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class AbTestServiceImplTest {

    @Mock
    private AbExperimentMapper experimentMapper;

    @Mock
    private AbVariantMapper variantMapper;

    @Mock
    private AbExposureMapper exposureMapper;

    @InjectMocks
    private AbTestServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrent(new LoginContext(0L, 1L, "admin", null, null));
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void assignVariantReturnsNullWhenNoRunningExperiment() {
        when(experimentMapper.selectOne(any())).thenReturn(null);

        AbTestService.VariantContext result = service.assignVariant(1L, "conv-1");

        assertThat(result).isNull();
    }

    @Test
    void assignVariantReturnsNullWhenNoVariants() {
        AbExperimentDO experiment = createRunningExperiment(1L, 1L, 100);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(variantMapper.selectList(any())).thenReturn(List.of());

        AbTestService.VariantContext result = service.assignVariant(1L, "conv-1");

        assertThat(result).isNull();
    }

    @Test
    void assignVariantSelectsByWeight() {
        AbExperimentDO experiment = createRunningExperiment(1L, 1L, 100);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);

        AbVariantDO variantA = createVariant(10L, 1L, "A", 50);
        AbVariantDO variantB = createVariant(11L, 1L, "B", 50);
        when(variantMapper.selectList(any())).thenReturn(List.of(variantA, variantB));

        // 多次调用验证能分配到变体（统计性测试）
        boolean gotA = false, gotB = false;
        for (int i = 0; i < 50; i++) {
            AbTestService.VariantContext ctx = service.assignVariant(1L, "conv-" + i);
            if (ctx != null) {
                if ("A".equals(ctx.variantName())) gotA = true;
                if ("B".equals(ctx.variantName())) gotB = true;
            }
        }
        // 两个变体都应该被分配到（50/50 权重，50次调用）
        assertThat(gotA).isTrue();
        assertThat(gotB).isTrue();
    }

    @Test
    void assignVariantIsStickyForSameConversation() {
        AbExperimentDO experiment = createRunningExperiment(1L, 1L, 100);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);

        AbVariantDO variantA = createVariant(10L, 1L, "A", 100);
        when(variantMapper.selectList(any())).thenReturn(List.of(variantA));

        // 同一 conversation 多次调用应返回相同变体
        AbTestService.VariantContext ctx1 = service.assignVariant(1L, "sticky-conv");
        AbTestService.VariantContext ctx2 = service.assignVariant(1L, "sticky-conv");

        assertThat(ctx1).isNotNull();
        assertThat(ctx2).isNotNull();
        assertThat(ctx1.variantId()).isEqualTo(ctx2.variantId());
    }

    @Test
    void recordExposureDoesNotThrow() {
        // 曝光记录失败不应传播异常
        org.assertj.core.api.Assertions.assertThatCode(() ->
                service.recordExposure(1L, 10L, "A", "conv-1", true, 150L, 200, null))
                .doesNotThrowAnyException();
    }

    private AbExperimentDO createRunningExperiment(Long id, Long agentId, int trafficPercent) {
        AbExperimentDO exp = new AbExperimentDO();
        exp.setId(id);
        exp.setAgentId(agentId);
        exp.setTrafficPercent(trafficPercent);
        exp.setStatus("RUNNING");
        exp.setTenantId(0L);
        return exp;
    }

    private AbVariantDO createVariant(Long id, Long experimentId, String name, int weight) {
        AbVariantDO v = new AbVariantDO();
        v.setId(id);
        v.setExperimentId(experimentId);
        v.setName(name);
        v.setWeight(weight);
        return v;
    }
}
