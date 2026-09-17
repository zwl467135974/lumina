package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 技能 DO（渐进披露：目录进上下文，全文按需加载）
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Data
@TableName("lumina_skill")
public class SkillDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 技能名（kebab-case，租户内唯一） */
    private String name;

    /** 一句话描述（进目录，供模型判断是否加载） */
    private String description;

    /** 适用场景说明（进目录，可选） */
    private String whenToUse;

    /** 技能全文（loadSkill 按需加载） */
    private String content;

    /** 0=禁用 1=启用 */
    private Integer enabled;

    /** 来源：MANUAL=手工创建 IMPORT=SKILL.md 导入 */
    private String source;

    /** 体检状态：NONE=未体检 PASSED=通过 FLAGGED=可疑（禁用待复核） */
    private String scanStatus;

    /** 体检报告 JSON（findings 列表） */
    private String scanReport;

    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
