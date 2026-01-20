package io.lumina.base.feign;

import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.UpdateUserDTO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Base模块Feign客户端降级处理
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class BaseFeignClientFallback implements BaseFeignClient {

    @Override
    public R<Long> createUser(CreateUserDTO dto) {
        log.error("Feign调用失败: createUser, dto={}", dto);
        return R.fail("服务暂时不可用");
    }

    @Override
    public R<Boolean> updateUser(Long userId, UpdateUserDTO dto) {
        log.error("Feign调用失败: updateUser, userId={}, dto={}", userId, dto);
        return R.fail("服务暂时不可用");
    }

    @Override
    public R<Boolean> deleteUser(Long userId) {
        log.error("Feign调用失败: deleteUser, userId={}", userId);
        return R.fail("服务暂时不可用");
    }

    @Override
    public R<UserVO> getUserById(Long userId) {
        log.error("Feign调用失败: getUserById, userId={}", userId);
        return R.fail("服务暂时不可用");
    }

    @Override
    public R<UserVO> getUserByUsername(String username) {
        log.error("Feign调用失败: getUserByUsername, username={}", username);
        return R.fail("服务暂时不可用");
    }
}
