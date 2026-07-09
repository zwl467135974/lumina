package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.base.api.dto.dict.CreateDictItemDTO;
import io.lumina.base.api.dto.dict.CreateDictTypeDTO;
import io.lumina.base.api.dto.dict.UpdateDictItemDTO;
import io.lumina.base.api.dto.dict.UpdateDictTypeDTO;
import io.lumina.base.api.vo.dict.DictItemVO;
import io.lumina.base.api.vo.dict.DictTypeVO;
import io.lumina.base.infrastructure.entity.DictItemDO;
import io.lumina.base.infrastructure.entity.DictTypeDO;
import io.lumina.base.infrastructure.mapper.DictItemMapper;
import io.lumina.base.infrastructure.mapper.DictTypeMapper;
import io.lumina.base.service.DictService;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据字典业务服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    // ========== 字典类型 ==========

    @Override
    public List<DictTypeVO> listTypes(String dictName) {
        LambdaQueryWrapper<DictTypeDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictName)) {
            wrapper.like(DictTypeDO::getDictName, dictName);
        }
        wrapper.orderByAsc(DictTypeDO::getDictType);
        return dictTypeMapper.selectList(wrapper).stream()
                .map(this::toTypeVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO createType(CreateDictTypeDTO dto) {
        // 校验字典类型唯一性
        Long count = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<DictTypeDO>().eq(DictTypeDO::getDictType, dto.getDictType()));
        if (count > 0) {
            throw new BusinessException("字典类型已存在: " + dto.getDictType());
        }

        DictTypeDO entity = new DictTypeDO();
        entity.setDictType(dto.getDictType());
        entity.setDictName(dto.getDictName());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        entity.setRemark(dto.getRemark());
        dictTypeMapper.insert(entity);
        return toTypeVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO updateType(Long id, UpdateDictTypeDTO dto) {
        DictTypeDO entity = dictTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("字典类型不存在");
        }
        entity.setDictName(dto.getDictName());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
        dictTypeMapper.updateById(entity);
        return toTypeVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        DictTypeDO entity = dictTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("字典类型不存在");
        }
        // 级联删除该类型下的所有字典项
        dictItemMapper.delete(new LambdaQueryWrapper<DictItemDO>()
                .eq(DictItemDO::getDictType, entity.getDictType()));
        dictTypeMapper.deleteById(id);
        log.info("删除字典类型: {} ({})", entity.getDictName(), entity.getDictType());
    }

    // ========== 字典项 ==========

    @Override
    public List<DictItemVO> listItems(String dictType) {
        LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItemDO::getDictType, dictType)
                .orderByAsc(DictItemDO::getSortOrder);
        return dictItemMapper.selectList(wrapper).stream()
                .map(this::toItemVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO createItem(CreateDictItemDTO dto) {
        // 校验字典类型是否存在
        Long typeCount = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<DictTypeDO>().eq(DictTypeDO::getDictType, dto.getDictType()));
        if (typeCount == 0) {
            throw new BusinessException("字典类型不存在: " + dto.getDictType());
        }

        DictItemDO entity = new DictItemDO();
        entity.setDictType(dto.getDictType());
        entity.setDictLabel(dto.getDictLabel());
        entity.setDictValue(dto.getDictValue());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        entity.setRemark(dto.getRemark());
        dictItemMapper.insert(entity);
        return toItemVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO updateItem(Long id, UpdateDictItemDTO dto) {
        DictItemDO entity = dictItemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("字典项不存在");
        }
        entity.setDictLabel(dto.getDictLabel());
        entity.setDictValue(dto.getDictValue());
        entity.setSortOrder(dto.getSortOrder());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
        dictItemMapper.updateById(entity);
        return toItemVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        DictItemDO entity = dictItemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("字典项不存在");
        }
        dictItemMapper.deleteById(id);
    }

    // ========== 转换方法 ==========

    private DictTypeVO toTypeVO(DictTypeDO entity) {
        DictTypeVO vo = new DictTypeVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private DictItemVO toItemVO(DictItemDO entity) {
        DictItemVO vo = new DictItemVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
