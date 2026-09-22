package io.lumina.agent.orchestration.script;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自主编排计划投影器单元测试（v3.14 批次 3.3）
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class AutonomyPlanProjectorTest {

    @Test
    void extractsPhaseStagesInOrder() {
        AutonomyPlanProjector.Plan plan = AutonomyPlanProjector.extractPlan(
                "phase('检索资料');\nconst r = agent('查一下');\nphase('汇总产出');\nreturn r");

        assertThat(plan.stages()).containsExactly("检索资料", "汇总产出");
        assertThat(plan.agents()).containsExactly("查一下");
    }

    @Test
    void handlesDoubleQuotesAndWhitespace() {
        AutonomyPlanProjector.Plan plan = AutonomyPlanProjector.extractPlan(
                "phase(  \"数据准备\"  );\nphase( '分析' );");

        assertThat(plan.stages()).containsExactly("数据准备", "分析");
    }

    @Test
    void ignoresDynamicTitlesAndNonPhaseCalls() {
        AutonomyPlanProjector.Plan plan = AutonomyPlanProjector.extractPlan(
                "const t = '动态';\nphase(t);\nphases('x');\nlog('phase(不是阶段)');");

        assertThat(plan.stages()).isEmpty();
    }

    @Test
    void truncatesLongLiterals() {
        AutonomyPlanProjector.Plan plan = AutonomyPlanProjector.extractPlan(
                "agent('" + "很长的子任务提示".repeat(20) + "')");

        assertThat(plan.agents()).hasSize(1);
        assertThat(plan.agents().get(0).length()).isLessThanOrEqualTo(81);
    }

    @Test
    void emptyOrNullScriptYieldsEmptyPlan() {
        assertThat(AutonomyPlanProjector.extractPlan(null).isEmpty()).isTrue();
        assertThat(AutonomyPlanProjector.extractPlan("   ").isEmpty()).isTrue();
        assertThat(AutonomyPlanProjector.extractPlan("const a = 1").isEmpty()).isTrue();
    }
}
