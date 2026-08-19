package io.lumina.agent.service;

import java.util.List;

/**
 * 技能目录提供者（渐进披露接缝）
 *
 * <p>借鉴 DeepSeek Harness 的两段式披露：
 * <ul>
 *   <li>目录阶段：引擎只在系统提示注入技能目录（name + 截断描述，
 *       {@code <available_skills>} 块），成本恒定为几百 token</li>
 *   <li>加载阶段：模型判断需要时调用 {@code util.loadSkill} 工具按名取全文，
 *       每次重读不缓存——技能更新下一次调用即生效</li>
 * </ul>
 *
 * <p>实现方注意：{@link #loadContent} 返回前必须做注入检测过滤，
 * 并确保租户隔离（当前用户只能取到本租户的技能）。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public interface SkillCatalogProvider {

    /**
     * 列出当前租户可用的技能目录条目
     *
     * @return 目录条目（仅 name/description/whenToUse，不含全文）
     */
    List<SkillCatalogEntry> listSkills();

    /**
     * 按名加载技能全文（租户隔离 + 注入过滤由实现方保证）
     *
     * @return 技能全文；不存在/禁用/跨租户返回 null
     */
    String loadContent(String name);

    /**
     * 技能目录条目（不含全文）
     */
    record SkillCatalogEntry(String name, String description, String whenToUse) {
    }
}
