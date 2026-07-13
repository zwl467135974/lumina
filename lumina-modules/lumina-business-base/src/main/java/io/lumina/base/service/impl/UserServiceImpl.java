package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.user.AssignRoleDTO;
import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.ResetPasswordDTO;
import io.lumina.base.api.dto.user.UpdateUserDTO;
import io.lumina.base.api.dto.user.UserQueryDTO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.entity.UserRoleDO;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.base.infrastructure.mapper.UserRoleMapper;
import io.lumina.base.service.UserService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.PasswordUtil;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RedisCacheManager redisCacheManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(CreateUserDTO dto) {
        log.info("创建用户: username={}, tenantId={}", dto.getUsername(), BaseContext.getTenantId());

        // 1. 验证用户名唯一性（租户内）
        UserDO existingUser = userMapper.selectByTenantIdAndUsername(
                BaseContext.getTenantId(), dto.getUsername());
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 创建用户实体
        UserDO userDO = new UserDO();
        userDO.setTenantId(BaseContext.getTenantId());
        userDO.setUsername(dto.getUsername());
        userDO.setPassword(PasswordUtil.hash(dto.getPassword()));
        userDO.setNickname(dto.getNickname());
        userDO.setEmail(dto.getEmail());
        userDO.setPhone(dto.getPhone());
        userDO.setAvatar(dto.getAvatar());
        userDO.setStatus(1); // 默认启用

        // 3. 插入用户
        userMapper.insert(userDO);

        // 4. 分配角色（如果有）
        if (dto.getRoleIds() != null && dto.getRoleIds().length > 0) {
            List<UserRoleDO> userRoleDOs = new ArrayList<>();
            for (Long roleId : dto.getRoleIds()) {
                UserRoleDO userRoleDO = new UserRoleDO();
                userRoleDO.setUserId(userDO.getUserId());
                userRoleDO.setRoleId(roleId);
                userRoleDOs.add(userRoleDO);
            }
            userRoleMapper.insertBatch(userRoleDOs);
        }

        log.info("用户创建成功: userId={}, username={}", userDO.getUserId(), userDO.getUsername());
        return userDO.getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateUser(UpdateUserDTO dto) {
        log.info("更新用户: userId={}", dto.getUserId());

        // 1. 查询用户
        UserDO userDO = userMapper.selectById(dto.getUserId());
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 租户隔离验证
        if (!userDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限操作此用户");
        }

        // 3. 更新字段
        if (dto.getNickname() != null) {
            userDO.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            userDO.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            userDO.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            userDO.setAvatar(dto.getAvatar());
        }
        if (dto.getStatus() != null) {
            userDO.setStatus(dto.getStatus());
        }

        // 4. 更新
        int result = userMapper.updateById(userDO);

        log.info("用户更新成功: userId={}", dto.getUserId());
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteUser(Long userId) {
        log.info("删除用户: userId={}", userId);

        // 1. 查询用户
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 租户隔离验证
        if (!userDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限操作此用户");
        }

        // 3. 不能删除系统管理员
        if (userDO.getTenantId() == 0 && "admin".equals(userDO.getUsername())) {
            throw new BusinessException(ErrorCode.USER_IS_ADMIN, "不能删除系统管理员");
        }

        // 4. 逻辑删除（deleteById 自动处理 @TableLogic 字段，updateById 会忽略逻辑删除字段）
        int result = userMapper.deleteById(userId);

        log.info("用户删除成功: userId={}", userId);
        return result > 0;
    }

    @Override
    public UserVO getUserById(Long userId) {
        log.info("查询用户详情: userId={}", userId);

        // 1. 查询用户
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 租户隔离验证
        if (!userDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限查看此用户");
        }

        // 3. 转换为 VO
        UserVO userVO = toVO(userDO);

        // 4. 加载角色
        List<RoleDO> roleDOs = roleMapper.selectRolesByUserId(userId);
        List<UserVO.RoleVO> roleVOs = roleDOs.stream()
                .map(roleDO -> {
                    UserVO.RoleVO roleVO = new UserVO.RoleVO();
                    roleVO.setRoleId(roleDO.getRoleId());
                    roleVO.setRoleCode(roleDO.getRoleCode());
                    roleVO.setRoleName(roleDO.getRoleName());
                    return roleVO;
                })
                .collect(Collectors.toList());
        userVO.setRoles(roleVOs);

        return userVO;
    }

    @Override
    public UserVO getUserByUsername(String username) {
        log.info("根据用户名查询用户: username={}", username);

        // 1. 查询用户（带租户隔离）
        Long tenantId = BaseContext.getTenantId();
        UserDO userDO = userMapper.selectByTenantIdAndUsername(tenantId, username);
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 转换为 VO
        UserVO userVO = toVO(userDO);

        // 3. 加载角色
        List<RoleDO> roleDOs = roleMapper.selectRolesByUserId(userDO.getUserId());
        List<UserVO.RoleVO> roleVOs = roleDOs.stream()
                .map(roleDO -> {
                    UserVO.RoleVO roleVO = new UserVO.RoleVO();
                    roleVO.setRoleId(roleDO.getRoleId());
                    roleVO.setRoleCode(roleDO.getRoleCode());
                    roleVO.setRoleName(roleDO.getRoleName());
                    return roleVO;
                })
                .collect(Collectors.toList());
        userVO.setRoles(roleVOs);

        return userVO;
    }

    @Override
    public Page<UserVO> listUsers(UserQueryDTO dto) {
        log.info("分页查询用户: current={}, size={}", dto.getCurrent(), dto.getSize());

        // 1. 构建查询条件
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getTenantId, BaseContext.getTenantId());

        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            wrapper.like(UserDO::getUsername, dto.getUsername());
        }
        if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
            wrapper.like(UserDO::getNickname, dto.getNickname());
        }
        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            wrapper.eq(UserDO::getEmail, dto.getEmail());
        }
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            wrapper.eq(UserDO::getPhone, dto.getPhone());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(UserDO::getStatus, dto.getStatus());
        }

        // 2. 分页查询
        Page<UserDO> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<UserDO> userDOPage = userMapper.selectPage(page, wrapper);

        // 3. 转换为 VO
        Page<UserVO> userVOPage = new Page<>();
        userVOPage.setCurrent(userDOPage.getCurrent());
        userVOPage.setSize(userDOPage.getSize());
        userVOPage.setTotal(userDOPage.getTotal());

        List<UserVO> userVOs = userDOPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        userVOPage.setRecords(userVOs);

        return userVOPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean assignRoles(AssignRoleDTO dto) {
        log.info("分配角色: userId={}, roleIds={}", dto.getUserId(), dto.getRoleIds());

        // 1. 查询用户
        UserDO userDO = userMapper.selectById(dto.getUserId());
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 租户隔离验证
        if (!userDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限操作此用户");
        }

        // 3. 不能修改系统管理员的角色
        if (userDO.getTenantId() == 0 && "admin".equals(userDO.getUsername())) {
            throw new BusinessException(ErrorCode.USER_IS_ADMIN, "不能修改系统管理员的角色");
        }

        // 4. 删除旧角色
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getUserId, dto.getUserId());
        userRoleMapper.delete(wrapper);

        // 5. 校验所有角色存在且属于本租户
        for (Long roleId : dto.getRoleIds()) {
            RoleDO roleDO = roleMapper.selectById(roleId);
            if (roleDO == null) {
                throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "角色不存在: roleId=" + roleId);
            }
            if (!roleDO.getTenantId().equals(BaseContext.getTenantId())) {
                throw new BusinessException(ErrorCode.ROLE_NOT_IN_TENANT, "角色不属于本租户: roleId=" + roleId);
            }
        }

        // 6. 批量分配新角色
        List<UserRoleDO> userRoleDOs = new ArrayList<>();
        for (Long roleId : dto.getRoleIds()) {
            UserRoleDO userRoleDO = new UserRoleDO();
            userRoleDO.setUserId(dto.getUserId());
            userRoleDO.setRoleId(roleId);
            userRoleDOs.add(userRoleDO);
        }
        userRoleMapper.insertBatch(userRoleDOs);

        redisCacheManager.evictPermissionSnapshot(dto.getUserId());

        log.info("角色分配成功: userId={}", dto.getUserId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean resetPassword(ResetPasswordDTO dto) {
        log.info("重置密码: userId={}", dto.getUserId());

        // 1. 验证密码一致性
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        // 2. 查询用户
        UserDO userDO = userMapper.selectById(dto.getUserId());
        if (userDO == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 租户隔离验证
        if (!userDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限操作此用户");
        }

        // 4. 更新密码
        userDO.setPassword(PasswordUtil.hash(dto.getNewPassword()));
        int result = userMapper.updateById(userDO);

        log.info("密码重置成功: userId={}", dto.getUserId());
        return result > 0;
    }

    /**
     * DO -> VO 转换
     */
    private UserVO toVO(UserDO userDO) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userDO, userVO);
        return userVO;
    }
}
