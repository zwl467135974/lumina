package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.infrastructure.entity.ToolArtifactDO;
import io.lumina.agent.infrastructure.mapper.ToolArtifactMapper;
import io.lumina.agent.tool.spill.ToolArtifactStore;
import io.lumina.common.core.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 工具结果存档的 DB 实现
 *
 * <p>全文存入 lumina_tool_artifact（租户隔离），取回时校验 tenant_id。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbToolArtifactStore implements ToolArtifactStore {

    private final ToolArtifactMapper toolArtifactMapper;

    @Override
    public String save(String conversationId, String toolName, String content) {
        ToolArtifactDO record = new ToolArtifactDO();
        record.setArtifactId(UUID.randomUUID().toString().replace("-", ""));
        record.setConversationId(conversationId);
        record.setToolName(toolName);
        record.setContent(content);
        record.setContentChars(content != null ? content.length() : 0);
        record.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
        record.setCreateBy(BaseContext.getUserId());
        record.setIsDeleted(0);
        toolArtifactMapper.insert(record);
        return record.getArtifactId();
    }

    @Override
    public String get(String artifactId) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        ToolArtifactDO record = toolArtifactMapper.selectOne(new LambdaQueryWrapper<ToolArtifactDO>()
                .eq(ToolArtifactDO::getArtifactId, artifactId)
                .eq(ToolArtifactDO::getTenantId, tenantId)
                .eq(ToolArtifactDO::getIsDeleted, 0)
                .last("LIMIT 1"));
        return record != null ? record.getContent() : null;
    }
}
