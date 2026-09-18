package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 模板 DO（分享中心：角色包导入/本租户导出的可复用编排产物）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@TableName("lumina_agent_template")
public class AgentTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名（实例化时的 Agent 名基础） */
    private String name;

    private String agentType;

    private String description;

    /** 模板 JSON（agent.json 内容，已剥离密钥） */
    private String content;

    /** 随包技能名列表（逗号分隔） */
    private String skillNames;

    /** 来源：IMPORT=角色包导入 EXPORT=本租户导出 */
    private String source;

    /** 同模板重复导入自增 */
    private Integer version;

    private Long tenantId;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDeleted;
}
