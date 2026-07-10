package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeDO> {}
