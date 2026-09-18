package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.OAuth2IdentityDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 三方身份绑定 Mapper
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Mapper
public interface OAuth2IdentityMapper extends BaseMapper<OAuth2IdentityDO> {
}
