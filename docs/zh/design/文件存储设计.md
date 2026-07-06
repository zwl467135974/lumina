# Lumina 文件存储模块设计

> 版本：v1.0  |  日期：2026-07-03  |  状态：实施中

---

## 1. 背景与问题

### 现状

| 场景 | 当前做法 | 问题 |
|------|---------|------|
| 知识库文档 | `Files.write` 写临时目录，解析后删除 | 原始文件不可追溯，无法重新解析 |
| 多模态图片 | Base64 硬塞 HTTP body，仅存内存 | 刷新页面图片丢失，消息表无文件引用 |
| 用户头像 | 无存储能力 | - |
| 导出/附件 | 无存储能力 | - |

### 根因

整个项目没有统一的文件存储抽象层。各业务模块各自 hack（临时文件 / Base64），无法扩展到生产环境。

---

## 2. 设计目标

1. **统一入口**：所有文件上传/下载走同一个 StorageClient 接口
2. **多后端**：开发用本地磁盘，生产用 MinIO（S3 兼容），切换只改配置
3. **元数据管理**：文件信息持久化到 DB，支持按租户/业务隔离
4. **消息引用**：多模态消息存文件 URL 引用，刷新不丢
5. **知识库改进**：文档上传后保留原始文件，支持重新解析

---

## 3. 技术选型

### MinIO

| 维度 | MinIO | 阿里云 OSS | 本地磁盘 |
|------|-------|-----------|---------|
| 协议 | S3 兼容 | S3 兼容 | 文件系统 |
| 部署 | Docker 一键 | 云服务 | 零部署 |
| 成本 | 免费 | 按量付费 | 免费 |
| 生产可用 | 是 | 是 | 否（单机） |

**选型**：StorageClient 接口 + 两套实现
- **LocalDiskStorageClient**：开发环境，文件存到 `./data/files/` 目录
- **MinioStorageClient**：生产环境，S3 兼容协议，可无缝切换 OSS/COS

切换方式：`storage.type=local` 或 `storage.type=minio`

---

## 4. 架构设计

### 4.1 分层

```
FileController          ← REST API（上传/下载/删除）
    ↓
FileService             ← 业务逻辑（元数据 CRUD、租户隔离、校验）
    ↓
StorageClient (接口)    ← 存储抽象
    ├── LocalDiskStorageClient   (storage.type=local)
    └── MinioStorageClient       (storage.type=minio)
    ↓
FileDO + FileMapper     ← 元数据持久化（file 表）
```

### 4.2 模块位置

放在 `lumina-framework` 中（而非新建模块），因为文件存储是横切关注点，所有业务模块都可能使用。

包路径：`io.lumina.framework.storage`

### 4.3 存储路径设计

```
按日期分目录，避免单目录文件过多：
data/files/
  └── {tenantId}/
      └── 2026/07/
          └── {uuid}.{ext}
          └── {uuid}.{ext}
```

MinIO 对应：
```
Bucket: lumina-files
Key: {tenantId}/2026/07/{uuid}.{ext}
```

---

## 5. 数据库设计

### file 表（Flyway V8）

```sql
CREATE TABLE lumina_file (
    file_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_uuid      VARCHAR(64)  NOT NULL COMMENT '文件唯一标识（对外暴露）',
    original_name  VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type   VARCHAR(128) NOT NULL COMMENT 'MIME 类型',
    file_size      BIGINT       NOT NULL COMMENT '文件大小（字节）',
    storage_key    VARCHAR(512) NOT NULL COMMENT '存储路径（相对路径或 S3 key）',
    storage_type   VARCHAR(20)  NOT NULL DEFAULT 'local' COMMENT 'local / minio',
    file_url       VARCHAR(1024) COMMENT '访问 URL（MinIO 预签名或本地相对路径）',
    md5_hash       VARCHAR(32)  COMMENT 'MD5 校验值',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户 ID',
    biz_type       VARCHAR(50)  COMMENT '业务类型：chat_image / knowledge_doc / avatar',
    biz_ref_id     VARCHAR(64)  COMMENT '业务关联 ID（如消息 ID）',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=已删除',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_uuid (file_uuid),
    KEY idx_tenant_biz (tenant_id, biz_type),
    KEY idx_md5 (md5_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据';
```

### lumina_message 表扩展

```sql
ALTER TABLE lumina_message ADD COLUMN file_ids VARCHAR(512) COMMENT '关联文件 ID 列表（JSON 数组）';
```

---

## 6. 接口设计

### StorageClient 接口

```java
public interface StorageClient {

    /** 上传文件，返回存储 key */
    String upload(InputStream stream, String key, String contentType);

    /** 下载文件 */
    InputStream download(String key);

    /** 删除文件 */
    void delete(String key);

    /** 获取访问 URL（本地=相对路径，MinIO=预签名 URL） */
    String getUrl(String key);

    /** 上传字节数组（便捷方法） */
    default String upload(byte[] bytes, String key, String contentType) {
        return upload(new ByteArrayInputStream(bytes), key, contentType);
    }
}
```

### REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/files/upload` | 上传文件（multipart），返回 `{fileUuid, url}` |
| GET | `/api/v1/files/{fileUuid}` | 获取文件元数据 |
| GET | `/api/v1/files/{fileUuid}/download` | 下载文件 |
| DELETE | `/api/v1/files/{fileUuid}` | 删除文件 |

上传参数：
- `file`: MultipartFile（必填）
- `bizType`: String（可选，如 `chat_image` / `knowledge_doc`）

上传响应：
```json
{
  "code": 200,
  "data": {
    "fileUuid": "a1b2c3d4...",
    "originalName": "photo.png",
    "contentType": "image/png",
    "fileSize": 581,
    "url": "/files/a1b2c3d4.../download"
  }
}
```

---

## 7. 多模态改造

### 改造前

```
前端选图片 → Base64 → POST multipart（images[]）→ 后端 Base64 → AgentScope ImageBlock
→ 消息表只存文本 → 刷新图片丢失
```

### 改造后

```
前端选图片 → POST /files/upload → 拿到 fileUuid + URL
→ POST /agents/{id}/execute/multimodal（传 fileUuid 列表）
→ 后端从 StorageClient 读取图片 → AgentScope ImageBlock
→ 消息表存 file_ids JSON → 刷新后通过 URL 重新渲染图片
```

### MessageVO 扩展

```typescript
interface MessageVO {
  // ... 已有字段
  fileIds?: string[]    // 关联文件 UUID 列表
}
```

前端加载消息时，如果 `fileIds` 非空，通过 `/api/v1/files/{uuid}/download` 拼接 URL 渲染缩略图。

---

## 8. 知识库改造

### 改造前

```
上传 → 临时写磁盘 → 解析 → Embedding → 删除临时文件
```

### 改造后

```
上传 → StorageClient 持久化 → 记录 file_uuid
→ 异步解析（从 StorageClient 读取）→ Embedding → 入库
→ 需要重新解析时从 StorageClient 重新读取，不需要用户重新上传
```

`lumina_knowledge_document` 表新增 `file_uuid` 字段关联原始文件。

---

## 9. 配置

```yaml
# application.yml
lumina:
  storage:
    type: ${STORAGE_TYPE:local}    # local / minio
    local:
      base-path: ${STORAGE_LOCAL_PATH:./data/files}
      url-prefix: /files           # 本地文件访问 URL 前缀
    minio:
      endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
      access-key: ${MINIO_ACCESS_KEY:minioadmin}
      secret-key: ${MINIO_SECRET_KEY:minioadmin}
      bucket: ${MINIO_BUCKET:lumina-files}
```

### docker-compose.yml 新增

```yaml
minio:
  image: minio/minio:latest
  ports:
    - "9000:9000"    # S3 API
    - "9001:9001"    # 管理控制台
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  command: server /data --console-address ":9001"
```

---

## 10. 安全

1. **租户隔离**：存储路径含 `tenant_id`，文件查询自动加租户条件
2. **文件类型白名单**：按 `bizType` 限制允许的 MIME 类型
   - `chat_image`: image/png, image/jpeg, image/webp
   - `knowledge_doc`: application/pdf, word, text
3. **大小限制**：图片 10MB / 文档 50MB
4. **访问控制**：下载接口校验文件归属租户

---

## 11. 实施计划

| 步骤 | 内容 | 涉及模块 |
|------|------|---------|
| 1 | docker-compose 加 MinIO | docker-compose.yml |
| 2 | StorageClient 接口 + Local/MinIO 实现 | lumina-framework |
| 3 | StorageProperties 配置类 | lumina-framework |
| 4 | Flyway V8：file 表 + message 表加字段 | lumina-business-base |
| 5 | FileDO + FileMapper + FileService | lumina-framework |
| 6 | FileController（上传/下载/删除） | lumina-business-agent |
| 7 | 多模态改造：图片走文件存储 | lumina-business-agent + 前端 |
| 8 | 知识库改造：保留原始文件 | lumina-business-agent |
| 9 | 单元测试 + E2E 验证 | 全部 |

---

## 12. 不包含在本期范围

- CDN 分发（生产环境图片加速）
- 图片压缩/缩略图自动生成（需要图片处理库）
- 文件分片上传（大文件场景）
- 版本管理（文件多版本，如文档更新）
