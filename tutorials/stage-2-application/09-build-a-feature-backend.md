# 09 — 实战：从零写一个后端业务模块

> **前置要求**：已完成 [01-08 全部理念篇](README.md)
> **预计阅读**：45 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

前面 8 篇你理解了 Lumina 的设计理念。现在来**真刀真枪**——从零开始，在 Lumina 项目里实现一个完整的"公告管理"后端模块。

**这是整个教学体系最有价值的一篇**。学完你能复制这个流程，做任何新功能。

---

## 我们要做什么

实现一个"公告管理"模块，包含：
- 公告的增删改查（CRUD）
- 多租户隔离（每个租户的公告互相看不到）
- 权限控制（公告管理需要权限）
- 审计日志（创建/修改/删除自动记录）
- 参数校验（标题必填、内容长度限制）

每一步都对照 Lumina 现有模式（以 Agent 模块为模板）。

---

## Step 1：Flyway 建表

### 创建迁移脚本

```
lumina-modules/lumina-business-base/src/main/resources/db/migration/V45__add_announcement.sql
```

```sql
-- ========================================
-- 公告管理表
-- 版本：3.6.0
-- 说明：多租户公告，含逻辑删除和租户隔离
-- ========================================

CREATE TABLE IF NOT EXISTS `lumina_announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-草稿，1-发布）',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',                    -- ← 多租户必须
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',  -- ← 逻辑删除
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),                 -- ← 租户+状态索引
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告管理表';

-- 权限种子数据
INSERT INTO `lumina_permission` (`permission_code`, `permission_name`, `parent_id`)
VALUES ('announcement:list', '公告管理', (SELECT permission_id FROM (SELECT * FROM lumina_permission) p WHERE permission_code = 'system'));

-- 给超级管理员角色授权
INSERT INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, permission_id FROM lumina_permission WHERE permission_code = 'announcement:list';
```

> ⚠️ **写 SQL 前先 DESCRIBE 实际表！** 见 `.agents/skills/lumina_flyway/SKILL.md`。Lumina 曾因凭记忆写列名导致 CI 失败。

### 注意三个要点

1. **`tenant_id` 列必须有**——多租户拦截器靠它自动隔离
2. **`deleted` 列必须有**——逻辑删除靠它
3. **权限种子一起灌**——不然超管也看不到菜单

---

## Step 2：实体类（DO）

```
lumina-modules/lumina-business-base/src/main/java/io/lumina/base/infrastructure/entity/AnnouncementDO.java
```

```java
package io.lumina.base.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("lumina_announcement")
public class AnnouncementDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)        // 自增主键
    private Long id;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("status")
    private Integer status;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(value = "create_by", fill = FieldFill.INSERT)            // 自动填充
    private Long createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic                                         // 逻辑删除
    @TableField("deleted")
    private Integer deleted;
}
```

**对照模板**：看 `LlmProviderDO.java`，结构完全一致。

---

## Step 3：Mapper

```
lumina-modules/lumina-business-base/src/main/java/io/lumina/base/infrastructure/mapper/AnnouncementMapper.java
```

```java
package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.AnnouncementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnouncementMapper extends BaseMapper<AnnouncementDO> {
    // 空的！BaseMapper 提供全部 CRUD
}
```

**3 行代码**——对照 `AgentMapper.java`。

---

## Step 4：DTO + VO

```
.../api/dto/AnnouncementDTO.java
```

```java
package io.lumina.base.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnouncementDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字符")
    private String title;

    @Size(max = 5000, message = "内容不能超过 5000 字符")
    private String content;

    private Integer status;    // null 时默认 1（发布）
}
```

```
.../api/vo/AnnouncementVO.java
```

```java
package io.lumina.base.api.vo;

import io.lumina.base.infrastructure.entity.AnnouncementDO;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AnnouncementVO {
    private Long id;
    private String title;
    private String content;
    private Integer status;
    private Long createBy;
    private LocalDateTime createTime;

    // DO → VO 转换
    public static AnnouncementVO from(AnnouncementDO entity) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setStatus(entity.getStatus());
        vo.setCreateBy(entity.getCreateBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
```

> 注意 VO 不含 `tenantId` 和 `deleted`——前端不需要这些内部字段。

---

## Step 5：Service 接口 + 实现

```
.../service/AnnouncementService.java
```

```java
package io.lumina.base.service;

import io.lumina.base.api.vo.AnnouncementVO;
import io.lumina.common.core.PageResult;

public interface AnnouncementService {
    PageResult<AnnouncementVO> list(String title, Integer status, int pageNum, int pageSize);
    AnnouncementVO getById(Long id);
    AnnouncementVO create(io.lumina.base.api.dto.AnnouncementDTO dto);
    AnnouncementVO update(Long id, io.lumina.base.api.dto.AnnouncementDTO dto);
    void delete(Long id);
}
```

```
.../service/impl/AnnouncementServiceImpl.java
```

```java
package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.AnnouncementDTO;
import io.lumina.base.api.vo.AnnouncementVO;
import io.lumina.base.infrastructure.entity.AnnouncementDO;
import io.lumina.base.infrastructure.mapper.AnnouncementMapper;
import io.lumina.base.service.AnnouncementService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor                           // ← 构造器注入（不是 @Autowired！）
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;    // ← final 字段

    @Override
    public PageResult<AnnouncementVO> list(String title, Integer status, int pageNum, int pageSize) {
        // 动态条件查询
        LambdaQueryWrapper<AnnouncementDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(title)) {
            wrapper.like(AnnouncementDO::getTitle, title);
        }
        if (status != null) {
            wrapper.eq(AnnouncementDO::getStatus, status);
        }
        wrapper.orderByDesc(AnnouncementDO::getCreateTime);

        // 分页查询（tenant_id 被拦截器自动加）
        Page<AnnouncementDO> page = announcementMapper.selectPage(
            new Page<>(pageNum, pageSize), wrapper);

        // 转换为 VO
        var voList = page.getRecords().stream().map(AnnouncementVO::from).toList();
        return PageResult.of(voList, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AnnouncementVO getById(Long id) {
        AnnouncementDO entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return AnnouncementVO.from(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)          // ← 事务
    public AnnouncementVO create(AnnouncementDTO dto) {
        AnnouncementDO entity = new AnnouncementDO();
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);

        announcementMapper.insert(entity);                 // tenant_id 自动加
        log.info("公告创建: id={}, title={}", entity.getId(), entity.getTitle());
        return AnnouncementVO.from(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVO update(Long id, AnnouncementDTO dto) {
        AnnouncementDO existing = announcementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        existing.setTitle(dto.getTitle());
        existing.setContent(dto.getContent());
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        announcementMapper.updateById(existing);
        return AnnouncementVO.from(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (announcementMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        announcementMapper.deleteById(id);    // 逻辑删除（@TableLogic）
    }
}
```

---

## Step 6：Controller

```
.../api/controller/AnnouncementController.java
```

```java
package io.lumina.base.api.controller;

import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.api.dto.AnnouncementDTO;
import io.lumina.base.api.vo.AnnouncementVO;
import io.lumina.base.service.AnnouncementService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequirePermission("announcement:list")              // ← 权限
@RequestMapping("/api/v1/base/announcements")
@RequiredArgsConstructor
@Validated
@Tag(name = "公告管理", description = "公告 CRUD")   // ← Swagger 文档
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    @Operation(summary = "分页查询公告列表")
    public R<PageResult<AnnouncementVO>> list(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.success(announcementService.list(title, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询公告详情")
    public R<AnnouncementVO> getById(@PathVariable Long id) {
        return R.success(announcementService.getById(id));
    }

    @Audit(module = "announcement", action = "CREATE", description = "创建公告")
    @Operation(summary = "创建公告")
    @PostMapping
    public R<AnnouncementVO> create(@Valid @RequestBody AnnouncementDTO dto) {
        return R.success(announcementService.create(dto));
    }

    @Audit(module = "announcement", action = "UPDATE", description = "更新公告")
    @Operation(summary = "更新公告")
    @PutMapping("/{id}")
    public R<AnnouncementVO> update(@PathVariable Long id, @Valid @RequestBody AnnouncementDTO dto) {
        return R.success(announcementService.update(id, dto));
    }

    @Audit(module = "announcement", action = "DELETE", description = "删除公告")
    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return R.success();
    }
}
```

---

## 检查清单

对照 AGENTS.md 的 Pre-Delivery Checklist：

- [x] **分层完整**：Controller → Service → ServiceImpl → Mapper
- [x] **写操作事务**：create/update/delete 标注 `@Transactional(rollbackFor = Exception.class)`
- [x] **异常用 ErrorCode**：`throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在")`
- [x] **校验注解**：DTO 用 `@NotBlank`/`@Size`，Controller 用 `@Valid`
- [x] **Controller 注解**：`@Slf4j` + `@Validated` + `@RequiredArgsConstructor`
- [x] **Controller 权限**：`@RequirePermission("announcement:list")`
- [x] **@Audit 规范**：module 用小写 `"announcement"`，action 用标准枚举
- [x] **Swagger 注解**：`@Tag` + `@Operation`
- [x] **构造器注入**：`@RequiredArgsConstructor` + `final` 字段（不是 @Autowired）
- [x] **新表 tenant_id**：表有 `tenant_id` 列，拦截器自动检测

---

## 动手试试

按照上面 6 步，在你的项目里创建这些文件，然后：

1. **编译**：`mvn compile -pl lumina-modules/lumina-business-base -am`
2. **启动项目**：Flyway 自动执行 V45 建表
3. **测试接口**：
   ```bash
   # 先登录拿 Token
   curl -X POST http://localhost:8080/api/v1/base/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'

   # 创建公告
   curl -X POST http://localhost:8080/api/v1/base/announcements \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"title":"系统升级通知","content":"今晚 22:00 系统升级"}'

   # 查询列表
   curl http://localhost:8080/api/v1/base/announcements \
     -H "Authorization: Bearer <token>"
   ```
4. **去审计日志页看**：创建操作已被 @Audit 记录

---

## 小结

| 步骤 | 做什么 | 对照模板 |
|------|--------|----------|
| 1. Flyway | 建表 + 权限种子 | V4/V23 迁移脚本 |
| 2. DO | 实体类（@TableName/@TableLogic） | LlmProviderDO |
| 3. Mapper | 继承 BaseMapper | AgentMapper |
| 4. DTO/VO | 输入输出对象 | CreateAgentDTO/AgentVO |
| 5. Service | 业务逻辑 + @Transactional | AgentServiceImpl |
| 6. Controller | @RequirePermission + @Audit + @Valid | AgentController |

**记住这个流程**——Lumina 里每一个新功能都是这 6 步。

---

## 下一步

后端完成了，下一篇 [实战：前端页面](10-build-a-feature-frontend.md)——给公告管理加个前端页面。

> 🚀 [10 — 实战：前端开发 →](10-build-a-feature-frontend.md)

---

## 自测题

1. **为什么 Flyway 脚本里要一起灌权限种子数据？**
   <details><summary>答案</summary>不灌权限种子，超管角色也没有 announcement:list 权限，菜单不显示，接口返回 403。建表+权限必须一起做。</details>

2. **AnnouncementServiceImpl 为什么用 `@RequiredArgsConstructor` 而不是 `@Autowired`？**
   <details><summary>答案</summary>构造器注入（@RequiredArgsConstructor + final）更安全（不可变）、可测试（脱离容器）、符合 AGENTS.md 规范。@Autowired 字段注入是历史遗留，不推荐。</details>

3. **`announcementMapper.deleteById(id)` 实际执行什么 SQL？**
   <details><summary>答案</summary>因为有 @TableLogic，执行的是 UPDATE lumina_announcement SET deleted=1 WHERE id=? AND tenant_id=?（逻辑删除 + 租户隔离）。</details>

---

📝 **本篇撰写期间修正的代码**：无。本篇是教学示例代码（新建文件），未实际创建到项目中——你可以照着步骤自己创建。
