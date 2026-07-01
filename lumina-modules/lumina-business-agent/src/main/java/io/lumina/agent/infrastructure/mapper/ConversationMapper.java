package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {
}
