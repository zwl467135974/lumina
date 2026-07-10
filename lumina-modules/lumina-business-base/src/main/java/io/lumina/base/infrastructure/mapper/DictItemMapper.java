package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.DictItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典项 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItemDO> {}
