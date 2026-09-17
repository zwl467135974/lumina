package io.lumina.agent.service.support;

import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SkillMarkdownParser 单元测试（SKILL.md 开放标准解析/序列化）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class SkillMarkdownParserTest {

    @Test
    void parseStandardFrontmatter() {
        String md = """
                ---
                name: pdf-report-writer
                description: 生成品牌一致的 PDF 报告
                when_to_use: 用户要求导出报告时
                ---
                ## 步骤
                1. 收集素材
                """;

        SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(md);

        assertThat(parsed.name()).isEqualTo("pdf-report-writer");
        assertThat(parsed.description()).isEqualTo("生成品牌一致的 PDF 报告");
        assertThat(parsed.whenToUse()).isEqualTo("用户要求导出报告时");
        assertThat(parsed.content()).startsWith("## 步骤");
    }

    @Test
    void parseQuotedAndCommentedValues() {
        String md = """
                ---
                name: "quoted-name"   # trailing comment
                description: '含: 冒号的描述'
                ---
                body
                """;

        SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(md);

        assertThat(parsed.name()).isEqualTo("quoted-name");
        assertThat(parsed.description()).isEqualTo("含: 冒号的描述");
    }

    @Test
    void parseBlockScalarDescription() {
        String md = """
                ---
                name: long-desc-skill
                description: >-
                    多行描述第一行
                    第二行
                ---
                body
                """;

        SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(md);

        assertThat(parsed.description()).contains("第一行").contains("第二行");
    }

    @Test
    void parseWithoutFrontmatterFailsOnMissingName() {
        assertThatThrownBy(() -> SkillMarkdownParser.parse("just body without frontmatter"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name");
    }

    @Test
    void parseRejectsInvalidName() {
        String md = "---\nname: Bad_Name\ndescription: x\n---\nbody";
        assertThatThrownBy(() -> SkillMarkdownParser.parse(md))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kebab-case");
    }

    @Test
    void parseRejectsBlankBody() {
        String md = "---\nname: empty-skill\ndescription: x\n---\n";
        assertThatThrownBy(() -> SkillMarkdownParser.parse(md))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正文为空");
    }

    @Test
    void parseDefaultsMissingDescription() {
        String md = "---\nname: no-desc\n---\nbody";
        SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(md);
        assertThat(parsed.description()).contains("no description");
    }

    @Test
    void roundTripPreservesFields() {
        String markdown = SkillMarkdownParser.toMarkdown(
                "round-trip", "描述: 带 冒号 #井号", "场景", "正文内容");

        SkillMarkdownParser.ParsedSkill parsed = SkillMarkdownParser.parse(markdown);

        assertThat(parsed.name()).isEqualTo("round-trip");
        assertThat(parsed.description()).isEqualTo("描述: 带 冒号 #井号");
        assertThat(parsed.whenToUse()).isEqualTo("场景");
        assertThat(parsed.content()).isEqualTo("正文内容");
    }
}
