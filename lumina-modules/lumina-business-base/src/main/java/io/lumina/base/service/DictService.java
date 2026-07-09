package io.lumina.base.service;

import io.lumina.base.api.dto.dict.CreateDictItemDTO;
import io.lumina.base.api.dto.dict.CreateDictTypeDTO;
import io.lumina.base.api.dto.dict.UpdateDictItemDTO;
import io.lumina.base.api.dto.dict.UpdateDictTypeDTO;
import io.lumina.base.api.vo.dict.DictItemVO;
import io.lumina.base.api.vo.dict.DictTypeVO;

import java.util.List;

/**
 * 数据字典业务服务
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface DictService {

    // ========== 字典类型 ==========

    /**
     * 查询字典类型列表
     */
    List<DictTypeVO> listTypes(String dictName);

    /**
     * 创建字典类型
     */
    DictTypeVO createType(CreateDictTypeDTO dto);

    /**
     * 更新字典类型
     */
    DictTypeVO updateType(Long id, UpdateDictTypeDTO dto);

    /**
     * 删除字典类型（级联删除其下所有字典项）
     */
    void deleteType(Long id);

    // ========== 字典项 ==========

    /**
     * 查询指定类型的字典项列表
     */
    List<DictItemVO> listItems(String dictType);

    /**
     * 创建字典项
     */
    DictItemVO createItem(CreateDictItemDTO dto);

    /**
     * 更新字典项
     */
    DictItemVO updateItem(Long id, UpdateDictItemDTO dto);

    /**
     * 删除字典项
     */
    void deleteItem(Long id);
}
