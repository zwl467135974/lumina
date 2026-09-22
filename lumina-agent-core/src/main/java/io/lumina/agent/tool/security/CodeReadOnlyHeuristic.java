package io.lumina.agent.tool.security;

import io.lumina.agent.util.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code code.execute} 只读启发式（弱判定，fail-closed）
 *
 * <p>对代码解释器的提交代码做静态扫描，只有同时满足双约束才认定只读：
 * <ul>
 *   <li><b>Python</b>：所有 import 的根模块都在白名单内（纯计算标准库），
 *       且不含危险词元（open/eval/exec/__import__ 等内置逃逸口）</li>
 *   <li><b>JavaScript</b>：不要求 import（无模块系统即纯表达式），
 *       但不含任何危险词元（require/fetch/process/fs 等）</li>
 * </ul>
 *
 * <p>纪律：这是启发式不是证明——白名单外的导入、无法解析的参数、
 * 超长代码（扫描成本封顶）一律返回 false 走人工审批。词元匹配用
 * 词边界正则避免误伤（如 {@code cos.} 不匹配 {@code \bos\b}）。
 *
 * @author Lumina Team
 * @since 3.13.0
 */
final class CodeReadOnlyHeuristic {

    /** 参与扫描的代码长度上限（超出直接判非只读，控制扫描成本） */
    private static final int MAX_CODE_LENGTH = 200_000;

    /** Python import 根模块白名单（纯计算，无文件/网络/进程/系统触达） */
    private static final Set<String> PYTHON_IMPORT_ALLOWLIST = Set.of(
            "json", "math", "cmath", "statistics", "decimal", "fractions",
            "datetime", "re", "string", "itertools", "collections", "functools",
            "heapq", "bisect", "typing", "random");

    /** Python 危险词元：不经 import 即可触达文件/执行/逃逸的内置口 */
    private static final List<Pattern> PYTHON_DENY_TOKENS = List.of(
            Pattern.compile("\\bopen\\s*\\("),
            Pattern.compile("\\beval\\s*\\("),
            Pattern.compile("\\bexec\\s*\\("),
            Pattern.compile("\\bcompile\\s*\\("),
            Pattern.compile("\\b__import__\\b"),
            Pattern.compile("\\bbreakpoint\\s*\\("),
            Pattern.compile("\\binput\\s*\\("),
            Pattern.compile("\\bglobals\\s*\\("),
            Pattern.compile("\\bvars\\s*\\("));

    /**
     * Python import 语句捕获：from-形式优先于裸 import——交替分支按序尝试，
     * "from math import sqrt" 被第一分支整段消耗后不会再把 "import sqrt"
     * 误判为独立导入；group(1)=from 模块，group(2)=裸 import 模块
     */
    private static final Pattern PYTHON_IMPORT =
            Pattern.compile("\\bfrom\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s+import|\\bimport\\s+([A-Za-z_][A-Za-z0-9_.]*)");

    /** JavaScript 危险词元：模块加载/网络/进程/文件/动态执行 */
    private static final List<Pattern> JS_DENY_TOKENS = List.of(
            Pattern.compile("\\brequire\\s*\\("),
            Pattern.compile("\\bimport\\b"),
            Pattern.compile("\\bfetch\\s*\\("),
            Pattern.compile("\\bXMLHttpRequest\\b"),
            Pattern.compile("\\bWebSocket\\b"),
            Pattern.compile("\\bprocess\\b"),
            Pattern.compile("\\bchild_process\\b"),
            Pattern.compile("\\bfs\\b"),
            Pattern.compile("\\bos\\b"),
            Pattern.compile("\\bpath\\b"),
            Pattern.compile("\\bhttp\\b"),
            Pattern.compile("\\bhttps\\b"),
            Pattern.compile("\\beval\\s*\\("),
            Pattern.compile("\\bFunction\\s*\\("));

    private CodeReadOnlyHeuristic() {
    }

    /**
     * 判定 code.execute 的提交代码是否只读
     *
     * @param paramsJson 工具参数 JSON（含 language 与 code 字段）
     * @return true = 双约束满足（白名单导入 + 无危险词元）；false = 判定不了或含危险词元
     */
    static boolean isProvablyReadOnly(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return false;
        }
        String language;
        String code;
        try {
            Map<String, Object> params = JsonUtils.OBJECT_MAPPER.readValue(paramsJson, Map.class);
            language = params.get("language") != null ? params.get("language").toString() : "";
            code = params.get("code") != null ? params.get("code").toString() : "";
        } catch (Exception e) {
            return false;
        }
        if (code == null || code.isEmpty() || code.length() > MAX_CODE_LENGTH) {
            return false;
        }
        String lang = language == null ? "" : language.trim().toLowerCase();
        if ("python".equals(lang) || "py".equals(lang) || "python3".equals(lang)) {
            return pythonIsReadOnly(code);
        }
        if ("javascript".equals(lang) || "js".equals(lang) || "node".equals(lang)) {
            return jsIsReadOnly(code);
        }
        // 其他语言不设白名单，一律不判定
        return false;
    }

    private static boolean pythonIsReadOnly(String code) {
        // 约束一：import 根模块全在白名单内
        Matcher imports = PYTHON_IMPORT.matcher(code);
        while (imports.find()) {
            String module = imports.group(1) != null ? imports.group(1) : imports.group(2);
            String root = module.split("\\.")[0];
            if (!PYTHON_IMPORT_ALLOWLIST.contains(root)) {
                return false;
            }
        }
        // 约束二：无危险词元
        return !containsAny(code, PYTHON_DENY_TOKENS);
    }

    private static boolean jsIsReadOnly(String code) {
        return !containsAny(code, JS_DENY_TOKENS);
    }

    private static boolean containsAny(String code, List<Pattern> tokens) {
        for (Pattern token : tokens) {
            if (token.matcher(code).find()) {
                return true;
            }
        }
        return false;
    }
}
