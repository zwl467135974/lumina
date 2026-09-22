package io.lumina.agent.tool.security;

import io.lumina.agent.config.LuminaAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只读分级（v3.13）单元测试：分类器三层证据 + 代码启发式双约束 + 管线豁免语义
 *
 * @author Lumina Team
 * @since 3.13.0
 */
class ReadOnlyClassifierTest {

    private LuminaAgentProperties properties;
    private DefaultReadOnlyToolClassifier classifier;

    @BeforeEach
    void setUp() {
        properties = new LuminaAgentProperties();
        properties.getTool().setSecurity(new LuminaAgentProperties.SecurityConfig());
        properties.getTool().getSecurity().setReadonlyAutoApprove(true);
        classifier = new DefaultReadOnlyToolClassifier(properties);
    }

    private ToolExecutionContext ctx(String tool, String paramsJson, Boolean hint) {
        return new ToolExecutionContext(tool, "test", paramsJson, "c1", 1L, 1L, hint);
    }

    // ==================== 开关与名单 ====================

    @Test
    void disabledSwitchBlocksExemptionButNotPureClassification() {
        properties.getTool().getSecurity().setReadonlyAutoApprove(false);
        // 豁免用途受总开关门控
        assertThat(classifier.allowsExemption(ctx("util.search", "{}", null))).isFalse();
        assertThat(classifier.allowsExemption(ctx("code.execute", pyCode("print(1)"), null))).isFalse();
        // 纯判定不受开关影响（PLAN 会话模式依赖：用户显式选 plan 即是开关）
        assertThat(classifier.isProvablyReadOnly(ctx("util.search", "{}", null))).isTrue();
    }

    @Test
    void enabledSwitchMakesExemptionFollowClassification() {
        assertThat(classifier.allowsExemption(ctx("util.search", "{}", null))).isTrue();
        assertThat(classifier.allowsExemption(ctx("util.http", "{}", null))).isFalse();
    }

    @Test
    void readonlyListMatchesExactlyAndByPrefix() {
        // 默认名单含 util.search/util.time/util.math
        assertThat(classifier.isProvablyReadOnly(ctx("util.search", "{}", null))).isTrue();
        assertThat(classifier.isProvablyReadOnly(ctx("util.searchx", "{}", null))).isFalse();

        properties.getTool().getSecurity().setReadonlyTools(
                List.of("mcp__playwright__browser_snapshot", "mcp__playwright__browser_get*"));
        assertThat(classifier.isProvablyReadOnly(ctx("mcp__playwright__browser_snapshot", "{}", null))).isTrue();
        assertThat(classifier.isProvablyReadOnly(ctx("mcp__playwright__browser_get_urls", "{}", null))).isTrue();
        assertThat(classifier.isProvablyReadOnly(ctx("mcp__playwright__browser_click", "{}", null))).isFalse();
    }

    @Test
    void mcpReadOnlyHintTrueCountsAsEvidence() {
        assertThat(classifier.isProvablyReadOnly(ctx("mcp__x__read", "{}", true))).isTrue();
        // hint=false / null 不是证据
        assertThat(classifier.isProvablyReadOnly(ctx("mcp__x__read", "{}", false))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("mcp__x__read", "{}", null))).isFalse();
    }

    // ==================== code.execute 启发式 ====================

    @Test
    void pythonWithAllowlistedImportsAndNoEscapeTokensIsReadOnly() {
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "import json\nfrom math import sqrt\nprint(json.dumps({'v': sqrt(2)}))"), null))).isTrue();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode("print(sum(range(10)))"), null))).isTrue();
    }

    @Test
    void pythonWithNonAllowlistedImportIsNotReadOnly() {
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "import requests\nprint(1)"), null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "from os.path import join\nprint(1)"), null))).isFalse();
    }

    @Test
    void pythonWithEscapeTokensIsNotReadOnly() {
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "open('/etc/passwd')"), null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "eval('1+1')"), null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "__import__('os').system('ls')"), null))).isFalse();
    }

    @Test
    void pythonWordBoundaryDoesNotFalsePositive() {
        // cos. 不是 os.；importer 不是 import 的模块捕获场景
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", pyCode(
                "import math\nprint(math.cos(0))"), null))).isTrue();
    }

    @Test
    void javascriptWithoutDenyTokensIsReadOnly() {
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", jsCode(
                "const xs=[1,2,3]; console.log(xs.map(x=>x*2).reduce((a,b)=>a+b,0));"), null))).isTrue();
    }

    @Test
    void javascriptWithDenyTokensIsNotReadOnly() {
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", jsCode(
                "require('fs').readFileSync('/etc/passwd')"), null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", jsCode(
                "fetch('https://example.com')"), null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", jsCode(
                "eval('1')"), null))).isFalse();
    }

    @Test
    void otherLanguagesAndBrokenParamsNeverClassify() {
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute",
                "{\"language\":\"bash\",\"code\":\"ls\"}", null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", "not-json", null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", "{}", null))).isFalse();
        assertThat(classifier.isProvablyReadOnly(ctx("code.execute", null, null))).isFalse();
    }

    @Test
    void heuristicOnlyAppliesToCodeExecute() {
        // 其他工具即使参数长得像代码也不走启发式
        assertThat(classifier.isProvablyReadOnly(ctx("util.http",
                pyCode("print(1)"), null))).isFalse();
    }

    private String pyCode(String code) {
        return "{\"language\":\"python\",\"code\":" + jsonEscape(code) + "}";
    }

    private String jsCode(String code) {
        return "{\"language\":\"javascript\",\"code\":" + jsonEscape(code) + "}";
    }

    private String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
