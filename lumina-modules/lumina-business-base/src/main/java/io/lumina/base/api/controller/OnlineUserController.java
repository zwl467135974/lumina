package io.lumina.base.api.controller;

import io.lumina.base.api.vo.user.OnlineUserVO;
import io.lumina.base.service.OnlineUserService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户管理 Controller
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/base/users/online")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    /**
     * 查询在线用户列表
     */
    @GetMapping
    public R<List<OnlineUserVO>> list(@RequestParam(required = false) String username) {
        return R.success(onlineUserService.listOnline(username));
    }

    /**
     * 强制下线
     */
    @DeleteMapping("/{userId}")
    @Audit(module = "ONLINE_USER", action = "FORCE_LOGOUT")
    public R<Void> forceLogout(@PathVariable Long userId) {
        onlineUserService.forceLogout(userId);
        return R.success();
    }
}
