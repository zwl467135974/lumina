package io.lumina.agent.security;

import io.lumina.agent.domain.model.SkillScanReport;
import io.lumina.agent.domain.model.SkillScanReport.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能上架安全体检扫描器
 *
 * <p>SKILL.md 开放标准生态的信任基础设施：导入前扫描技能内容，阻止提示注入与
 * 破坏性指令（HIGH→拒收），标记凭据外传与捆绑可执行文件等可疑项（MEDIUM→
 * 落库但禁用，待人工复核后启用）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Component
public class SkillSecurityScanner {

    /** 破坏性/隐蔽执行命令（HIGH）：命中即拒绝导入 */
    private static final List<Pattern> DANGEROUS_COMMAND_PATTERNS = List.of(
            Pattern.compile("(?i)rm\\s+-[rf]+\\s+/(\\s|$)"),
            Pattern.compile("(?i)(curl|wget)[^\\n]*\\|\\s*(sudo\\s+)?(ba)?sh(\\s|$)"),
            Pattern.compile("(?i)\\|\\s*base64\\s+(-d|-D|--decode)\\s*\\|\\s*(ba)?sh"),
            Pattern.compile("(?i)mkfs(\\.\\w+)?\\s+/dev/"),
            Pattern.compile("(?i)dd\\s+if=/dev/(zero|random)\\s+of=/dev/"),
            Pattern.compile("(?i)chmod\\s+-R\\s+777\\s+/"),
            Pattern.compile("(?i)powershell(\\.exe)?[^\\n]*\\s-(enc|encodedcommand)\\s+"),
            Pattern.compile("(?i)Invoke-Expression\\s+\\$"),
            Pattern.compile("(?i)crontab\\s+-r\\b"),
            Pattern.compile("(?i):\\(\\)\\s*\\{\\s*:\\|:\\s*&\\s*\\}\\s*;:") // fork bomb
    );

    /** 凭据/敏感文件访问（MEDIUM）：可疑，人工复核 */
    private static final List<Pattern> EXFIL_PATTERNS = List.of(
            Pattern.compile("(?i)\\.ssh/(id_rsa|id_ed25519|authorized_keys)"),
            Pattern.compile("(?i)\\.aws/credentials"),
            Pattern.compile("(?i)\\.netrc|\\.env\\b.*\\bcurl\\b|\\bcurl\\b[^\\n]*\\.env"),
            Pattern.compile("(?i)(api[_-]?key|secret|password|token)[^\\n]{0,40}(curl|wget|http[s]?://)"),
            Pattern.compile("(?i)http[s]?://[^\\s]{0,200}(api[_-]?key|secret|password)="),
            Pattern.compile("sk-[a-zA-Z0-9]{20,}")
    );

    /** 隐瞒行为指令（LOW）：仅提示 */
    private static final List<Pattern> CONCEALMENT_PATTERNS = List.of(
            Pattern.compile("(?i)do\\s+not\\s+tell\\s+the\\s+user"),
            Pattern.compile("(?i)don't\\s+tell\\s+the\\s+user"),
            Pattern.compile("(?i)hide\\s+this\\s+from\\s+the\\s+user"),
            Pattern.compile("(?i)\\bsecretly\\b"),
            Pattern.compile("(?i)without\\s+the\\s+user'?s?\\s+(knowledge|consent)")
    );

    /** 可执行/脚本类捆绑文件后缀（MEDIUM）：等同未审查的代码执行依赖 */
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            ".sh", ".bash", ".zsh", ".py", ".ps1", ".psm1", ".bat", ".cmd",
            ".exe", ".dll", ".so", ".dylib", ".js", ".mjs", ".ts", ".jar", ".rb", ".pl"
    );

    /** 发现项摘要最大长度（不回显全文，防体检报告本身成为注入载体） */
    private static final int DETAIL_MAX_LENGTH = 160;

    private final PromptInjectionFilter promptInjectionFilter;

    public SkillSecurityScanner(PromptInjectionFilter promptInjectionFilter) {
        this.promptInjectionFilter = promptInjectionFilter;
    }

    /**
     * 扫描一个待上架技能
     *
     * @param name 技能名
     * @param description 描述（进目录，会进模型上下文）
     * @param content 技能全文
     * @param bundledFiles 同目录捆绑的文件名列表（zip 导入时非 SKILL.md 的文件；单个导入传空）
     */
    public SkillScanReport scan(String name, String description, String content, List<String> bundledFiles) {
        List<Finding> findings = new ArrayList<>();

        // 目录字段与全文统一扫描（description 也会进上下文）
        String catalog = (name == null ? "" : name + "\n") + (description == null ? "" : description);
        String injection = promptInjectionFilter.detect(catalog);
        if (injection == null && content != null) {
            injection = promptInjectionFilter.detect(content);
        }
        if (injection != null) {
            findings.add(new Finding("HIGH", "prompt-injection", "命中注入规则: " + injection));
        }

        String fullText = catalog + "\n" + (content == null ? "" : content);
        collect(fullText, DANGEROUS_COMMAND_PATTERNS, "dangerous-command", "HIGH", findings);
        collect(fullText, EXFIL_PATTERNS, "credential-access", "MEDIUM", findings);
        collect(content == null ? "" : content, CONCEALMENT_PATTERNS, "concealment", "LOW", findings);

        if (bundledFiles != null) {
            for (String file : bundledFiles) {
                String lower = file.toLowerCase();
                if (EXECUTABLE_EXTENSIONS.stream().anyMatch(lower::endsWith)) {
                    findings.add(new Finding("MEDIUM", "executable-bundle", "捆绑可执行文件: " + file));
                }
            }
        }

        return SkillScanReport.of(findings);
    }

    private void collect(String text, List<Pattern> patterns, String rule, String severity, List<Finding> findings) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            int hits = 0;
            String sample = null;
            while (matcher.find() && hits < 3) {
                hits++;
                if (sample == null) {
                    sample = truncate(matcher.group());
                }
            }
            if (hits > 0) {
                findings.add(new Finding(severity, rule, "命中 " + hits + " 处，示例: " + sample));
            }
        }
    }

    private String truncate(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= DETAIL_MAX_LENGTH ? compact : compact.substring(0, DETAIL_MAX_LENGTH) + "...";
    }
}
