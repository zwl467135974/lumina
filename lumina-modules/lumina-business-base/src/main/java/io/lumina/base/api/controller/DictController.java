package io.lumina.base.api.controller;

import io.lumina.base.api.dto.dict.CreateDictItemDTO;
import io.lumina.base.api.dto.dict.CreateDictTypeDTO;
import io.lumina.base.api.dto.dict.UpdateDictItemDTO;
import io.lumina.base.api.dto.dict.UpdateDictTypeDTO;
import io.lumina.base.api.vo.dict.DictItemVO;
import io.lumina.base.api.vo.dict.DictTypeVO;
import io.lumina.base.service.DictService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典管理 Controller
 * <p>
 * 提供字典类型和字典项的 CRUD 接口。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/base/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    // ========== 字典类型 ==========

    @GetMapping("/types")
    public R<List<DictTypeVO>> listTypes(@RequestParam(required = false) String dictName) {
        return R.success(dictService.listTypes(dictName));
    }

    @PostMapping("/types")
    @Audit(module = "DICT", action = "CREATE_TYPE")
    public R<DictTypeVO> createType(@Valid @RequestBody CreateDictTypeDTO dto) {
        return R.success(dictService.createType(dto));
    }

    @PutMapping("/types/{id}")
    @Audit(module = "DICT", action = "UPDATE_TYPE")
    public R<DictTypeVO> updateType(@PathVariable Long id, @Valid @RequestBody UpdateDictTypeDTO dto) {
        return R.success(dictService.updateType(id, dto));
    }

    @DeleteMapping("/types/{id}")
    @Audit(module = "DICT", action = "DELETE_TYPE")
    public R<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return R.success();
    }

    // ========== 字典项 ==========

    @GetMapping("/items")
    public R<List<DictItemVO>> listItems(@RequestParam String dictType) {
        return R.success(dictService.listItems(dictType));
    }

    @PostMapping("/items")
    @Audit(module = "DICT", action = "CREATE_ITEM")
    public R<DictItemVO> createItem(@Valid @RequestBody CreateDictItemDTO dto) {
        return R.success(dictService.createItem(dto));
    }

    @PutMapping("/items/{id}")
    @Audit(module = "DICT", action = "UPDATE_ITEM")
    public R<DictItemVO> updateItem(@PathVariable Long id, @Valid @RequestBody UpdateDictItemDTO dto) {
        return R.success(dictService.updateItem(id, dto));
    }

    @DeleteMapping("/items/{id}")
    @Audit(module = "DICT", action = "DELETE_ITEM")
    public R<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return R.success();
    }
}
