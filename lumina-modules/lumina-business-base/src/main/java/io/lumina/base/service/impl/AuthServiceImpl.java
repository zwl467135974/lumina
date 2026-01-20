package io.lumina.base.service.impl;

import io.lumina.base.api.dto.LoginDTO;
import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.domain.model.Role;
import io.lumina.base.domain.model.User;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.service.AuthService;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.JwtUtil;
import io.lumina.common.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 认证服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        log.info("用户登录请求: tenantId={}, username={}", loginDTO.getTenantId(), loginDTO.getUsername());

        // 1. 确定租户 ID（默认为 0，即系统租户）
        Long tenantId = loginDTO.getTenantId() != null ? loginDTO.getTenantId() : 0L;

        // 2. 查询用户（支持租户隔离）
        UserDO userDO = userMapper.selectByTenantIdAndUsername(tenantId, loginDTO.getUsername());

        if (userDO == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 验证密码（使用 BCrypt 加密验证）
        if (!PasswordUtil.verify(loginDTO.getPassword(), userDO.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 4. 检查用户状态
        if (userDO.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        // 5. 转换为领域模型
        User user = toDomain(userDO);

        // 6. 加载用户角色（过滤禁用的角色）
        List<RoleDO> roleDOs = roleMapper.selectRolesByUserId(user.getUserId());
        List<Role> roles = roleDOs.stream()
                .filter(roleDO -> roleDO.getStatus() != 0)  // 过滤禁用的角色
                .map(this::toRoleDomain)
                .collect(Collectors.toList());

        if (roles.isEmpty()) {
            throw new BusinessException("用户没有有效的角色");
        }

        user.setRoles(roles);

        // 7. 加载角色权限
        if (!roles.isEmpty()) {
            List<Long> roleIds = roles.stream()
                    .map(Role::getRoleId)
                    .collect(Collectors.toList());

            List<Long> permissionIds = roleMapper.selectPermissionIdsByRoleIds(roleIds);

            if (!permissionIds.isEmpty()) {
                var permissions = permissionMapper.selectPermissionsByIds(permissionIds);
                user.setPermissions(permissions.stream()
                        .map(this::toPermissionDomain)
                        .collect(Collectors.toList()));
            }
        }

        // 8. 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("username", user.getUsername());
        claims.put("tenantId", user.getTenantId());
        claims.put("roles", user.getRoleCodes().toArray(new String[0]));
        claims.put("permissions", user.getPermissionCodes().toArray(new String[0]));

        String token = jwtUtil.generateToken(user.getUsername(), claims);

        // 9. 构建响应
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType("Bearer");
        loginVO.setUserId(user.getUserId());
        loginVO.setUsername(user.getUsername());
        loginVO.setTenantId(user.getTenantId());
        loginVO.setRealName(user.getRealName());
        loginVO.setRoles(user.getRoleCodes().toArray(new String[0]));
        loginVO.setPermissions(user.getPermissionCodes().toArray(new String[0]));
        loginVO.setExpiration(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000); // 7天

        log.info("用户登录成功: userId={}, username={}, tenantId={}, roles={}",
                user.getUserId(), user.getUsername(), user.getTenantId(), user.getRoleCodes());

        return loginVO;
    }

    @Override
    public void logout() {
        // 在 JWT 模式下，服务端不需要做太多处理
        // 客户端删除本地存储的 Token 即可
        // 如果需要实现 Token 黑名单，可以使用 Redis
        log.info("用户登出");
    }

    /**
     * DO -> Domain 转换（用户）
     */
    private User toDomain(UserDO userDO) {
        User user = new User();
        BeanUtils.copyProperties(userDO, user);
        return user;
    }

    /**
     * DO -> Domain 转换（角色）
     */
    private Role toRoleDomain(RoleDO roleDO) {
        Role role = new Role();
        BeanUtils.copyProperties(roleDO, role);
        return role;
    }

    /**
     * DO -> Domain 转换（权限）
     */
    private io.lumina.base.domain.model.Permission toPermissionDomain(io.lumina.base.infrastructure.entity.PermissionDO permissionDO) {
        io.lumina.base.domain.model.Permission permission = new io.lumina.base.domain.model.Permission();
        BeanUtils.copyProperties(permissionDO, permission);
        return permission;
    }
}
