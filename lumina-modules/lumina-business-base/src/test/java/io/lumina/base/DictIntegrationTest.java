package io.lumina.base;

import io.lumina.base.api.dto.dict.CreateDictItemDTO;
import io.lumina.base.api.dto.dict.CreateDictTypeDTO;
import io.lumina.base.api.vo.dict.DictItemVO;
import io.lumina.base.api.vo.dict.DictTypeVO;
import io.lumina.base.service.DictService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据字典集成测试
 *
 * <p>核心验证点：lumina_dict_type / lumina_dict_item 表不含 tenant_id 列，
 * TenantLineHandler 自动检测后应跳过这两张表（不注入 tenant_id 条件）。
 * 若拦截器误注入会导致 SQL 异常（Unknown column 'tenant_id'）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>字典类型 CRUD — 无 SQL 异常</li>
 *   <li>字典项 CRUD — 无 SQL 异常</li>
 *   <li>字典数据跨租户共享（全局表，不隔离）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Transactional
class DictIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DictService dictService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    // ========== 字典类型 ==========

    @Test
    void createDictTypeSuccess() {
        CreateDictTypeDTO dto = new CreateDictTypeDTO();
        dto.setDictType("itest_gender");
        dto.setDictName("性别");

        DictTypeVO result = dictService.createType(dto);
        assertThat(result.getId()).isNotNull().isPositive();
        assertThat(result.getDictType()).isEqualTo("itest_gender");
        assertThat(result.getDictName()).isEqualTo("性别");
        assertThat(result.getStatus()).isEqualTo(1);
    }

    @Test
    void listDictTypesNoSqlException() {
        CreateDictTypeDTO dto = new CreateDictTypeDTO();
        dto.setDictType("itest_status");
        dto.setDictName("状态");
        dictService.createType(dto);

        List<DictTypeVO> types = dictService.listTypes(null);
        assertThat(types).isNotNull();
        assertThat(types).extracting(DictTypeVO::getDictType)
                .contains("itest_status");
    }

    @Test
    void duplicateDictTypeThrows() {
        CreateDictTypeDTO dto = new CreateDictTypeDTO();
        dto.setDictType("itest_dup_type");
        dto.setDictName("重复类型");
        dictService.createType(dto);

        CreateDictTypeDTO dup = new CreateDictTypeDTO();
        dup.setDictType("itest_dup_type");
        dup.setDictName("重复类型2");

        assertThatThrownBy(() -> dictService.createType(dup))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateDictTypeSuccess() {
        CreateDictTypeDTO dto = new CreateDictTypeDTO();
        dto.setDictType("itest_update");
        dto.setDictName("原始名称");
        DictTypeVO created = dictService.createType(dto);

        io.lumina.base.api.dto.dict.UpdateDictTypeDTO update =
                new io.lumina.base.api.dto.dict.UpdateDictTypeDTO();
        update.setDictName("更新名称");
        update.setStatus(1);

        DictTypeVO updated = dictService.updateType(created.getId(), update);
        assertThat(updated.getDictName()).isEqualTo("更新名称");
    }

    @Test
    void deleteDictTypeCascadeDeleteItems() {
        CreateDictTypeDTO dto = new CreateDictTypeDTO();
        dto.setDictType("itest_cascade");
        dto.setDictName("级联删除");
        DictTypeVO type = dictService.createType(dto);

        CreateDictItemDTO item = new CreateDictItemDTO();
        item.setDictType("itest_cascade");
        item.setDictLabel("项1");
        item.setDictValue("v1");
        dictService.createItem(item);

        dictService.deleteType(type.getId());

        List<DictItemVO> items = dictService.listItems("itest_cascade");
        assertThat(items).isEmpty();
    }

    // ========== 字典项 ==========

    @Test
    void createDictItemSuccess() {
        createType("itest_item_type", "测试类型");

        CreateDictItemDTO dto = new CreateDictItemDTO();
        dto.setDictType("itest_item_type");
        dto.setDictLabel("男");
        dto.setDictValue("male");
        dto.setSortOrder(1);

        DictItemVO result = dictService.createItem(dto);
        assertThat(result.getId()).isNotNull().isPositive();
        assertThat(result.getDictLabel()).isEqualTo("男");
        assertThat(result.getDictValue()).isEqualTo("male");
    }

    @Test
    void listDictItemsNoSqlException() {
        createType("itest_list_items", "列表项测试");
        createItem("itest_list_items", "活跃", "active", 1);
        createItem("itest_list_items", "禁用", "inactive", 2);

        List<DictItemVO> items = dictService.listItems("itest_list_items");
        assertThat(items).hasSize(2);
        assertThat(items).extracting(DictItemVO::getSortOrder)
                .containsExactly(1, 2);
    }

    @Test
    void createDictItemWithMissingTypeThrows() {
        CreateDictItemDTO dto = new CreateDictItemDTO();
        dto.setDictType("nonexistent_type_xyz");
        dto.setDictLabel("标签");
        dto.setDictValue("val");

        assertThatThrownBy(() -> dictService.createItem(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateAndDeleteDictItemSuccess() {
        createType("itest_item_crud", "项CRUD");
        DictItemVO item = createItem("itest_item_crud", "原始", "orig", 1);

        io.lumina.base.api.dto.dict.UpdateDictItemDTO update =
                new io.lumina.base.api.dto.dict.UpdateDictItemDTO();
        update.setDictLabel("修改后");
        update.setDictValue("updated");
        update.setSortOrder(2);
        update.setStatus(1);

        DictItemVO updated = dictService.updateItem(item.getId(), update);
        assertThat(updated.getDictLabel()).isEqualTo("修改后");

        dictService.deleteItem(item.getId());

        assertThatThrownBy(() -> dictService.updateItem(item.getId(), update))
                .isInstanceOf(BusinessException.class);
    }

    // ========== 跨租户共享（全局表不隔离） ==========

    @Test
    void dictTypeSharedAcrossTenants() {
        BaseContext.setTenantId(1L);
        createType("itest_shared", "跨租户共享");
        List<DictTypeVO> tenant1Types = dictService.listTypes(null);

        BaseContext.setTenantId(2L);
        List<DictTypeVO> tenant2Types = dictService.listTypes(null);

        assertThat(tenant1Types).isEqualTo(tenant2Types);
        assertThat(tenant2Types).extracting(DictTypeVO::getDictType)
                .contains("itest_shared");
    }

    // ========== 辅助方法 ==========

    private DictTypeVO createType(String type, String name) {
        CreateDictTypeDTO dto = new CreateDictTypeDTO();
        dto.setDictType(type);
        dto.setDictName(name);
        return dictService.createType(dto);
    }

    private DictItemVO createItem(String type, String label, String value, int sort) {
        CreateDictItemDTO dto = new CreateDictItemDTO();
        dto.setDictType(type);
        dto.setDictLabel(label);
        dto.setDictValue(value);
        dto.setSortOrder(sort);
        return dictService.createItem(dto);
    }
}
