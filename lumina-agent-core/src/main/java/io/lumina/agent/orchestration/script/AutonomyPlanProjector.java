package io.lumina.agent.orchestration.script;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自主编排计划投影器（v3.14 批次 3.3）
 *
 * <p>从编排脚本静态抽取结构化计划（阶段标题序列 + 子调用面），供人工审批
 * "先成图再执行"——审批人看到的是图不是日志。仅识别字符串字面量参数
 *（动态拼接的标题不在静态可知范围，运行期由 phase 钩子事件补全真实序列）。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
public final class AutonomyPlanProjector {

    /** phase("...") 字符串字面量（单双引号，容忍空白与尾随参数） */
    private static final Pattern PHASE_LITERAL = Pattern.compile(
            "phase\\(\\s*(['\"])((?:\\\\.|(?!\1).)*)\\1");

    /** agent("...") 字符串字面量（估算子调用面） */
    private static final Pattern AGENT_LITERAL = Pattern.compile(
            "agent\\(\\s*(['\"])((?:\\\\.|(?!\1).)*)\\1");

    private static final int MAX_STAGES = 50;
    private static final int MAX_PROMPT_LEN = 80;

    private AutonomyPlanProjector() {
    }

    /**
     * 抽取计划投影：阶段标题序列（phase 字面量）+ 子调用面（agent 字面量数）
     *
     * @param script 编排脚本（null/空返回空计划）
     */
    public static Plan extractPlan(String script) {
        if (script == null || script.isBlank()) {
            return new Plan(List.of(), List.of());
        }
        List<String> stages = extractLiterals(PHASE_LITERAL, script, MAX_STAGES);
        List<String> agents = extractLiterals(AGENT_LITERAL, script, MAX_STAGES);
        return new Plan(stages, agents);
    }

    private static List<String> extractLiterals(Pattern pattern, String script, int max) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(script);
        while (matcher.find() && found.size() < max) {
            String literal = matcher.group(2).trim();
            if (!literal.isEmpty()) {
                found.add(literal.length() > MAX_PROMPT_LEN
                        ? literal.substring(0, MAX_PROMPT_LEN) + "…" : literal);
            }
        }
        return found;
    }

    /**
     * 计划投影结果：stages = 声明的阶段序列；agents = 静态可见的子调用提示
     */
    public record Plan(List<String> stages, List<String> agents) {

        public boolean isEmpty() {
            return stages.isEmpty() && agents.isEmpty();
        }
    }
}
