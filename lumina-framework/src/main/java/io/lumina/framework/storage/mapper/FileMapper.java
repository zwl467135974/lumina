package io.lumina.framework.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.framework.storage.entity.FileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据 Mapper
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Mapper
public interface FileMapper extends BaseMapper<FileDO> {
}
