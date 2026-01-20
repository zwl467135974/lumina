package io.lumina.base.feign;

import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.UpdateUserDTO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Base模块Feign远程服务接口
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@FeignClient(
    name = "lumina-business-base",
    path = "/api/v1/base",
    fallbackFactory = BaseFeignClientFallback.class
)
public interface BaseFeignClient {

    /**
     * 创建用户
     */
    @PostMapping("/users")
    R<Long> createUser(@RequestBody CreateUserDTO dto);

    /**
     * 更新用户
     */
    @PutMapping("/users/{userId}")
    R<Boolean> updateUser(@PathVariable("userId") Long userId, @RequestBody UpdateUserDTO dto);

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{userId}")
    R<Boolean> deleteUser(@PathVariable("userId") Long userId);

    /**
     * 获取用户详情
     */
    @GetMapping("/users/{userId}")
    R<UserVO> getUserById(@PathVariable("userId") Long userId);

    /**
     * 根据用户名获取用户
     */
    @GetMapping("/users/username/{username}")
    R<UserVO> getUserByUsername(@PathVariable("username") String username);
}
