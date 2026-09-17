package io.lumina.agent.service.support;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SKILL.md 开放标准解析/序列化（Anthropic Agent Skills 格式）
 *
 * <p>格式为「YAML frontmatter + Markdown 正文」：
 * <pre>
 * ---
 * name: pdf-report-writer
 * description: 生成品牌一致的 PDF 报告
 * when_to_use: 用户要求导出报告时
 * ---
 * 正文（技能全文）...
 * </pre>
 *
 * <p>仅解析扁平键值（开放标准的 frontmatter 即扁平标量），带引号剥离与缩进
 * 续行（宽松兼容块标量）；避免为几十行解析引入 YAML 依赖。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public final class SkillMarkdownParser {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private static final int NAME_MAX = 64;

    private static final int DESCRIPTION_MAX = 500;

    private static final int WHEN_TO_USE_MAX = 500;

    private SkillMarkdownParser() {
    }

    /**
     * 解析后的技能（frontmatter 字段 + 正文）
     */
    public record ParsedSkill(String name, String description, String whenToUse, String content) {
    }

    /**
     * 解析 SKILL.md 全文
     *
     * @throws BusinessException name 缺失/非法、正文为空时
     */
    public static ParsedSkill parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SKILL.md 内容为空");
        }
        String[] parts = splitFrontmatter(markdown);
        Map<String, String> meta = parts[0] == null ? Map.of() : parseFlatYaml(parts[0]);
        String body = parts[1].strip();

        String name = meta.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SKILL.md frontmatter 缺少 name 字段");
        }
        if (name.length() > NAME_MAX || !NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "技能名必须是 kebab-case 且最长 64 字符: " + truncate(name));
        }
        // description 开放标准必填；缺失时给保守默认值，导入不因此失败
        String description = meta.getOrDefault("description", "").trim();
        if (description.isEmpty()) {
            description = "Imported skill (no description)";
        }
        String whenToUse = meta.getOrDefault("whenToUse", "").trim();

        if (body.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SKILL.md 正文为空（frontmatter 之后无内容）");
        }
        return new ParsedSkill(name, description, whenToUse, body);
    }

    /**
     * 序列化为 SKILL.md（导出）
     */
    public static String toMarkdown(String name, String description, String whenToUse, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append('\n');
        sb.append("description: ").append(escape(description)).append('\n');
        if (whenToUse != null && !whenToUse.isBlank()) {
            sb.append("when_to_use: ").append(escape(whenToUse.trim())).append('\n');
        }
        sb.append("---\n\n");
        sb.append(content == null ? "" : content.strip());
        sb.append('\n');
        return sb.toString();
    }

    /**
     * 拆出 frontmatter 块与正文；不以 --- 开头时整体视为正文（name 由文件名/目录名兜底场景不走此方法）
     */
    private static String[] splitFrontmatter(String markdown) {
        String normalized = markdown.replace("\r\n", "\n").stripLeading();
        if (!normalized.startsWith("---")) {
            return new String[] {null, markdown};
        }
        int firstLineEnd = normalized.indexOf('\n');
        if (firstLineEnd < 0) {
            return new String[] {null, markdown};
        }
        int closing = findClosingFence(normalized, firstLineEnd + 1);
        if (closing < 0) {
            return new String[] {null, markdown};
        }
        return new String[] {
                normalized.substring(firstLineEnd + 1, closing),
                normalized.substring(nextLineStart(normalized, closing))
        };
    }

    /** 从 from 开始找闭合的 --- 行（或 ... 行），返回该行的起始偏移 */
    private static int findClosingFence(String text, int from) {
        int idx = from;
        while (idx < text.length()) {
            int lineEnd = text.indexOf('\n', idx);
            String line = text.substring(idx, lineEnd < 0 ? text.length() : lineEnd).trim();
            if ("---".equals(line) || "...".equals(line)) {
                return idx;
            }
            if (lineEnd < 0) {
                return -1;
            }
            idx = lineEnd + 1;
        }
        return -1;
    }

    private static int nextLineStart(String text, int offset) {
        int lineEnd = text.indexOf('\n', offset);
        return lineEnd < 0 ? text.length() : lineEnd + 1;
    }

    /**
     * 扁平 YAML 解析：key: value，支持引号剥离与缩进续行（宽松块标量）
     */
    static Map<String, String> parseFlatYaml(String block) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = block.replace("\r\n", "\n").split("\n", -1);
        String currentKey = null;
        StringBuilder pending = null;
        for (String rawLine : lines) {
            if (rawLine.isBlank()) {
                continue;
            }
            String line = stripComment(rawLine);
            if (!line.isBlank() && line.length() > 1 && line.charAt(0) != ' '
                    && line.charAt(0) != '\t' && line.contains(":")) {
                // 新键：先把上一个键的续行收尾
                flushPending(result, currentKey, pending);
                int colon = line.indexOf(':');
                String key = normalizeKey(line.substring(0, colon).trim());
                String value = line.substring(colon + 1).trim();
                if (value.isEmpty() || value.equals(">") || value.equals("|")
                        || value.equals(">-") || value.equals("|-")) {
                    currentKey = key;
                    pending = new StringBuilder();
                } else {
                    result.put(key, stripQuotes(value));
                    currentKey = null;
                    pending = null;
                }
            } else if (currentKey != null) {
                // 缩进续行（块标量内容）
                pending.append(line.strip());
            }
        }
        flushPending(result, currentKey, pending);
        return result;
    }

    private static void flushPending(Map<String, String> result, String key, StringBuilder pending) {
        if (key != null && pending != null && !pending.isEmpty()) {
            result.put(key, stripQuotes(pending.toString()));
        }
    }

    /** when_to_use → whenToUse（蛇形转驼峰，仅这两类键存在） */
    private static String normalizeKey(String key) {
        if (key.contains("_")) {
            String[] parts = key.split("_");
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
                }
            }
            return sb.toString();
        }
        return key;
    }

    private static String stripQuotes(String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }

    /** 行内注释剥离：仅当 # 前有空白且不在引号内时生效 */
    private static String stripComment(String line) {
        boolean single = false;
        boolean doubleQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !doubleQ) {
                single = !single;
            } else if (c == '"' && !single) {
                doubleQ = !doubleQ;
            } else if (c == '#' && !single && !doubleQ && i > 0
                    && (line.charAt(i - 1) == ' ' || line.charAt(i - 1) == '\t')) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String escape(String value) {
        // 含冒号+空格、# 或首尾空白的值加双引号，保证导出的 frontmatter 可被再解析
        boolean needQuote = value.contains(": ") || value.contains(" #")
                || value.startsWith(" ") || value.endsWith(" ")
                || value.startsWith("\"") || value.startsWith("'");
        return needQuote ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value;
    }

    private static String truncate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "...";
    }
}
