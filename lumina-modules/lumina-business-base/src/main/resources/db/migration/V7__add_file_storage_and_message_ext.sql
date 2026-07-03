-- V7: 文件存储表 + 消息表扩展

-- 文件元数据表
CREATE TABLE IF NOT EXISTS lumina_file (
    file_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_uuid      VARCHAR(64)  NOT NULL COMMENT '文件唯一标识',
    original_name  VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type   VARCHAR(128) NOT NULL COMMENT 'MIME 类型',
    file_size      BIGINT       NOT NULL COMMENT '文件大小（字节）',
    storage_key    VARCHAR(512) NOT NULL COMMENT '存储路径',
    storage_type   VARCHAR(20)  NOT NULL DEFAULT 'local' COMMENT 'local / minio',
    file_url       VARCHAR(1024) COMMENT '访问 URL',
    md5_hash       VARCHAR(32)  COMMENT 'MD5 校验值',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户 ID',
    biz_type       VARCHAR(50)  COMMENT '业务类型',
    biz_ref_id     VARCHAR(64)  COMMENT '业务关联 ID',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=已删除',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_uuid (file_uuid),
    KEY idx_tenant_biz (tenant_id, biz_type),
    KEY idx_md5 (md5_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据';

-- 消息表新增文件关联字段
ALTER TABLE lumina_message ADD COLUMN file_ids VARCHAR(512) COMMENT '关联文件 UUID 列表（JSON 数组）';
