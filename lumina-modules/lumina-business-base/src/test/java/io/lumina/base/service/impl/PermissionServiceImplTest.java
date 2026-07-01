package io.lumina.base.service.impl;

import io.lumina.base.api.dto.permission.CreatePermissionDTO;
import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.infrastructure.entity.PermissionDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PermissionServiceImpl 单元测试
 *
 * <p>覆盖权限编码唯一性、CRUD 边界、树形结构构建。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void createPermissionSuccess() {
        CreatePermissionDTO dto = new CreatePermissionDTO();
        dto.setPermissionCode("system:user:create");
        dto.setPermissionName("创建用户");
        dto.setPermissionType(3);

        when(permissionMapper.selectByCode("system:user:create")).thenReturn(Collections.emptyList());
        when(permissionMapper.insert(any(PermissionDO.class))).thenAnswer(inv -> {
            ((PermissionDO) inv.getArgument(0)).setPermissionId(100L);
            return 1;
        });

        Long id = permissionService.createPermission(dto);
        assertThat(id).isEqualTo(100L);
    }

    @Test
    void createPermissionDuplicateThrows() {
        CreatePermissionDTO dto = new CreatePermissionDTO();
        dto.setPermissionCode("DUP");

        when(permissionMapper.selectByCode("DUP"))
                .thenReturn(List.of(new PermissionDO()));

        assertThatThrownBy(() -> permissionService.createPermission(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updatePermissionNotFoundThrows() {
        io.lumina.base.api.dto.permission.UpdatePermissionDTO dto =
                new io.lumina.base.api.dto.permission.UpdatePermissionDTO();
        dto.setPermissionId(99L);

        when(permissionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> permissionService.updatePermission(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deletePermissionNotFoundThrows() {
        when(permissionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> permissionService.deletePermission(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPermissionByIdNotFoundThrows() {
        when(permissionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> permissionService.getPermissionById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPermissionTreeBuildsHierarchy() {
        PermissionDO root = new PermissionDO();
        root.setPermissionId(1L);
        root.setParentId(0L);
        root.setPermissionCode("system");
        root.setPermissionName("系统");

        PermissionDO child = new PermissionDO();
        child.setPermissionId(2L);
        child.setParentId(1L);
        child.setPermissionCode("system:user");
        child.setPermissionName("用户管理");

        PermissionDO grandchild = new PermissionDO();
        grandchild.setPermissionId(3L);
        grandchild.setParentId(2L);
        grandchild.setPermissionCode("system:user:create");
        grandchild.setPermissionName("创建用户");

        when(permissionMapper.selectAllPermissions())
                .thenReturn(List.of(root, child, grandchild));

        List<PermissionVO> tree = permissionService.getPermissionTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getPermissionCode()).isEqualTo("system");

        List<PermissionVO> children = tree.get(0).getChildren();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getPermissionCode()).isEqualTo("system:user");

        List<PermissionVO> grandchildren = children.get(0).getChildren();
        assertThat(grandchildren).hasSize(1);
        assertThat(grandchildren.get(0).getPermissionCode()).isEqualTo("system:user:create");
    }

    @Test
    void getPermissionTreeEmptyReturnsEmpty() {
        when(permissionMapper.selectAllPermissions()).thenReturn(Collections.emptyList());

        List<PermissionVO> tree = permissionService.getPermissionTree();
        assertThat(tree).isEmpty();
    }
}
