package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.MessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageDO> {
}
