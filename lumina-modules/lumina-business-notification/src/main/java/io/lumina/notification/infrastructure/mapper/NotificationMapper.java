package io.lumina.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.notification.infrastructure.entity.NotificationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {
}
