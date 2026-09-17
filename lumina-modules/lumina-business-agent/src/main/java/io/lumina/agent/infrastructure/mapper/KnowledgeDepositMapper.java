package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.KnowledgeDepositDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识沉淀 Mapper（纯 CRUD 由 BaseMapper 提供，无聚合查询）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Mapper
public interface KnowledgeDepositMapper extends BaseMapper<KnowledgeDepositDO> {
}
