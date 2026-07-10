package io.lumina.base.api.controller;

import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.api.vo.user.OnlineUserVO;
import io.lumina.base.service.OnlineUserService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户管理 Controller
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/base/users/online")
@RequiredArgsConstructor
@Tag(name = "在线用户", description = "在线用户查询、强制下线接口")
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    /**
     * 查询在线用户列表
     */
    @GetMapping
    @RequirePermission("system:online:list")
    public R<List<OnlineUserVO>> list(@RequestParam(required = false) String username) {
        return R.success(onlineUserService.listOnline(username));
    }

    /**
     * 强制下线
     */
    @DeleteMapping("/{userId}")
    @RequirePermission("system:online:force_logout")
    @Audit(module = "online_user", action = "FORCE_LOGOUT")
    public R<Void> forceLogout(@PathVariable Long userId) {
        onlineUserService.forceLogout(userId);
        return R.success();
    }
}
